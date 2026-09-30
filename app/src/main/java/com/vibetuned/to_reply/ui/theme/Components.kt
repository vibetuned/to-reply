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
import androidx.compose.ui.graphics.StrokeCap
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
