package com.vibetuned.to_reply

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vibetuned.to_reply.ui.common.appContainer
import com.vibetuned.to_reply.ui.navigation.ToReplyNavGraph
import com.vibetuned.to_reply.ui.navigation.TopLevelDestination
import com.vibetuned.to_reply.ui.navigation.TrainingRoute
import com.vibetuned.to_reply.ui.player.MiniPlayerBar
import com.vibetuned.to_reply.ui.theme.ToReplyTheme
import kotlinx.coroutines.awaitCancellation

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationPermission()
        enableEdgeToEdge()
        setContent {
            ToReplyTheme {
                ToReplyApp()
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun ToReplyApp() {
    KeepScreenOnWhilePlaying()

    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStack?.destination

    // On a fresh process, reopen whatever play the user was last rehearsing (paused at the saved
    // position — launching the app should not start audio on its own), layered on top of Home so
    // Back still returns there. The guard lives on the process-scoped container so this fires
    // once per launch — on a cold start and after Android kills the backgrounded process — but
    // never again across config changes.
    val container = appContainer()
    LaunchedEffect(Unit) {
        if (container.lastPlayRestoreHandled) return@LaunchedEffect
        container.lastPlayRestoreHandled = true
        val playId = container.positionRepository.lastPlayedPlayId() ?: return@LaunchedEffect
        // If a process-death restore already put the training screen back via the saved back
        // stack, it reopens the play on its own — don't push a duplicate entry.
        if (navController.currentDestination?.route == TrainingRoute.PATTERN) return@LaunchedEffect
        navController.navigate(TrainingRoute.forPlay(playId))
    }

    Scaffold(
        // Don't let the outer Scaffold add system-bar insets to the content padding — each
        // screen's own Scaffold/TopAppBar consumes the status bar. Without this, edge-to-edge
        // (enforced on Android 15+) makes the status-bar inset get applied twice, leaving a
        // tall blank band above the app bar.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Unlike ln-reader there's no NavigationBar (a single top-level destination) and the
            // mini-player is shown on the training screen too: it IS that screen's transport —
            // the chat gets the whole canvas and playback control stays one thumb-reach away.
            MiniPlayerBar(
                onExpand = { playId ->
                    // Tapping the bar while already on the training screen is a no-op (the bar
                    // there refers to the same play that screen is showing).
                    if (currentDestination?.route != TrainingRoute.PATTERN) {
                        navController.navigate(TrainingRoute.forPlay(playId)) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ToReplyNavGraph(
                navController = navController,
                startDestination = TopLevelDestination.Start.route
            )
        }
    }
}

/**
 * Keeps the screen awake while audio is playing — rehearsing means long stretches of reading
 * the script without touching the screen, and the display sleeping mid-scene loses the user's
 * place. Only while actually PLAYING: a paused app lets the screen time out normally, so
 * leaving the app open doesn't burn the battery. Sits at the app root so it covers every
 * screen; when the app is backgrounded the flag is moot (the window isn't visible) and
 * playback continues via the foreground service as before.
 */
@Composable
private fun KeepScreenOnWhilePlaying() {
    val container = appContainer()
    val controller by container.playerHolder.controller.collectAsStateWithLifecycle()
    val isPlaying = rememberIsPlaying(controller)
    val view = LocalView.current
    DisposableEffect(isPlaying) {
        view.keepScreenOn = isPlaying
        onDispose { view.keepScreenOn = false }
    }
}

/** Event-driven mirror of [Player.isPlaying] — no polling; the listener callback is enough. */
@Composable
private fun rememberIsPlaying(controller: MediaController?): Boolean {
    val isPlaying by produceState(initialValue = false, controller) {
        val c = controller
        if (c == null) {
            value = false
            return@produceState
        }
        value = c.isPlaying
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                value = playing
            }
        }
        c.addListener(listener)
        try {
            awaitCancellation()
        } finally {
            c.removeListener(listener)
        }
    }
    return isPlaying
}
