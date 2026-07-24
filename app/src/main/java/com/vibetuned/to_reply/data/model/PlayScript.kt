package com.vibetuned.to_reply.data.model

/**
 * Kind of timed entry in a play script. Unknown types coming from newer script formats are
 * mapped to [STAGING] by the parser so they render as harmless system notes instead of being
 * dropped (and silently shifting every index).
 */
enum class EntryType { DIALOGUE, STAGING, CUE }

/**
 * One timed entry of the script. All times are absolute milliseconds from the start of the
 * m4b — converted from the JSON's Double seconds once at parse time so everything downstream
 * (Media3 positions, mute ranges, seeks) works in the same integer unit.
 */
data class PlayEntry(
    val startMs: Long,
    val endMs: Long,
    val type: EntryType,
    /** Raw speaker string, may be empty (unattributed line) and may contain parentheticals. */
    val speaker: String,
    val text: String,
    /** Acting direction, e.g. "hésitant, un peu confus". Raw string, may be empty. */
    val direction: String,
    /** Emotion tag from the producer pipeline. Raw string, not an enum — tolerate anything. */
    val emotion: String,
)

data class PlayScene(
    val sceneId: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val entries: List<PlayEntry>,
)

/** A speaker and how many dialogue lines they have — feeds the character picker. */
data class SpeakerStat(val name: String, val lineCount: Int)

data class PlayScript(
    val scenarioId: String,
    val title: String,
    val totalDurationMs: Long,
    val scenes: List<PlayScene>,
) {
    /** All entries in scene order — the single index space the whole training UI works in. */
    val flatEntries: List<PlayEntry> = scenes.flatMap { it.entries }

    /**
     * Index into [flatEntries] of the entry active at [positionMs]: the last entry whose start
     * is <= the position (same semantics as ln-reader's SyncManifest.beatAt). Only starts are
     * compared, so during silences the previous entry stays "active", which reads naturally in
     * the chat UI. Returns -1 before the first entry. Entries are chronological by contract
     * (the parser sorts defensively), so a binary search is safe.
     */
    fun entryIndexAt(positionMs: Long): Int {
        var lo = 0
        var hi = flatEntries.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (flatEntries[mid].startMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }

    /** Speakers with at least one attributed dialogue line, busiest first. */
    fun speakerStats(): List<SpeakerStat> = flatEntries
        .asSequence()
        .filter { it.type == EntryType.DIALOGUE && it.speaker.isNotBlank() }
        .groupingBy { it.speaker }
        .eachCount()
        .map { (name, count) -> SpeakerStat(name, count) }
        .sortedWith(compareByDescending<SpeakerStat> { it.lineCount }.thenBy { it.name })

    /**
     * The time windows (absolute ms, end-exclusive) during which playback should be muted when
     * rehearsing [speakers] (one or several characters at once). Matching is an exact string
     * compare on the raw speaker field — the picker only ever offers strings that literally
     * appear in the script, so selected values always match something.
     * TODO: normalize parenthetical variants ("Caissière 1 (Roselyne)" vs "Roselyne") before
     * comparing — needs product input, as some variants are genuinely distinct characters.
     *
     * Zero/negative-width entries are skipped, and ranges closer than [MUTE_MERGE_GAP_MS] are
     * merged so back-to-back rehearsed lines don't produce a one-tick unmute blip between them
     * (including exchanges between two rehearsed characters). End-exclusive ranges mean a line
     * ending exactly where the next one starts never claims the boundary instant of its
     * neighbour.
     */
    fun muteRangesFor(speakers: Set<String>): List<LongRange> {
        if (speakers.isEmpty()) return emptyList()
        val raw = flatEntries
            .filter { it.type == EntryType.DIALOGUE && it.speaker in speakers && it.endMs > it.startMs }
            .map { it.startMs until it.endMs }
            .sortedBy { it.first }
        val merged = mutableListOf<LongRange>()
        for (range in raw) {
            val last = merged.lastOrNull()
            if (last != null && range.first - last.last <= MUTE_MERGE_GAP_MS) {
                merged[merged.size - 1] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }

    companion object {
        const val MUTE_MERGE_GAP_MS = 300L
    }
}
