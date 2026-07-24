package com.vibetuned.to_reply.ui.training

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SpeakerNotes
import androidx.compose.material.icons.outlined.SpeakerNotesOff
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.VoiceOverOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibetuned.to_reply.ui.common.appContainer
import kotlinx.coroutines.flow.StateFlow

/**
 * The rehearsal screen: the play's script as a chat conversation, synced to the audio.
 *
 * - Other characters: left-aligned bubbles with speaker name + line (audio plays aloud).
 * - The trained character: right-aligned "me" bubbles (audio muted — the user speaks).
 * - Staging/cues: centered italic system notes; scene titles: section headers.
 * - The entry under the playhead is highlighted and kept in view; tapping any bubble seeks there.
 *
 * Transport lives in the global MiniPlayerBar (root scaffold) — this screen adds no second
 * player UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    playId: String,
    autoPlay: Boolean,
    onBack: () -> Unit,
) {
    val container = appContainer()
    val viewModel: TrainingViewModel = viewModel(
        // Keyed per play: opening a different play from Home must get a fresh VM even though
        // the same composable hosts both back-stack entries.
        key = "training:$playId",
        factory = TrainingViewModel.factory(
            playId, autoPlay,
            container.playRepository,
            container.positionRepository,
            container.playerHolder,
            container.trainingController,
            container.trainingPreferences
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    // Ephemeral by design (plain remember): re-entering the screen always resumes following.
    var autoFollow by remember { mutableStateOf(true) }

    // Stop following the audio when the USER grabs the list. DragInteraction.Start is emitted
    // only for touch drags — programmatic animateScrollToItem never emits drag interactions —
    // so this cleanly distinguishes user intent without heuristics. (A user touch mid-animation
    // additionally cancels the scroll effect below, which is exactly what we want.)
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) autoFollow = false
        }
    }

    // Follow the active line, parking it about a third from the top so upcoming lines are
    // visible below. Keyed restart cancels an in-flight animation when the index moves on.
    LaunchedEffect(state.activeItemIndex, autoFollow, state.items.size) {
        val index = state.activeItemIndex
        if (autoFollow && index >= 0 && index < state.items.size) {
            val parkOffset = -(listState.layoutInfo.viewportSize.height / 3)
            listState.animateScrollToItem(index, scrollOffset = parkOffset)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.play?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Text-hiding drills. Others' lines: train listening for your cue instead of
                    // reading ahead. Your lines: test memorization — only the name and the
                    // timing gauge remain.
                    IconButton(onClick = { viewModel.toggleHideOthersText() }) {
                        Icon(
                            if (state.hideOthersText) Icons.Outlined.SpeakerNotesOff
                            else Icons.Outlined.SpeakerNotes,
                            contentDescription = if (state.hideOthersText) "Show others' lines"
                            else "Hide others' lines",
                            tint = if (state.hideOthersText) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.toggleHideMyText() }) {
                        Icon(
                            if (state.hideMyText) Icons.Outlined.VoiceOverOff
                            else Icons.Outlined.RecordVoiceOver,
                            contentDescription = if (state.hideMyText) "Show my lines"
                            else "Hide my lines",
                            tint = if (state.hideMyText) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { viewModel.openSpeakerSheet() }) {
                        Icon(
                            Icons.Outlined.TheaterComedy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when (state.selectedSpeakers.size) {
                                0 -> "Choose roles"
                                1 -> state.selectedSpeakers.first()
                                else -> "${state.selectedSpeakers.first()} +${state.selectedSpeakers.size - 1}"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 140.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (!autoFollow) {
                SmallFloatingActionButton(onClick = { autoFollow = true }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Jump to current line")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    state.error.orEmpty(),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        itemsIndexed(state.items, key = { _, item -> item.key }) { index, item ->
                            val isActive = index == state.activeItemIndex
                            when (item) {
                                is ScriptItem.SceneHeader -> SceneHeaderRow(item)
                                is ScriptItem.Bubble -> BubbleRow(
                                    item = item,
                                    isActive = isActive,
                                    isPast = index < state.activeItemIndex,
                                    hideText = if (item.isMine) state.hideMyText
                                    else state.hideOthersText,
                                    positionMs = viewModel.positionMs,
                                    onClick = { viewModel.seekTo(item.startMs, index) }
                                )
                                is ScriptItem.StagingNote -> StagingNoteRow(
                                    item = item,
                                    isActive = isActive,
                                    onClick = { viewModel.seekTo(item.startMs, index) }
                                )
                            }
                        }
                    }
                    if (state.isMuted) {
                        SpeakNowBanner(modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
            }
        }
    }

    if (state.showSpeakerSheet) {
        SpeakerPickerSheet(
            speakers = state.speakers,
            selectedSpeakers = state.selectedSpeakers.toSet(),
            onToggle = { viewModel.toggleSpeaker(it) },
            onDismiss = { viewModel.dismissSpeakerSheet() }
        )
    }
}

@Composable
private fun SceneHeaderRow(item: ScriptItem.SceneHeader) {
    Text(
        item.title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp)
    )
}

@Composable
private fun BubbleRow(
    item: ScriptItem.Bubble,
    isActive: Boolean,
    isPast: Boolean,
    hideText: Boolean,
    positionMs: StateFlow<Long>,
    onClick: () -> Unit,
) {
    // Chat convention: my lines on the right with the "tail" corner bottom-right, everyone
    // else on the left with the tail bottom-left.
    val shape = if (item.isMine) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }
    val containerColor = when {
        isActive -> MaterialTheme.colorScheme.primaryContainer
        item.isMine -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (item.isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = containerColor,
            border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // "Me" bubbles normally skip the name (chat convention: alignment identifies
                // them), but with the text hidden the name is all that's left to say whose
                // line this is.
                if (!item.isMine || hideText) {
                    Text(
                        item.speaker.ifBlank { "—" },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary
                    )
                }
                if (!hideText) {
                    Text(item.text, style = MaterialTheme.typography.bodyMedium)
                }
                LineProgressBar(
                    item, isActive, isPast, positionMs,
                    // With no text to size the bubble, the gauge's width itself encodes the
                    // line's length (voice-message convention).
                    compactWidth = if (hideText) durationBarWidth(item.durationMs) else null
                )
                if (!hideText && item.direction.isNotBlank()) {
                    Text(
                        item.direction,
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Voice-message-style line gauge: a thin track that is full for lines already played, empty for
 * upcoming ones, and fills in real time on the active line, with the spoken duration alongside.
 *
 * Only the active bubble subscribes to [positionMs] (a conditional composable read), so the
 * 300ms position ticks recompose exactly one row. The tween matches the poll interval, turning
 * the stepped updates into a continuous sweep — and it doubles as a pleasant fill/drain
 * animation when a seek or line change snaps the fraction.
 */
