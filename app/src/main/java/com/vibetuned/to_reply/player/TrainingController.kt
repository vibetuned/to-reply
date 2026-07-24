package com.vibetuned.to_reply.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Process-scoped rehearsal mute engine: while a session is active, playback is muted whenever the
 * position is inside one of the trained character's line windows, so the user speaks the line
 * aloud while the recording keeps the play on schedule.
 *
 * Process-scoped (not ViewModel-scoped) for the same reason as ln-reader's sleep timer: the user
 * backgrounds the app and drives playback from the media notification, and their lines must still
 * go silent with no UI alive. The service runs in this same process, so "audio is possible"
 * always implies "this controller is alive".
 *
 * Muting is the player-level gain ([MediaController.setVolume], 0f/1f) — never AudioManager
 * stream volume — so device volume and other apps are untouched, and a fresh process after a
 * kill always starts back at gain 1f (nothing persistent to restore).
 *
 * This class is the app's only volume writer, so there is no writer conflict by construction.
 */
class TrainingController(private val playerHolder: PlayerHolder) {

    /**
     * @param speakers the characters being rehearsed — one or several at once.
     * @param muteRanges absolute-ms, end-exclusive-derived windows (see PlayScript.muteRangesFor),
     * sorted and pre-merged across all rehearsed characters. Precomputed by the caller so this
     * engine never parses a script.
     */
    data class Session(val playId: String, val speakers: Set<String>, val muteRanges: List<LongRange>)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** True while playback is inside the trained character's line — drives the "speak now" UI. */
    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private var job: Job? = null

    /**
     * Start (or replace) the rehearsal session. Idempotent for the same (playId, speakers): the
     * training screen re-arms on every entry, and restarting would cause a volume blip mid-line.
     */
    fun start(playId: String, speakers: Set<String>, muteRanges: List<LongRange>) {
        val current = _session.value
        if (current != null && current.playId == playId && current.speakers == speakers) return
        stop()
        val session = Session(playId, speakers, muteRanges)
        _session.value = session
        job = scope.launch {
            // collectLatest restarts the inner loop on every reconnect — a fresh MediaController
            // starts at volume 1f, and the old one's volume no longer matters once released.
            playerHolder.controller.collectLatest { controller ->
                if (controller != null) runSession(controller, session)
            }
        }
    }

    /** End the session and restore full volume. */
    fun stop() {
        job?.cancel()
        job = null
        _session.value = null
        _isMuted.value = false
        playerHolder.controller.value?.volume = 1f
    }

    private suspend fun runSession(controller: MediaController, session: Session) {
        val listener = object : Player.Listener {
            // Immediate re-evaluation on any seek — including tap-to-seek in the chat and seeks
            // from the notification — so seeking *into* one's own line mutes before the first
            // syllable plays instead of waiting out the current tick.
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                evaluate(controller, session)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                evaluate(controller, session)
            }
        }
        controller.addListener(listener)
        try {
            while (true) {
                delay(evaluate(controller, session))
            }
        } finally {
            // Cancel-safe restore, same shape as SleepTimerController: whatever interrupts the
            // loop (stop(), controller swap, scope death) may not leave the player muted.
            controller.removeListener(listener)
            _isMuted.value = false
            controller.volume = 1f
        }
    }

    /**
     * Applies the mute state for the current position and returns how long to sleep before the
     * next check. The sleep is deadline-aware: at most [TICK_MS], but shortened to land right on
     * the nearest range boundary (scaled by playback speed), so mute edges engage with far less
     * error than the base tick — the residue disappears into the script's ~250ms inter-line
     * silences.
     */
    private fun evaluate(controller: MediaController, session: Session): Long {
        // A different play got loaded while this session is alive (the normal path replaces the
        // session first, but e.g. a cold-start restore can race it): never mute foreign audio.
        if (controller.currentMediaItem?.mediaId != session.playId) {
            setMuted(controller, false)
            return TICK_MS
        }
        val pos = controller.currentPosition
        val ranges = session.muteRanges

        // Last range starting at or before pos, if any.
        var lo = 0
        var hi = ranges.size - 1
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (ranges[mid].first <= pos) {
                idx = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        val inRange = idx >= 0 && pos <= ranges[idx].last
        setMuted(controller, inRange)

        val nextBoundaryMs = when {
            inRange -> ranges[idx].last + 1
            idx + 1 < ranges.size -> ranges[idx + 1].first
            else -> return TICK_MS
        }
        val speed = controller.playbackParameters.speed.takeIf { it > 0f } ?: 1f
        return (((nextBoundaryMs - pos) / speed).toLong()).coerceIn(MIN_TICK_MS, TICK_MS)
    }

    private fun setMuted(controller: MediaController, muted: Boolean) {
        if (_isMuted.value == muted) return
        _isMuted.value = muted
        controller.volume = if (muted) 0f else 1f
    }

    companion object {
        private const val TICK_MS = 100L
        private const val MIN_TICK_MS = 20L
    }
}
