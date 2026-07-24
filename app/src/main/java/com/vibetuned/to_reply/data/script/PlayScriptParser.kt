package com.vibetuned.to_reply.data.script

import com.vibetuned.to_reply.data.model.EntryType
import com.vibetuned.to_reply.data.model.PlayEntry
import com.vibetuned.to_reply.data.model.PlayScene
import com.vibetuned.to_reply.data.model.PlayScript
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToLong

/**
 * Parses a play.json script (see test_data/play.json for the reference shape) into a
 * [PlayScript]. Uses the platform org.json with opt* accessors throughout — same tolerant
 * style as ln-reader's SyncManifestParser — so a script missing optional fields still loads.
 * Times are Double seconds in the file and become Long milliseconds here, once.
 */
class PlayScriptParser {

    /** Parse [file]; any I/O or JSON failure surfaces as a failed [Result], never a throw. */
    fun parse(file: File): Result<PlayScript> = runCatching { parse(file.readText()) }

    fun parse(json: String): PlayScript {
        val root = JSONObject(json)
        val scenes = buildList {
            val scenesArray = root.optJSONArray("scenes") ?: return@buildList
            for (i in 0 until scenesArray.length()) {
                val sceneObj = scenesArray.optJSONObject(i) ?: continue
                add(parseScene(sceneObj))
            }
        }.sortedBy { it.startMs }
        return PlayScript(
            scenarioId = root.optString("scenario_id"),
            title = root.optString("title"),
            totalDurationMs = root.optDouble("total_duration_seconds", 0.0).toMs(),
            scenes = scenes
        )
    }

    /**
     * Strict checks applied at import time (regular opens trust the already-validated file).
     * Returns a user-facing error message, or null when the script is usable.
     */
    fun validate(script: PlayScript): String? = when {
        script.scenes.isEmpty() -> "Not a valid play script: it has no scenes."
        script.flatEntries.none { it.type == EntryType.DIALOGUE } ->
            "Not a valid play script: it has no dialogue entries."
        script.title.isBlank() -> "Not a valid play script: it has no title."
        script.totalDurationMs <= 0 -> "Not a valid play script: it has no duration."
        else -> null
    }

    private fun parseScene(obj: JSONObject): PlayScene {
        val entries = buildList {
            val entriesArray = obj.optJSONArray("entries") ?: return@buildList
            for (i in 0 until entriesArray.length()) {
                val entryObj = entriesArray.optJSONObject(i) ?: continue
                add(parseEntry(entryObj))
            }
        }.sortedBy { it.startMs }
        return PlayScene(
            sceneId = obj.optString("scene_id"),
            title = obj.optString("title"),
            startMs = obj.optDouble("start", 0.0).toMs(),
            endMs = obj.optDouble("end", 0.0).toMs(),
            entries = entries
        )
    }

    private fun parseEntry(obj: JSONObject): PlayEntry = PlayEntry(
        startMs = obj.optDouble("start", 0.0).toMs(),
        endMs = obj.optDouble("end", 0.0).toMs(),
        type = when (obj.optString("type")) {
            "dialogue" -> EntryType.DIALOGUE
            "cue" -> EntryType.CUE
            // Unknown types become staging notes rather than being dropped, so future script
            // formats degrade gracefully instead of shifting every entry index.
            else -> EntryType.STAGING
        },
        speaker = obj.optString("speaker"),
        text = obj.optString("text"),
        direction = obj.optString("direction"),
        emotion = obj.optString("emotion")
    )

    private fun Double.toMs(): Long = (this * 1000).roundToLong()
}
