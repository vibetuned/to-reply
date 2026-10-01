package com.vibetuned.to_reply.ui.training

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SpeakerNotes
import androidx.compose.material.icons.outlined.SpeakerNotesOff
import androidx.compose.material.icons.outlined.VoiceOverOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibetuned.to_reply.ui.common.appContainer
import com.vibetuned.to_reply.ui.theme.CardContainer
import com.vibetuned.to_reply.ui.theme.CharacterBadge
import com.vibetuned.to_reply.ui.theme.DimRose
import com.vibetuned.to_reply.ui.theme.Glass
import com.vibetuned.to_reply.ui.theme.Hairline
import com.vibetuned.to_reply.ui.theme.LabelCharacter
import com.vibetuned.to_reply.ui.theme.LineDuration
import com.vibetuned.to_reply.ui.theme.MyCard
import com.vibetuned.to_reply.ui.theme.MyCardActive
import com.vibetuned.to_reply.ui.theme.OpposingCard
import com.vibetuned.to_reply.ui.theme.OpposingCardBorder
import com.vibetuned.to_reply.ui.theme.OpposingName
import com.vibetuned.to_reply.ui.theme.ProsceniumGold
import com.vibetuned.to_reply.ui.theme.Ruby
import com.vibetuned.to_reply.ui.theme.StageDirection
import com.vibetuned.to_reply.ui.theme.TextPrimary
import com.vibetuned.to_reply.ui.theme.TextTimestamp
import com.vibetuned.to_reply.ui.theme.Velvet
import com.vibetuned.to_reply.ui.theme.VoiceWaveform
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
                        style = MaterialTheme.typography.titleMedium,
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
                    CharacterBadge(
                        text = when (state.selectedSpeakers.size) {
                            0 -> "Choose roles"
                            1 -> state.selectedSpeakers.first()
                            else -> "${state.selectedSpeakers.first()} +${state.selectedSpeakers.size - 1}"
                        },
                        onClick = { viewModel.openSpeakerSheet() },
                        modifier = Modifier.padding(end = 8.dp).widthIn(max = 164.dp)
                    )
                }
            )
        },
        floatingActionButton = {
            if (!autoFollow) {
                SmallFloatingActionButton(
                    onClick = { autoFollow = true },
                    shape = CircleShape,
                    containerColor = Glass,
                    contentColor = TextPrimary
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "Jump to current line")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Ruby)
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
                                    topGap = bubbleGap(state.items, index),
                                    positionMs = viewModel.positionMs,
                                    onClick = { viewModel.seekTo(item.startMs, index) },
                                    onPlay = { viewModel.playFrom(item.startMs, index) }
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
    // Proscenium-style divider: the scene title set between two hairline rules.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(Modifier.weight(1f), color = Hairline)
        Text(
            item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .widthIn(max = 260.dp)
        )
        HorizontalDivider(Modifier.weight(1f), color = Hairline)
    }
}

/**
 * Conversation-stream rhythm: consecutive lines by the same character sit 12dp apart, a
 * speaker change gets 16dp. After a scene header or staging note (which bring their own
 * padding) a bubble only needs a small lead.
 */
private fun bubbleGap(items: List<ScriptItem>, index: Int): Dp {
    val current = items[index] as? ScriptItem.Bubble ?: return 0.dp
    val previous = items.getOrNull(index - 1) as? ScriptItem.Bubble ?: return 8.dp
    return if (previous.speaker == current.speaker) 12.dp else 16.dp
}

@Composable
private fun BubbleRow(
    item: ScriptItem.Bubble,
    isActive: Boolean,
    isPast: Boolean,
    hideText: Boolean,
    topGap: Dp,
    positionMs: StateFlow<Long>,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    // Chat convention: my lines on the right with the "tail" corner bottom-right, everyone
    // else on the left with the tail bottom-left.
    val shape = if (item.isMine) {
        RoundedCornerShape(12.dp, 12.dp, 4.dp, 12.dp)
    } else {
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp)
    }
    // Dialogue cards: others' lines are charcoal, mine are velvet-tinted. The line under the
    // playhead gets a ruby border — and when it's mine, the ambient ruby "stage highlight".
    val containerColor = when {
        item.isMine && isActive -> MyCardActive
        item.isMine -> MyCard
        isActive -> CardContainer
        else -> OpposingCard
    }
    val border = when {
        isActive -> BorderStroke(1.5.dp, Ruby)
        item.isMine -> BorderStroke(1.dp, Velvet)
        else -> BorderStroke(1.dp, OpposingCardBorder)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Asymmetric gutters: others' lines keep 48dp clear on the right, mine 40dp on the
            // left, so the two sides never line up.
            .padding(
                start = if (item.isMine) 40.dp else 16.dp,
                end = if (item.isMine) 16.dp else 48.dp,
                top = topGap
            ),
        horizontalArrangement = if (item.isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = containerColor,
            contentColor = TextPrimary,
            border = border,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .then(
                    if (isActive && item.isMine) {
                        Modifier.dropShadow(shape, Shadow(radius = 12.dp, color = Ruby, alpha = 0.2f))
                    } else Modifier
                )
        ) {
            // Voice-message card: the line's audio row on top, the transcript under a rule.
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                // "Me" bubbles normally skip the name (chat convention: alignment identifies
                // them), but with the text hidden the name is all that's left to say whose
                // line this is.
                if (!item.isMine || hideText) {
                    Text(
                        item.speaker.ifBlank { "—" }.uppercase(),
                        style = LabelCharacter,
                        color = if (item.isMine) MaterialTheme.colorScheme.primaryContainer
                        else OpposingName
                    )
                    Spacer(Modifier.height(8.dp))
                }
                VoiceLine(
                    item, isActive, isPast, positionMs, onPlay,
                    // With no text to size the bubble, the waveform's width itself encodes the
                    // line's length (voice-message convention).
                    waveformWidth = if (hideText) waveformWidth(item.durationMs) else null
                )
                if (!hideText) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
                        color = if (item.isMine) Velvet.copy(alpha = 0.5f) else Hairline
                    )
                    Text(item.text, style = MaterialTheme.typography.bodyMedium)
                    if (item.direction.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(item.direction, style = StageDirection, color = DimRose)
                    }
                }
            }
        }
    }
}

