package com.vibetuned.to_reply.ui.training

import com.vibetuned.to_reply.data.model.Play
import com.vibetuned.to_reply.data.model.SpeakerStat

/**
 * One row of the training chat. The list is a flattening of the script (scene headers
 * interleaved with entries) so a single LazyColumn index space drives rendering, highlighting
 * and auto-scroll. Keys are stable across rebuilds — the script file is immutable, so "e-<n>"
 * always names the same entry even when a speaker switch rebuilds the items to flip [Bubble.isMine].
 */
sealed interface ScriptItem {
    val key: String

    data class SceneHeader(
        val title: String,
        val startMs: Long,
        override val key: String,
    ) : ScriptItem

    data class Bubble(
        /** Index into PlayScript.flatEntries — the position lookup works in that space. */
        val entryIndex: Int,
        val startMs: Long,
        val endMs: Long,
        val speaker: String,
        val text: String,
        val direction: String,
        val emotion: String,
        /** True when this line belongs to a rehearsed character: right "me" bubble, muted audio. */
        val isMine: Boolean,
        override val key: String,
    ) : ScriptItem {
        val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
    }

    data class StagingNote(
        val entryIndex: Int,
        val startMs: Long,
        val text: String,
        val isCue: Boolean,
        override val key: String,
    ) : ScriptItem
}

data class TrainingUiState(
    val play: Play? = null,
    val items: List<ScriptItem> = emptyList(),
    val speakers: List<SpeakerStat> = emptyList(),
    /** Characters being rehearsed, in pick order (order feeds the top-bar chip label). */
    val selectedSpeakers: List<String> = emptyList(),
    /** Index into [items] of the entry under the playhead, or -1 before the first entry. */
    val activeItemIndex: Int = -1,
    /** True while the playhead is inside a rehearsed character's line — "speak now". */
    val isMuted: Boolean = false,
    /** Memorization drill: my bubbles show only speaker name + progress gauge. */
    val hideMyText: Boolean = false,
    /** Listening drill: other characters' bubbles show only speaker name + progress gauge. */
    val hideOthersText: Boolean = false,
    val showSpeakerSheet: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)
