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

    /**
     * Step one of the import picked a file. A zip imports right away — every play in it, each
     * with its own script; a recording waits for its script, which [HomeUiState.needsScript]
     * asks the screen to pick. Telling them apart can mean a provider round-trip, hence a
     * coroutine rather than the picker callback.
     */
    fun onFirstPick(uri: Uri) {
        viewModelScope.launch {
            if (playRepository.isZip(uri)) {
                importZip(uri)
            } else {
                pendingAudioUri = uri
                _state.update { it.copy(needsScript = true) }
            }
        }
    }

    fun scriptPickerShown() {
        _state.update { it.copy(needsScript = false) }
    }

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

    private suspend fun importZip(uri: Uri) {
        _state.update { it.copy(isImporting = true, importProgress = null) }
        val result = playRepository.importZip(uri) { progress ->
            _state.update { it.copy(importProgress = progress) }
        }
        _state.update {
            it.copy(
                isImporting = false,
                importProgress = null,
                // A zip can import some plays and fail others: that's a message too.
                error = result.getOrNull()?.problem ?: result.exceptionOrNull()?.message
            )
        }
    }

    /**
     * Writes [playId]'s share zip, then hands it to the screen ([HomeUiState.pendingShare]) to
     * open the share sheet. One share at a time.
     */
    fun share(playId: String) {
        if (_state.value.sharing != null) return
        viewModelScope.launch {
            _state.update { it.copy(sharing = SharingProgress(playId, 0f)) }
            runCatching {
                playRepository.prepareShare(playId) { fraction ->
                    _state.update { it.copy(sharing = SharingProgress(playId, fraction)) }
                }
            }
                .onSuccess { share -> _state.update { it.copy(sharing = null, pendingShare = share) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(sharing = null, error = e.message ?: "Couldn't prepare the play to share.")
                    }
                }
        }
    }

    fun shareLaunched() {
        _state.update { it.copy(pendingShare = null) }
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
