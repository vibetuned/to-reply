package com.vibetuned.to_reply.data.model

/** An imported play as the UI sees it. Paths point into the app's private storage. */
data class Play(
    val id: String,
    val title: String,
    val audioPath: String,
    val scriptPath: String,
    val coverPath: String? = null,
    val durationMs: Long = 0,
    val importedAt: Long = 0,
    val fileSize: Long = 0,
    /** Raw speaker strings of the characters being rehearsed, in pick order. Empty = none yet. */
    val selectedSpeakers: List<String> = emptyList(),
) {
    val hasCover: Boolean get() = coverPath != null
}
