package com.vibetuned.to_reply.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The design system's Material 3 role table (DESIGN.md § Visual design). Accent roles are the
 * table's values verbatim; the surface ladder and text levels follow the prose's explicit
 * values (stage-pit canvas, #141418 / #1E1E24 containers, #F8F8FA / #9E9EA8 text), since the
 * table is the M3 derivation of those same seeds.
 */
private val StageColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4AC),
    onPrimary = Color(0xFF690006),
    primaryContainer = Color(0xFFFF544C),
    onPrimaryContainer = Color(0xFF5C0005),
    inversePrimary = Color(0xFFBB171C),
    secondary = Color(0xFFFFB3B3),
    onSecondary = Color(0xFF680015),
    secondaryContainer = Color(0xFF960423),
    onSecondaryContainer = Color(0xFFFF9E9F),
    tertiary = Color(0xFFE9C349),
    onTertiary = Color(0xFF3C2F00),
    tertiaryContainer = Color(0xFFCCA830),
    onTertiaryContainer = Color(0xFF4F3E00),
    background = StagePit,
    onBackground = TextPrimary,
    surface = StagePit,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF353439),
    onSurfaceVariant = TextSecondary,
    surfaceTint = Color(0xFFFFB4AC),
    inverseSurface = Color(0xFFE4E1E7),
    inverseOnSurface = Color(0xFF303034),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFAB8985),
    outlineVariant = Hairline,
    surfaceBright = Color(0xFF39393D),
    surfaceDim = StagePit,
    surfaceContainerLowest = StagePit,
    surfaceContainerLow = SurfaceElevated,
    surfaceContainer = CardContainer,
    surfaceContainerHigh = Color(0xFF2A292E),
    surfaceContainerHighest = Color(0xFF353439),
)

/**
 * Dark only, and no dynamic color: the design system is a darkened auditorium, and wallpaper
 * colors would replace the brand's ruby/gold.
 */
@Composable
fun ToReplyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StageColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
