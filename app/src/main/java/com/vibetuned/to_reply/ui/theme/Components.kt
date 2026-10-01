package com.vibetuned.to_reply.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The design system's progress track: a [Hairline] rail with a ruby elapsed fill and, when
 * [glow] is set, a subtle 4dp glow. Display only — the app deliberately has no scrubber.
 * [progress] is read in the draw phase, so an animating value redraws without recomposing.
 */
@Composable
fun StageTrack(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    thickness: Dp = 3.dp,
    cap: StrokeCap = StrokeCap.Round,
    glow: Boolean = true,
) {
    val glowRadiusPx = with(LocalDensity.current) { 4.dp.toPx() }
    val glowPaint = remember(glowRadiusPx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = Ruby.copy(alpha = 0.6f).toArgb()
            maskFilter = BlurMaskFilter(glowRadiusPx, BlurMaskFilter.Blur.NORMAL)
        }
    }
    Canvas(modifier.height(thickness)) {
        val stroke = size.height
        val y = size.height / 2
        // A round cap overhangs each end by half the stroke — inset so the track stays inside.
        val inset = if (cap == StrokeCap.Round) stroke / 2 else 0f
        drawLine(Hairline, Offset(inset, y), Offset(size.width - inset, y), stroke, cap)
        val fraction = progress().coerceIn(0f, 1f)
        if (fraction <= 0f) return@Canvas
        val end = (inset + (size.width - 2 * inset) * fraction)
        if (glow) drawIntoCanvas { canvas ->
            glowPaint.strokeWidth = stroke
            glowPaint.strokeCap = if (cap == StrokeCap.Round) android.graphics.Paint.Cap.ROUND
            else android.graphics.Paint.Cap.BUTT
            canvas.nativeCanvas.drawLine(inset, y, end, y, glowPaint)
        }
        drawLine(Ruby, Offset(inset, y), Offset(end, y), stroke, cap)
    }
}

/**
 * Voice-message waveform: rounded 3dp bars about 6dp apart — the pitch is stretched so the bars
 * span the whole width, edge to edge — ruby up to [progress] and [WaveformIdle] after it — the played part sweeps smoothly through a bar rather than flipping
 * whole bars. There is no per-line amplitude data, so bar heights are a stable pseudo-random
 * pattern derived from [seed]: every line gets its own shape, and it never changes between
 * frames. [progress] is read in the draw phase.
 */
@Composable
fun VoiceWaveform(
    seed: Long,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val bar = 3.dp.toPx()
        val count = ((size.width + 3.dp.toPx()) / 6.dp.toPx()).toInt().coerceAtLeast(1)
        val pitch = if (count > 1) (size.width - bar) / (count - 1) else 0f
        fun DrawScope.bars(color: Color) {
            for (i in 0 until count) {
                val height = size.height * waveformLevel(seed, i)
                val x = i * pitch + bar / 2
                val top = (size.height - height) / 2
                drawLine(
                    color,
                    Offset(x, top + bar / 2),
                    Offset(x, top + height - bar / 2),
                    strokeWidth = bar,
                    cap = StrokeCap.Round
                )
            }
        }
        bars(WaveformIdle)
        val played = size.width * progress().coerceIn(0f, 1f)
        if (played > 0f) clipRect(right = played) { bars(Ruby) }
    }
}

/**
 * Bar height as a fraction (0.3–1.0) of the waveform's height: an integer hash of (seed, index).
 * Bit-for-bit the same as the iOS port, so a line has the same bars, in the same order, on
 * both platforms.
 */
internal fun waveformLevel(seed: Long, index: Int): Float {
    var h = (seed.toInt() * 0x9E3779B1.toInt()) xor (index * 0x85EBCA77.toInt())
    h = h xor (h ushr 15)
    h *= 0x2C1B3C6D
    h = h xor (h ushr 12)
    h *= 0x297A2D39
    h = h xor (h ushr 15)
    return 0.3f + 0.7f * (h and 0xFFFF) / 65535f
}

/**
 * Glass badge pairing the comedy/tragedy masks with a character persona — `[🎭 LOMOV]`, the
 * design system's header selector. Clickable when [onClick] is set.
 */
@Composable
fun CharacterBadge(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .clip(CircleShape)
            .background(GlassBadge)
            .border(1.dp, GlassBadgeBorder, CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.TheaterComedy,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = Ruby
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text.uppercase(),
            style = LabelCharacter,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
