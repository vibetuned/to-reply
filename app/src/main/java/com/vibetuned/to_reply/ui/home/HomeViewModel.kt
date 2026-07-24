package com.vibetuned.to_reply.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.vibetuned.to_reply.data.model.Play
import com.vibetuned.to_reply.data.repo.PlayRepository
import com.vibetuned.to_reply.data.repo.PositionRepository
import com.vibetuned.to_reply.player.PlayerHolder
import com.vibetuned.to_reply.player.TrainingController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val playRepository: PlayRepository,
    private val positionRepository: PositionRepository,
    private val playerHolder: PlayerHolder,
    private val trainingController: TrainingController,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /**
     * The .m4b picked in step one of the two-step import, held while the script picker is up.
     * Plain VM state (not UiState): it never renders, it only bridges the two launcher callbacks.
     */
    var pendingAudioUri: Uri? = null

    init {
        viewModelScope.launch {
            combine(
                playRepository.plays(),
                positionRepository.observeAllPositions()
            ) { plays, positions ->
                plays.map { play -> PlayRow(play, progressFraction(play, positions[play.id])) }
            }.collect { rows ->
                _state.update { it.copy(plays = rows, isLoading = false) }
            }
        }
    }

    fun import(audioUri: Uri, scriptUri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true, importProgress = null) }
            val result = playRepository.import(audioUri, scriptUri) { progress ->
                _state.update { it.copy(importProgress = progress) }
            }
            _state.update {
                it.copy(
                    isImporting = false,
                    importProgress = null,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun delete(playId: String) {
        viewModelScope.launch {
            // If the deleted play is the one currently loaded, tear the session down first so
            // neither the notification nor the mute engine keeps referencing dead files.
            if (trainingController.session.value?.playId == playId) {
                trainingController.stop()
            }
            playerHolder.controller.value?.let { controller ->
                if (controller.currentMediaItem?.mediaId == playId) {
                    controller.stop()
                    controller.clearMediaItems()
                }
            }
            playRepository.delete(playId)
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun progressFraction(play: Play, positionMs: Long?): Float {
        if (positionMs == null || play.durationMs <= 0) return 0f
        return (positionMs.toFloat() / play.durationMs).coerceIn(0f, 1f)
    }

    companion object {
        fun factory(
            playRepository: PlayRepository,
            positionRepository: PositionRepository,
            playerHolder: PlayerHolder,
            trainingController: TrainingController,
        ) = viewModelFactory {
            initializer {
                HomeViewModel(playRepository, positionRepository, playerHolder, trainingController)
            }
        }
    }
}
