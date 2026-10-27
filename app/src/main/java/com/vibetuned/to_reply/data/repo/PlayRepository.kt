package com.vibetuned.to_reply.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
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
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
        val totalBytes: Long = -1,
        /** 1-based position of the play being imported, when a zip brings several; else 0. */
        val playIndex: Int = 0,
        val playCount: Int = 0
    ) {
        enum class Phase { Extracting, Validating, Copying, Finalizing }
    }

    /**
     * What a zip import brought in. A zip can hold several plays, and some of them can fail while
     * the rest succeed, which [failures] lists by file name.
     */
    data class ImportOutcome(
        val plays: List<Play>,
        val failures: List<Pair<String, String>> = emptyList()
    ) {
        /** A message for the user when part of the import didn't make it; null when all did. */
        val problem: String?
            get() {
                if (failures.isEmpty()) return null
                val total = plays.size + failures.size
                val names = failures.joinToString("; ") { (name, why) -> "$name: $why" }
                return "Imported ${plays.size} of $total plays. Couldn't import $names"
            }
    }

    /** A file ready for the system share sheet. */
    data class PlayShare(val uri: Uri, val mimeType: String, val fileName: String, val title: String)

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
     *
     * [adoptAudio] is a recording already on this device that the import may take over — one just
     * extracted from a zip. It is moved into place rather than copied; [audioUri] is then only
     * that file's address.
     */
    suspend fun import(
        audioUri: Uri,
        scriptUri: Uri,
        adoptAudio: File? = null,
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

                val audioMeta = adoptAudio?.let { UriMeta(it.name, it.length()) } ?: queryUriMeta(audioUri)
                val totalBytes = audioMeta.size.takeIf { it > 0 } ?: -1L
                val audioFile = File(dir, AUDIO_FILE_NAME)
                if (adoptAudio != null) {
                    // Already on this device (just unzipped, on the same storage): a rename, so a
                    // play from a zip never exists twice on disk.
                    if (!adoptAudio.renameTo(audioFile)) {
                        adoptAudio.copyTo(audioFile, overwrite = true)
                        adoptAudio.delete()
                    }
                } else {
                    audioFile.outputStream().buffered().use { out ->
                        copyStream(audioUri, out) { copied ->
                            onProgress(ImportProgress(ImportProgress.Phase.Copying, copied, totalBytes))
                        }
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

    // ── zips ───────────────────────────────────────────────────────────────────

    /**
     * Whether [uri] is a zip rather than a recording — the import button's first picker takes
     * either. Goes by the file name, or failing that the provider's type.
     */
    suspend fun isZip(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        queryUriMeta(uri).name?.let { return@withContext it.endsWith(".zip", ignoreCase = true) }
        val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        type == "application/zip" || type == "application/x-zip-compressed"
    }

    /**
     * Imports every play in a zip ([PlayZipPlanner] pairs the recordings with their scripts).
     * Entries are unpacked into a staging folder beside the plays, so each recording is then moved,
     * not copied, into place; the staging folder is removed whatever happens. One play failing
     * doesn't stop the others; the outcome lists what failed, including any recording that came
     * without a script.
     */
    suspend fun importZip(
        uri: Uri,
        onProgress: (ImportProgress) -> Unit = {}
    ): Result<ImportOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val meta = queryUriMeta(uri)
            val staging = File(context.filesDir, "$STAGING_DIR/${UUID.randomUUID()}")
            try {
                val total = meta.size.takeIf { it > 0 } ?: -1L
                onProgress(ImportProgress(ImportProgress.Phase.Extracting, 0, total))
                val extracted = ZipExtractor.extract(
                    open = { context.contentResolver.openInputStream(uri) ?: error("Couldn't open the zip.") },
                    dir = staging
                ) { read -> onProgress(ImportProgress(ImportProgress.Phase.Extracting, read, total)) }

                // A .json only counts if it is a play script; the planner can't look inside.
                val usable = extracted.filter { path ->
                    !path.endsWith(".json", ignoreCase = true) || isPlayScript(File(staging, path))
                }
                val plan = PlayZipPlanner.plan(usable)
                require(plan.plays.isNotEmpty()) {
                    if (plan.unpaired.isEmpty()) "There is no play in this zip: it needs a recording (.m4b) and its script (play.json)."
                    else "There is no play.json next to ${plan.unpaired.joinToString { it.substringAfterLast('/') }}."
                }

                val plays = mutableListOf<Play>()
                val failures = plan.unpaired
                    .map { it.substringAfterLast('/') to "no play.json next to it" }
                    .toMutableList()
                plan.plays.forEachIndexed { index, zipPlay ->
                    val audio = File(staging, zipPlay.audio)
                    import(
                        audioUri = audio.toUri(),
                        scriptUri = File(staging, zipPlay.script).toUri(),
                        onProgress = { onProgress(it.copy(playIndex = index + 1, playCount = plan.plays.size)) },
                        adoptAudio = audio
                    )
                        .onSuccess { plays += it }
                        .onFailure { failures += audio.name to (it.message ?: it.javaClass.simpleName) }
                }
                if (plays.isEmpty()) {
                    val (name, why) = failures.first()
                    error("Couldn't import $name: $why")
                }
                ImportOutcome(plays, failures)
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    /** A play script is the JSON the producer pipeline writes: a `scenes` list of timed entries. */
    private fun isPlayScript(file: File): Boolean = runCatching {
        file.length() < MAX_SCRIPT_BYTES && file.readText().contains("\"scenes\"")
    }.getOrDefault(false)

    // ── sharing ────────────────────────────────────────────────────────────────

    /**
     * Prepares a play to send with Quick Share, Nearby, mail or anything else on the share sheet,
     * as the one file 2Reply on the other end imports whole: a zip holding the recording and its
     * script in a folder named after the play — the layout [importZip] reads. Written uncompressed
     * (audio doesn't shrink), so it costs one copy and no CPU; [onProgress] runs from 0 to 1.
     * Each share gets its own folder, so a second share can't cut short a transfer still reading
     * the first.
     */
    suspend fun prepareShare(playId: String, onProgress: (Float) -> Unit = {}): PlayShare =
        withContext(Dispatchers.IO) {
            val play = playDao.byId(playId) ?: error("This play is no longer in the library.")
            val audio = File(play.audioPath).takeIf { it.isFile } ?: error("This play's recording is missing.")
            val script = File(play.scriptPath).takeIf { it.isFile } ?: error("This play's script is missing.")
            pruneShares()
            val name = shareFileName(play.title)
            val zip = File(shareDir(), "${UUID.randomUUID()}/$name.zip").apply { parentFile?.mkdirs() }
            val entries = listOf("$name/$name.m4b" to audio, "$name/$SCRIPT_FILE_NAME" to script)
            val total = entries.sumOf { it.second.length() }.coerceAtLeast(1)
            var written = 0L
            try {
                ZipOutputStream(zip.outputStream().buffered(SHARE_BUFFER)).use { out ->
                    // Deflate at level 0: stored blocks, so no CPU spent on audio that won't
                    // compress, and one pass — the checksums are computed as the bytes go by.
                    out.setLevel(Deflater.NO_COMPRESSION)
                    for ((entry, file) in entries) {
                        out.putNextEntry(ZipEntry(entry))
                        file.inputStream().use { input ->
                            val buffer = ByteArray(SHARE_BUFFER)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                written += n
                                onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                        out.closeEntry()
                    }
                }
            } catch (e: Throwable) {
                zip.parentFile?.deleteRecursively()
                throw e
            }
            PlayShare(
                uri = FileProvider.getUriForFile(context, "${context.packageName}.share", zip),
                mimeType = "application/zip",
                fileName = zip.name,
                title = play.title
            )
        }

    private fun shareDir() = File(context.cacheDir, "share")

    /**
     * Shares more than a day old. Recent ones stay: Android restarts the app to serve a file
     * another app is still reading, and deleting it then would cut the transfer short.
     */
    private fun pruneShares() {
        val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
        shareDir().listFiles()?.filter { it.lastModified() < dayAgo }?.forEach { it.deleteRecursively() }
    }

    /**
     * Removes what an earlier run may have left: a zip import's staging folder, when the app was
     * stopped halfway through one, and old shares. Called once per process start, when no import
     * can be under way.
     */
    fun cleanUpTemporaryFiles() {
        File(context.filesDir, STAGING_DIR).deleteRecursively()
        pruneShares()
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

        /** Where a zip import unpacks, beside `plays/` so recordings move into place by rename. */
        private const val STAGING_DIR = "import-staging"

        /** No real script comes close; anything bigger isn't worth reading to find out. */
        private const val MAX_SCRIPT_BYTES = 16L * 1024 * 1024

        private const val SHARE_BUFFER = 256 * 1024
    }
}
