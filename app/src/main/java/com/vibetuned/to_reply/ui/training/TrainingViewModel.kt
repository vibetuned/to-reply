package com.vibetuned.to_reply.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.media3.session.MediaController
import com.vibetuned.to_reply.data.model.EntryType
import com.vibetuned.to_reply.data.model.Play
import com.vibetuned.to_reply.data.model.PlayScript
import com.vibetuned.to_reply.data.prefs.TrainingPreferences
import com.vibetuned.to_reply.data.repo.PlayRepository
import com.vibetuned.to_reply.data.repo.PositionRepository
import com.vibetuned.to_reply.player.PlayerHolder
import com.vibetuned.to_reply.player.TrainingController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrainingViewModel(
    private val playId: String,
    private val autoPlay: Boolean,
    private val playRepository: PlayRepository,
    private val positionRepository: PositionRepository,
    private val playerHolder: PlayerHolder,
    private val trainingController: TrainingController,
    private val trainingPreferences: TrainingPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(TrainingUiState())
    val state: StateFlow<TrainingUiState> = _state.asStateFlow()

    /**
     * Raw playhead position, refreshed every poll tick. Deliberately kept OUT of
     * [TrainingUiState]: only the active bubble's progress bar reads it, so exposing it as its
     * own flow confines the 3Hz recomposition to that single row instead of the whole screen
     * (UiState still only changes when the active entry changes).
     */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private var script: PlayScript? = null

    /** flat entry index -> index into UiState.items (headers shift them apart). */
    private var entryIndexToItemIndex: IntArray = IntArray(0)

    init {
        playerHolder.connect()

        viewModelScope.launch {
            val play = playRepository.get(playId)
            if (play == null) {
                _state.update { it.copy(isLoading = false, error = "Play not found.") }
                return@launch
            }
            val loaded = playRepository.script(playId).getOrElse { e ->
                _state.update {
                    it.copy(play = play, isLoading = false, error = "Couldn't read the script: ${e.message}")
                }
                return@launch
            }
            script = loaded
            val selected = play.selectedSpeakers
            buildItems(loaded, selected.toSet())
            _state.update {
                it.copy(
                    play = play,
                    speakers = loaded.speakerStats(),
                    selectedSpeakers = selected,
                    // First open of this play: ask who's rehearsing before anything mutes.
                    showSpeakerSheet = selected.isEmpty(),
                    isLoading = false
                )
            }
            if (selected.isNotEmpty()) {
                // Idempotent re-arm: same (play, speakers) keeps the live session untouched.
                trainingController.start(playId, selected.toSet(), loaded.muteRangesFor(selected.toSet()))
            }
            ensureLoaded(play)
        }

        viewModelScope.launch {
            trainingController.isMuted.collect { muted ->
                _state.update { it.copy(isMuted = muted) }
            }
        }

        // Prefs are the source of truth for the text-hiding drills: the toggles below write to
        // DataStore and these collectors feed the value back into state.
        viewModelScope.launch {
            trainingPreferences.hideMyText.collect { hide ->
                _state.update { it.copy(hideMyText = hide) }
            }
        }
        viewModelScope.launch {
            trainingPreferences.hideOthersText.collect { hide ->
                _state.update { it.copy(hideOthersText = hide) }
            }
        }

        // Position poll: cheap 300ms tick mapping position -> active entry, with a
        // change-detection guard so state (and recomposition) only moves at line-change
        // frequency. collectLatest restarts the loop if the controller reconnects.
        viewModelScope.launch {
            playerHolder.controller.collectLatest { controller ->
                if (controller == null) return@collectLatest
                while (true) {
                    updateActive(controller)
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Load this play into the player unless it's already the current item — re-entering the
     * screen while it plays must reattach, not restart (ln-reader PlayerViewModel behavior).
     */
    private suspend fun ensureLoaded(play: Play) {
        val controller = playerHolder.controller.filterNotNull().first()
        if (controller.currentMediaItem?.mediaId != play.id) {
            val startMs = positionRepository.get(play.id) ?: 0L
            playerHolder.loadPlay(play, startMs, playWhenReady = autoPlay)
        }
    }

    private fun updateActive(controller: MediaController) {
        val script = script ?: return
        if (controller.currentMediaItem?.mediaId != playId) return
        _positionMs.value = controller.currentPosition
        val entryIdx = script.entryIndexAt(controller.currentPosition)
        val itemIdx =
            if (entryIdx in entryIndexToItemIndex.indices) entryIndexToItemIndex[entryIdx] else -1
        if (itemIdx != _state.value.activeItemIndex) {
            _state.update { it.copy(activeItemIndex = itemIdx) }
        }
    }

    /**
     * Add or remove one character from the rehearsed set. The picker stays open so several can
     * be toggled in a row; deselecting the last one simply ends the mute session (pure
     * listening). New picks append, preserving pick order for the chip label.
     */
    fun toggleSpeaker(name: String) {
        val script = script ?: return
        viewModelScope.launch {
            val current = _state.value.selectedSpeakers
            val updated = if (name in current) current - name else current + name
            playRepository.setSelectedSpeakers(playId, updated)
            buildItems(script, updated.toSet())
            _state.update { it.copy(selectedSpeakers = updated) }
            if (updated.isEmpty()) {
                trainingController.stop()
            } else {
                trainingController.start(playId, updated.toSet(), script.muteRangesFor(updated.toSet()))
            }
        }
    }

    fun toggleHideMyText() {
        viewModelScope.launch { trainingPreferences.setHideMyText(!_state.value.hideMyText) }
    }

    fun toggleHideOthersText() {
        viewModelScope.launch { trainingPreferences.setHideOthersText(!_state.value.hideOthersText) }
    }

    fun openSpeakerSheet() = _state.update { it.copy(showSpeakerSheet = true) }

    fun dismissSpeakerSheet() = _state.update { it.copy(showSpeakerSheet = false) }

    /** Tap-to-seek on a bubble. The optimistic index keeps the highlight from lagging one poll. */
    fun seekTo(startMs: Long, itemIndex: Int) {
        playerHolder.controller.value?.seekTo(startMs)
        _positionMs.value = startMs
        _state.update { it.copy(activeItemIndex = itemIndex) }
    }

    /**
     * Flattens scenes + entries into the chat rows and records the entry->item index mapping.
     * Called again on speaker changes (flips isMine); keys stay identical so LazyColumn keeps
     * its scroll anchor through the rebuild.
     */
    private fun buildItems(script: PlayScript, selectedSpeakers: Set<String>) {
        val items = ArrayList<ScriptItem>(script.flatEntries.size + script.scenes.size)
        val mapping = IntArray(script.flatEntries.size)
        var entryIndex = 0
        for ((sceneIdx, scene) in script.scenes.withIndex()) {
            items += ScriptItem.SceneHeader(scene.title, scene.startMs, key = "s-$sceneIdx")
            for (entry in scene.entries) {
                val key = "e-$entryIndex"
                items += when (entry.type) {
                    EntryType.DIALOGUE -> ScriptItem.Bubble(
                        entryIndex = entryIndex,
                        startMs = entry.startMs,
                        endMs = entry.endMs,
                        speaker = entry.speaker,
                        text = entry.text,
                        direction = entry.direction,
                        emotion = entry.emotion,
                        // Empty-speaker lines can never be "mine": they're unattributed in the
                        // script, don't appear in the picker, and must never mute.
                        isMine = entry.speaker.isNotEmpty() && entry.speaker in selectedSpeakers,
                        key = key
                    )
                    else -> ScriptItem.StagingNote(
                        entryIndex = entryIndex,
                        startMs = entry.startMs,
                        text = entry.text,
                        isCue = entry.type == EntryType.CUE,
                        key = key
                    )
                }
                mapping[entryIndex] = items.size - 1
                entryIndex++
            }
        }
        entryIndexToItemIndex = mapping
        _state.update { it.copy(items = items) }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 300L

        fun factory(
            playId: String,
            autoPlay: Boolean,
            playRepository: PlayRepository,
            positionRepository: PositionRepository,
            playerHolder: PlayerHolder,
            trainingController: TrainingController,
            trainingPreferences: TrainingPreferences,
        ) = viewModelFactory {
            initializer {
                TrainingViewModel(
                    playId, autoPlay,
                    playRepository, positionRepository, playerHolder, trainingController,
                    trainingPreferences
                )
            }
        }
    }
}
