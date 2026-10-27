package com.vibetuned.to_reply.ui.home

import com.vibetuned.to_reply.data.model.Play
import com.vibetuned.to_reply.data.repo.PlayRepository

/** A play plus its listening progress (0..1) for the home list's thin progress bar. */
data class PlayRow(
    val play: Play,
    val progress: Float
)

/** The play whose share zip is being written, and how far along it is (0..1). */
data class SharingProgress(val playId: String, val fraction: Float)

data class HomeUiState(
    val plays: List<PlayRow> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val importProgress: PlayRepository.ImportProgress? = null,
    val error: String? = null,
    /** Step one of the import picked a recording: the screen opens the script picker. Consumed once. */
    val needsScript: Boolean = false,
    /** A share being prepared; null when none is. One at a time. */
    val sharing: SharingProgress? = null,
    /** A prepared share waiting for the screen to open the share sheet. Consumed once. */
    val pendingShare: PlayRepository.PlayShare? = null,
)
