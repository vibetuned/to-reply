package com.vibetuned.to_reply.ui.player

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.vibetuned.to_reply.data.model.PlayScene
import com.vibetuned.to_reply.player.SceneSkip
import com.vibetuned.to_reply.ui.common.appContainer
import com.vibetuned.to_reply.ui.theme.Glass
import com.vibetuned.to_reply.ui.theme.Ruby
import com.vibetuned.to_reply.ui.theme.StageTrack
import kotlinx.coroutines.delay

/** Lightweight snapshot of the active playback session, read straight off the [MediaController]. */
data class NowPlaying(
    val playId: String,
    val title: String,
    val subtitle: String?,
    val artworkUri: Uri?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val positionMs: Long,
    val durationMs: Long
)

/**
 * Reduced playback bar driven by the process-scoped [MediaController]. Renders nothing when no
 * play is loaded, so callers can place it unconditionally. Tapping the row (outside the transport
 * buttons) opens the training screen for the current play via [onExpand]; the scenes button opens
 * the [ScenePickerSheet].
 */
@Composable
fun MiniPlayerBar(
    onExpand: (playId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val container = appContainer()
    val controller by container.playerHolder.controller.collectAsStateWithLifecycle()
    val nowPlaying = rememberNowPlaying(controller) ?: return
    val c = controller ?: return

    // The loaded play's scenes — titles for the scene picker, starts for the prev/next-scene
    // buttons — reloaded only when the loaded play changes. Comes from the repository's script
    // cache, so this is a one-time parse per play at most.
    val scenes by produceState(initialValue = emptyList<PlayScene>(), nowPlaying.playId) {
        value = container.playRepository.script(nowPlaying.playId).getOrNull()?.scenes.orEmpty()
    }
    val sceneStarts = remember(scenes) { scenes.map { it.startMs } }
    var showScenes by remember { mutableStateOf(false) }

    // Floating-controls glass (the design system's level 3); the ruby progress strip doubles as
    // its lit top edge.
    Surface(modifier = modifier.fillMaxWidth(), color = Glass) {
        Column {
            val fraction = if (nowPlaying.durationMs > 0)
                (nowPlaying.positionMs.toFloat() / nowPlaying.durationMs).coerceIn(0f, 1f) else 0f
            StageTrack(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                thickness = 2.dp,
                cap = StrokeCap.Butt
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpand(nowPlaying.playId) }
                    .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (nowPlaying.artworkUri != null) {
                    AsyncImage(
                        model = nowPlaying.artworkUri,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        nowPlaying.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!nowPlaying.subtitle.isNullOrBlank()) {
                        Text(
                            nowPlaying.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = { showScenes = true }) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Scenes")
                }
                IconButton(onClick = {
                    SceneSkip.previousTargetMs(sceneStarts, c.currentPosition)?.let { c.seekTo(it) }
                }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous scene")
                }
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (nowPlaying.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Ruby
                        )
                    } else {
                        FilledIconButton(
                            onClick = { if (c.isPlaying) c.pause() else c.play() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Ruby,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (nowPlaying.isPlaying) Icons.Filled.Pause
                                else Icons.Filled.PlayArrow,
                                contentDescription = if (nowPlaying.isPlaying) "Pause" else "Play"
                            )
                        }
                    }
                }
                IconButton(onClick = {
                    SceneSkip.nextTargetMs(sceneStarts, c.currentPosition)?.let { c.seekTo(it) }
                }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next scene")
                }
            }
        }
    }

    if (showScenes) {
        ScenePickerSheet(
            scenes = scenes,
            currentIndex = currentSceneIndex(scenes, nowPlaying.positionMs),
            onPick = { scene ->
                c.seekTo(scene.startMs)
                showScenes = false
            },
            onDismiss = { showScenes = false }
        )
    }
}

/**
 * Mirrors the controller's now-playing state into Compose: refreshes on every player event and
 * polls once a second so the position progress advances during playback. Listener is removed when
 * the bar leaves composition. Null while nothing is loaded.
 */
@Composable
private fun rememberNowPlaying(controller: MediaController?): NowPlaying? {
    val state by produceState<NowPlaying?>(initialValue = controller?.toNowPlaying(), controller) {
        val c = controller
        if (c == null) {
            value = null
            return@produceState
        }
        value = c.toNowPlaying()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                value = c.toNowPlaying()
            }
        }
        c.addListener(listener)
        try {
            while (true) {
                delay(1000)
                value = c.toNowPlaying()
            }
        } finally {
            c.removeListener(listener)
        }
    }
    return state
}

private fun MediaController.toNowPlaying(): NowPlaying? {
    val item = currentMediaItem ?: return null
    val id = item.mediaId.takeIf { it.isNotEmpty() } ?: return null
    val md = item.mediaMetadata
    return NowPlaying(
        playId = id,
        title = md.title?.toString().orEmpty(),
        subtitle = md.artist?.toString(),
        artworkUri = md.artworkUri,
        isPlaying = isPlaying,
        isBuffering = playbackState == Player.STATE_BUFFERING,
        positionMs = currentPosition.coerceAtLeast(0),
        durationMs = duration.takeIf { it > 0 } ?: 0L
    )
}
