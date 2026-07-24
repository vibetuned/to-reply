package com.vibetuned.to_reply.ui.home

import com.vibetuned.to_reply.data.model.Play
import com.vibetuned.to_reply.data.repo.PlayRepository

/** A play plus its listening progress (0..1) for the home list's thin progress bar. */
data class PlayRow(
    val play: Play,
    val progress: Float
)

data class HomeUiState(
    val plays: List<PlayRow> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val importProgress: PlayRepository.ImportProgress? = null,
    val error: String? = null,
)
