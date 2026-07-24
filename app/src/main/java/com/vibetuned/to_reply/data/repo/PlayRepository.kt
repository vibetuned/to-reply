package com.vibetuned.to_reply.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.vibetuned.to_reply.data.db.PlayDao
import com.vibetuned.to_reply.data.db.PlayEntity
import com.vibetuned.to_reply.data.db.PositionDao
import com.vibetuned.to_reply.data.db.ToReplyDatabase
import com.vibetuned.to_reply.data.model.Play
import com.vibetuned.to_reply.data.model.PlayScript
import com.vibetuned.to_reply.data.script.PlayScriptParser
import com.vibetuned.to_reply.m4b.M4bParser
import com.vibetuned.to_reply.m4b.M4bSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.UUID
import kotlin.math.abs

class PlayRepository(
    private val context: Context,
    private val database: ToReplyDatabase,
    private val playDao: PlayDao,
    private val positionDao: PositionDao,
    private val m4bParser: M4bParser,
    private val scriptParser: PlayScriptParser
) {

    data class ImportProgress(
        val phase: Phase,
        val bytesRead: Long = 0,
        val totalBytes: Long = -1
    ) {
        enum class Phase { Validating, Copying, Finalizing }
    }

    /**
     * Parsed scripts by play id. Scripts are immutable once imported, so this only ever grows
     * (evicted on delete) and saves a re-parse on every training-screen open. Guarded with
     * synchronized because reads come from arbitrary IO-dispatcher threads.
     */
    private val scriptCache = mutableMapOf<String, PlayScript>()

    fun plays(): Flow<List<Play>> =
        playDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(playId: String): Play? = withContext(Dispatchers.IO) {
        playDao.byId(playId)?.toDomain()
    }

    /** The parsed script for [playId], from cache or disk. */
    suspend fun script(playId: String): Result<PlayScript> = withContext(Dispatchers.IO) {
        runCatching {
            synchronized(scriptCache) { scriptCache[playId] }?.let { return@runCatching it }
            val play = playDao.byId(playId) ?: error("Play not found.")
            val script = scriptParser.parse(File(play.scriptPath)).getOrThrow()
            synchronized(scriptCache) { scriptCache[playId] = script }
            script
        }
    }

    suspend fun setSelectedSpeakers(playId: String, speakers: List<String>) {
        playDao.updateSelectedSpeakers(playId, encodeSpeakers(speakers))
    }

    /**
     * Scene start times for [playId], for the previous/next-scene transport. Empty when the
     * script can't be read (deleted play, corrupt file) — callers treat that as "no skipping".
     */
    suspend fun sceneStartsMs(playId: String): List<Long> =
        script(playId).getOrNull()?.scenes?.map { it.startMs } ?: emptyList()

    /**
     * Import a play from two SAF documents: the .m4b recording and the play.json script. Both
     * are copied into private storage (`filesDir/plays/<id>/`), so no persistable URI permission
     * is taken and the user's originals are never touched again.
     *
     * The script is copied and validated *before* the (much larger) audio copy so a bad pick
     * fails in milliseconds, not after tens of megabytes. A cleanup list unwinds any partial
     * on-disk state when a later step fails — the play directory either ends up complete and
     * referenced by a DB row, or doesn't exist at all.
     */
    suspend fun import(
        audioUri: Uri,
        scriptUri: Uri,
        onProgress: (ImportProgress) -> Unit = {}
    ): Result<Play> = withContext(Dispatchers.IO) {
        runCatching {
            val playId = UUID.randomUUID().toString()
            val dir = playDir(playId)
            val cleanup = mutableListOf<() -> Unit>()
            try {
                check(dir.mkdirs()) { "Couldn't create $dir" }
                cleanup += { dir.deleteRecursively() }

                onProgress(ImportProgress(ImportProgress.Phase.Validating))
                val scriptFile = File(dir, SCRIPT_FILE_NAME)
                scriptFile.outputStream().buffered().use { out -> copyStream(scriptUri, out) {} }
                val script = scriptParser.parse(scriptFile).getOrElse {
                    error("Not a valid play script: ${it.message}")
                }
                scriptParser.validate(script)?.let { error(it) }

                val audioMeta = queryUriMeta(audioUri)
                val totalBytes = audioMeta.size.takeIf { it > 0 } ?: -1L
                val audioFile = File(dir, AUDIO_FILE_NAME)
                audioFile.outputStream().buffered().use { out ->
                    copyStream(audioUri, out) { copied ->
                        onProgress(ImportProgress(ImportProgress.Phase.Copying, copied, totalBytes))
                    }
                }

                onProgress(ImportProgress(ImportProgress.Phase.Finalizing))
                val parsed = M4bSource.open(context, audioFile.toUri()).use { m4bParser.parse(it) }

                // Wrong-pair guard, not a precision check: the pipeline that produces these
                // files writes exact timings, so a large mismatch means the user paired a
                // script with the wrong recording.
                if (abs(parsed.durationMs - script.totalDurationMs) > DURATION_TOLERANCE_MS) {
                    error("Script timing doesn't match this audio file.")
                }

                val coverPath = parsed.images.firstOrNull()?.let { img ->
                    val ext = when (img.mimeType) {
                        "image/png" -> "png"
                        else -> "jpg"
                    }
                    File(dir, "cover.$ext").apply { writeBytes(img.bytes) }.absolutePath
                }

                val title = script.title.ifBlank {
                    parsed.title
                        ?: audioMeta.name?.removeSuffix(".m4b")
                        ?: "Untitled"
                }

                val entity = PlayEntity(
                    id = playId,
                    title = title,
                    audioPath = audioFile.absolutePath,
                    scriptPath = scriptFile.absolutePath,
                    coverPath = coverPath,
                    durationMs = parsed.durationMs,
                    importedAt = System.currentTimeMillis(),
                    fileSize = audioFile.length(),
                    selectedSpeakers = null
                )
                playDao.upsert(entity)
                cleanup.clear()
                synchronized(scriptCache) { scriptCache[playId] = script }
                entity.toDomain()
            } finally {
                cleanup.forEach { runCatching(it) }
            }
        }
    }

    /** Remove the play, its saved position, and its whole on-disk directory. */
    suspend fun delete(playId: String) = withContext(Dispatchers.IO) {
        playDir(playId).deleteRecursively()
        synchronized(scriptCache) { scriptCache.remove(playId) }
        database.withTransaction {
            positionDao.delete(playId)
            playDao.delete(playId)
        }
    }

    private fun playDir(playId: String): File = File(context.filesDir, "plays/$playId")

    private fun copyStream(sourceUri: Uri, output: OutputStream, onBytes: (Long) -> Unit) {
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: error("Couldn't open $sourceUri for reading.")
        input.use {
            val buf = ByteArray(64 * 1024)
            var copied = 0L
            while (true) {
                val n = it.read(buf)
                if (n <= 0) break
                output.write(buf, 0, n)
                copied += n
                onBytes(copied)
            }
            output.flush()
        }
    }

    private data class UriMeta(val name: String?, val size: Long)

    /**
     * Fetches display name and size in a single ContentResolver query. For cloud document
     * providers each metadata lookup is a network round-trip, so reading both columns at once
     * halves the latency before the copy can begin. Returns nulls/0 on failure.
     */
    private fun queryUriMeta(uri: Uri): UriMeta = runCatching {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use UriMeta(null, 0L)
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            UriMeta(
                name = nameIdx.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString),
                size = sizeIdx.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong) ?: 0L
            )
        }
    }.getOrNull() ?: UriMeta(null, 0L)

    companion object {
        const val AUDIO_FILE_NAME = "audio.m4b"
        const val SCRIPT_FILE_NAME = "play.json"

        /** Max |m4b duration − script duration| before we assume a mismatched pair. */
        private const val DURATION_TOLERANCE_MS = 30_000L
    }
}