/**
 * Voice-message row: a round play button, a waveform that is ruby for lines already played,
 * grey for upcoming ones and fills in real time on the active line, and the spoken duration.
 *
 * Only the active bubble subscribes to [positionMs] (a conditional composable read), so the
 * 300ms position ticks recompose exactly one row. The tween matches the poll interval, turning
 * the stepped updates into a continuous sweep — and it doubles as a pleasant fill/drain
 * animation when a seek or line change snaps the fraction.
 */
@Composable
private fun VoiceLine(
    item: ScriptItem.Bubble,
    isActive: Boolean,
    isPast: Boolean,
    positionMs: StateFlow<Long>,
    onPlay: () -> Unit,
    waveformWidth: Dp? = null,
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
        modifier = if (waveformWidth == null) Modifier.fillMaxWidth() else Modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VoiceButton(isMine = item.isMine, speaking = item.isMine && isActive, onClick = onPlay)
        Spacer(Modifier.width(12.dp))
        // The waveform spans the bubble between button and duration; a text-hidden bubble
        // instead sizes it by the line's length, and the bubble shrinks around it.
        VoiceWaveform(
            seed = item.startMs,
            progress = { animated },
            modifier = (if (waveformWidth == null) Modifier.weight(1f) else Modifier.width(waveformWidth))
                .height(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            formatLineDuration(item.durationMs),
            style = LineDuration,
            color = if (item.isMine) TextPrimary else TextTimestamp
        )
    }
}

/**
 * The row's play button — plays from the start of this line. Ruby on my lines, charcoal on
 * everyone else's; on my line under the playhead it becomes a glowing mic: your turn to speak.
 */
@Composable
private fun VoiceButton(isMine: Boolean, speaking: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .then(
                if (speaking) Modifier.dropShadow(CircleShape, Shadow(radius = 12.dp, color = Ruby, alpha = 0.5f))
                else Modifier
            )
            .clip(CircleShape)
            .background(if (isMine) Ruby else Hairline)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (speaking) Icons.Filled.Mic else Icons.Filled.PlayArrow,
            contentDescription = "Play from this line",
            modifier = Modifier.size(20.dp),
            tint = if (isMine) Color.White else TextPrimary
        )
    }
}

private fun formatLineDuration(ms: Long): String {
    val totalSeconds = (ms + 500) / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * Waveform width for a text-hidden bubble: linear in the line's duration, capped at 20s (longer
 * lines all read as "long"), so bubble width visually encodes how much speaking time the line
 * takes — the same convention chat apps use for voice messages.
 */
private fun waveformWidth(durationMs: Long): Dp =
    40.dp + 160.dp * (durationMs / 20_000f).coerceIn(0f, 1f)

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
        // Stage directions and timing cues are the one place the proscenium gold appears.
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.small,
            color = if (isActive) ProsceniumGold.copy(alpha = 0.12f) else Color.Transparent,
            border = if (isActive) BorderStroke(1.dp, ProsceniumGold.copy(alpha = 0.4f)) else null
        ) {
            Text(
                if (item.isCue) "◈ ${item.text}" else item.text,
                style = StageDirection,
                color = ProsceniumGold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * Shown while the playhead is inside the trained character's (muted) line: the floating
 * rehearsal prompt — a ruby-to-velvet pill under a pulsing crimson aura that signals the
 * mic-is-yours moment.
 */
@Composable
private fun SpeakNowBanner(modifier: Modifier = Modifier) {
    val aura = rememberInfiniteTransition(label = "speakNowAura").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "auraAlpha"
    )
    Row(
        modifier = modifier
            .padding(bottom = 12.dp)
            // Block form: the pulse is read in the draw phase, so it never recomposes.
            .dropShadow(CircleShape) {
                radius = 24.dp.toPx()
                offset = Offset(0f, 8.dp.toPx())
                color = Ruby
                alpha = aura.value
            }
            .background(Brush.horizontalGradient(listOf(Ruby, Velvet)), CircleShape)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Mic,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = Color.White
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Your line — speak now",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
