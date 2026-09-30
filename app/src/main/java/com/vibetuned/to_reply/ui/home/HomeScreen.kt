package com.vibetuned.to_reply.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vibetuned.to_reply.data.model.Play
import com.vibetuned.to_reply.data.repo.PlayRepository
import com.vibetuned.to_reply.ui.common.appContainer
import com.vibetuned.to_reply.ui.theme.CardContainer
import com.vibetuned.to_reply.ui.theme.CharacterBadge
import com.vibetuned.to_reply.ui.theme.Hairline
import com.vibetuned.to_reply.ui.theme.Ruby
import com.vibetuned.to_reply.ui.theme.RowDivider
import com.vibetuned.to_reply.ui.theme.StageTrack
import com.vibetuned.to_reply.ui.theme.SurfaceElevated
import java.io.File

/**
 * MIME filters for the two-step import. `application/octet-stream` appears in both because many
 * providers report it for anything they can't classify — .m4b files in particular.
 */
private val AUDIO_MIME_TYPES = arrayOf("audio/mp4", "audio/x-m4b", "application/octet-stream")
private val SCRIPT_MIME_TYPES = arrayOf("application/json", "application/octet-stream")

/** Home: the list of imported plays. The FAB runs the two-step import (.m4b, then play.json). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenPlay: (playId: String) -> Unit,
) {
    val container = appContainer()
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            container.playRepository,
            container.positionRepository,
            container.playerHolder,
            container.trainingController
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var playPendingDelete by remember { mutableStateOf<Play?>(null) }

    // Two-step SAF import: the audio picker's callback stashes the URI on the VM (surviving the
    // recomposition churn of launching another activity) and immediately opens the script picker.
    // Cancelling either step aborts with nothing written.
    val scriptPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { scriptUri ->
        val audioUri = viewModel.pendingAudioUri
        viewModel.pendingAudioUri = null
        if (scriptUri != null && audioUri != null) viewModel.import(audioUri, scriptUri)
    }
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { audioUri ->
        if (audioUri != null) {
            viewModel.pendingAudioUri = audioUri
            scriptPicker.launch(SCRIPT_MIME_TYPES)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("2Reply") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!state.isImporting) {
                FloatingActionButton(
                    onClick = { audioPicker.launch(AUDIO_MIME_TYPES) },
                    shape = CircleShape,
                    containerColor = Ruby,
                    contentColor = Color.White,
                    modifier = Modifier.dropShadow(
                        CircleShape,
                        Shadow(radius = 16.dp, color = Ruby, offset = DpOffset(0.dp, 6.dp), alpha = 0.35f)
                    )
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Import a play")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isImporting) {
                ImportProgressBanner(state.importProgress)
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ruby)
                }
                state.plays.isEmpty() -> EmptyState()
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.plays, key = { it.play.id }) { row ->
                        PlayRowItem(
                            row = row,
                            onClick = { onOpenPlay(row.play.id) },
                            onDelete = { playPendingDelete = row.play }
                        )
                    }
                }
            }
        }
    }

    playPendingDelete?.let { play ->
        AlertDialog(
            onDismissRequest = { playPendingDelete = null },
            title = { Text("Remove play?") },
            text = { Text("“${play.title}” and its imported files will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(play.id)
                    playPendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { playPendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlayRowItem(
    row: PlayRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    // Script-index row: playbill thumb, title, duration + rehearsed roles, and a completion
    // track along the base; rows are separated by a subtle bottom rule.
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.play.hasCover) {
                    AsyncImage(
                        model = File(row.play.coverPath!!),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = MaterialTheme.shapes.small,
                        color = CardContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.TheaterComedy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.play.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatDuration(row.play.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (row.play.selectedSpeakers.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            CharacterBadge(
                                row.play.selectedSpeakers.joinToString(", "),
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Remove play",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (row.progress > 0f) {
                Spacer(Modifier.height(10.dp))
                StageTrack(progress = { row.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
        HorizontalDivider(color = RowDivider)
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.TheaterComedy,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = Ruby
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No plays yet",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap + and pick the recording (.m4b),\nthen its script (play.json).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Import feedback strip: indeterminate while validating/finalizing, determinate with a byte
 * count while the (large) audio file is copied.
 */
@Composable
private fun ImportProgressBanner(progress: PlayRepository.ImportProgress?) {
    Surface(color = SurfaceElevated) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            val label = when (progress?.phase) {
                PlayRepository.ImportProgress.Phase.Validating -> "Checking script…"
                PlayRepository.ImportProgress.Phase.Copying ->
                    if (progress.totalBytes > 0)
                        "Copying audio: ${formatBytes(progress.bytesRead)} / ${formatBytes(progress.totalBytes)}"
                    else "Copying audio: ${formatBytes(progress.bytesRead)}"
                PlayRepository.ImportProgress.Phase.Finalizing -> "Finalizing…"
                null -> "Importing…"
            }
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            val fraction = progress?.takeIf {
                it.phase == PlayRepository.ImportProgress.Phase.Copying && it.totalBytes > 0
            }?.let { it.bytesRead.toFloat() / it.totalBytes }
            if (fraction != null) {
                StageTrack(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Ruby,
                    trackColor = Hairline,
                    gapSize = 0.dp
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h} h ${m.toString().padStart(2, '0')} min" else "$m min"
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(mb)
}