@Composable
private fun LineProgressBar(
    item: ScriptItem.Bubble,
    isActive: Boolean,
    isPast: Boolean,
    positionMs: StateFlow<Long>,
    compactWidth: Dp? = null,
) {
    val fraction = if (isActive && item.durationMs > 0) {
        val position by positionMs.collectAsStateWithLifecycle()
        ((position - item.startMs).toFloat() / item.durationMs).coerceIn(0f, 1f)
    } else if (isPast) 1f else 0f
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "lineProgress"
    )
    Row(
        modifier = (if (compactWidth != null) Modifier.width(compactWidth) else Modifier)
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.weight(1f).height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            strokeCap = StrokeCap.Round
        )
        Spacer(Modifier.width(8.dp))
        Text(
            formatLineDuration(item.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatLineDuration(ms: Long): String {
    val totalSeconds = (ms + 500) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * Gauge width for a text-hidden bubble: linear in the line's duration, capped at 20s (longer
 * lines all read as "long"), so bubble width visually encodes how much speaking time the line
 * takes — the same convention chat apps use for voice messages.
 */
private fun durationBarWidth(durationMs: Long): Dp =
    72.dp + 200.dp * (durationMs / 20_000f).coerceIn(0f, 1f)

@Composable
private fun StagingNoteRow(
    item: ScriptItem.StagingNote,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            color = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else androidx.compose.ui.graphics.Color.Transparent
        ) {
            Text(
                if (item.isCue) "◈ ${item.text}" else item.text,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** Shown while the playhead is inside the trained character's (muted) line. */
@Composable
private fun SpeakNowBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Mic,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Your line — speak now",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
