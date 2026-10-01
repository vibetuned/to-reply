package com.vibetuned.to_reply.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vibetuned.to_reply.data.model.PlayScene
import com.vibetuned.to_reply.ui.theme.LineDuration
import com.vibetuned.to_reply.ui.theme.MyCard
import com.vibetuned.to_reply.ui.theme.Ruby
import com.vibetuned.to_reply.ui.theme.TextPrimary
import com.vibetuned.to_reply.ui.theme.TextTimestamp

/**
 * The mini-player's chapter picker: every scene of the loaded play with its start time, the
 * current one marked. Scenes come from the script — the timing source of truth, and what the
 * previous/next-scene buttons step through (the m4b's own chapters mirror them and aren't read).
 * Picking one seeks to its start and closes the sheet; playback state is left as it was, like
 * the skip buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenePickerSheet(
    scenes: List<PlayScene>,
    currentIndex: Int,
    onPick: (PlayScene) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Scenes", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (scenes.size == 1) "1 scene" else "${scenes.size} scenes — jump to the start of one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onDismiss) { Text("Done") }
        }
        Spacer(Modifier.padding(top = 8.dp))
        // Opens with the current scene in view, a couple of rows of context above it.
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0)
        )
        LazyColumn(state = listState) {
            itemsIndexed(scenes, key = { index, _ -> index }) { index, scene ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) MyCard else Color.Transparent)
                        .clickable { onPick(scene) }
                        .semantics { selected = isCurrent }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The scene under the playhead gets a ruby marker.
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (isCurrent) Ruby else Color.Transparent, CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        scene.title.ifBlank { "Scene ${index + 1}" },
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        formatSceneStart(scene.startMs),
                        style = LineDuration,
                        color = if (isCurrent) TextPrimary else TextTimestamp
                    )
                }
            }
        }
        Spacer(Modifier.padding(bottom = 16.dp))
    }
}

/** Index of the scene the playhead is in: the last one starting at or before [positionMs]. */
fun currentSceneIndex(scenes: List<PlayScene>, positionMs: Long): Int =
    scenes.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)

private fun formatSceneStart(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
