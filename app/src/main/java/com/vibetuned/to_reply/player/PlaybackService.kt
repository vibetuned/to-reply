package com.vibetuned.to_reply.player

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vibetuned.to_reply.ToReplyApplication
import com.vibetuned.to_reply.data.repo.PositionRepository
import com.vibetuned.to_reply.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Notification buttons are backed by custom session commands rather than player commands:
// DefaultMediaNotificationProvider only renders custom-layout buttons whose sessionCommand is set,
// and silently drops player-command buttons. So the seeks are dispatched by hand in onCustomCommand.
private const val ACTION_PREV_SCENE = "com.vibetuned.to_reply.action.PREV_SCENE"
private const val ACTION_NEXT_SCENE = "com.vibetuned.to_reply.action.NEXT_SCENE"

/**
 * Foreground media session host. The player itself lives in here so playback survives the
 * activity going away; UI talks to it via a MediaController.
 *
 * The media notification carries a custom layout — previous scene / next scene, targets from
 * the play's script via [SceneSkip] — wired through [ToReplySessionCallback].
 *
 * Position is throttled-saved every [POSITION_SAVE_INTERVAL_MS] while playing, and on every
 * pause / stop, via [PositionRepository] keyed by the media item's mediaId (= play id).
 *
 * Note: the rehearsal mute (volume = 0 during the user's lines) is NOT handled here — it lives
 * in the process-scoped [TrainingController], which drives the player through the same
 * MediaController surface as the UI.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionSaveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as ToReplyApplication).container

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            // Make the seek-back/seek-forward player commands match the notification buttons.
            .setSeekBackIncrementMs(SKIP_BACK_MS)
            .setSeekForwardIncrementMs(SKIP_FORWARD_MS)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startPositionSaver(container.positionRepository)
                else {
                    stopPositionSaver()
                    saveCurrentPosition(container.positionRepository)
                }
            }
        })

        session = MediaSession.Builder(this, player)
            .setCallback(ToReplySessionCallback(container, serviceScope, buildCustomLayout()))
            .build()
    }

    /**
     * Notification buttons. Both are custom session-command buttons — the only kind
     * [androidx.media3.session.DefaultMediaNotificationProvider] renders from a custom layout.
     * The predefined `ICON_PREVIOUS`/`ICON_NEXT` constants auto-resolve to Media3's bundled
     * drawables. They read as track-skip icons but perform SCENE skips here — this is a play
     * rehearsal app, so the meaningful jump unit is a scene, not an arbitrary time increment.
     * Commands are handled in [ToReplySessionCallback.onCustomCommand].
     */
    private fun buildCustomLayout(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setSessionCommand(SessionCommand(ACTION_PREV_SCENE, Bundle.EMPTY))
            .setDisplayName("Previous scene")
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setSessionCommand(SessionCommand(ACTION_NEXT_SCENE, Bundle.EMPTY))
            .setDisplayName("Next scene")
            .build()
    )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // If the user swipes the app away while paused, tear down. If playing, keep going.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopPositionSaver()
        session?.run {
            player.release()
            release()
            session = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPositionSaver(positionRepository: PositionRepository) {
        stopPositionSaver()
        positionSaveJob = serviceScope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                saveCurrentPosition(positionRepository)
            }
        }
    }

    private fun stopPositionSaver() {
        positionSaveJob?.cancel()
        positionSaveJob = null
    }

    private fun saveCurrentPosition(positionRepository: PositionRepository) {
        val playId = player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        val pos = player.currentPosition
        if (pos < 0) return
        serviceScope.launch { positionRepository.save(playId, pos) }
    }

    companion object {
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
        private const val SKIP_BACK_MS = 10_000L
        private const val SKIP_FORWARD_MS = 30_000L
    }
}

/**
 * Wires up the notification's custom layout: grants the buttons' custom session commands on
 * connect (otherwise they render disabled), pushes the layout to each controller in
 * [onPostConnect], and performs the scene seeks in [onCustomCommand].
 */
@OptIn(UnstableApi::class)
private class ToReplySessionCallback(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val customLayout: List<CommandButton>
) : MediaSession.Callback {

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        // Grant the custom commands behind our notification buttons; without these a controller
        // (including the notification controller) renders the buttons disabled.
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            .buildUpon()
            .apply { customLayout.forEach { button -> button.sessionCommand?.let { add(it) } } }
            .build()
        // Drop the previous/next commands so DefaultMediaNotificationProvider doesn't render its
        // own skip-to-item buttons — for a single-file recording they're useless and they'd
        // displace our skip buttons. Play/pause and the seek bar stay available.
        val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            .buildUpon()
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
            .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .remove(Player.COMMAND_SEEK_TO_NEXT)
            .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    /** Push the custom layout once a controller (including the notification controller) connects. */
    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        session.setCustomLayout(controller, customLayout)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            ACTION_PREV_SCENE -> seekScene(session) { starts, pos -> SceneSkip.previousTargetMs(starts, pos) }
            ACTION_NEXT_SCENE -> seekScene(session) { starts, pos -> SceneSkip.nextTargetMs(starts, pos) }
            else -> return Futures.immediateFuture(
                SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
            )
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /**
     * Scene starts come from the (cached) script, so a coroutine hop is needed; the scope runs
     * on Main, matching the player's thread. First press after a cold service start may parse
     * the JSON once (~tens of ms) — imperceptible on a button tap.
     */
    private fun seekScene(session: MediaSession, target: (List<Long>, Long) -> Long?) {
        val playId = session.player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        scope.launch {
            val starts = container.playRepository.sceneStartsMs(playId)
            target(starts, session.player.currentPosition)?.let { session.player.seekTo(it) }
        }
    }
}
