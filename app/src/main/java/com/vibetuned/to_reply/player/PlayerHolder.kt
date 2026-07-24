package com.vibetuned.to_reply.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vibetuned.to_reply.data.model.Play
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Process-scoped holder for a [MediaController] bound to [PlaybackService]. UI code observes
 * [controller] as a StateFlow — null while connecting / disconnected, non-null once the session
 * is bound. Use [connect] from the first screen that needs playback.
 */
class PlayerHolder(private val context: Context) {

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener(
            {
                _controller.value = f.get()
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _controller.value = null
    }

    /**
     * Sets [play] as the current item, seeks to [startPositionMs], and starts buffering. When
     * [playWhenReady] is true, playback begins as soon as the item is buffered (used to resume on
     * app restart and when the user taps a play to rehearse); otherwise the item loads paused.
     */
    fun loadPlay(play: Play, startPositionMs: Long, playWhenReady: Boolean = false) {
        val controller = _controller.value ?: return
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(play.title)
        play.coverPath?.let { metadataBuilder.setArtworkUri(File(it).toUri()) }
        val mediaItem = MediaItem.Builder()
            .setMediaId(play.id)
            .setUri(File(play.audioPath).toUri())
            .setMediaMetadata(metadataBuilder.build())
            .build()
        controller.setMediaItem(mediaItem, startPositionMs)
        controller.prepare()
        if (playWhenReady) controller.play()
    }
}
