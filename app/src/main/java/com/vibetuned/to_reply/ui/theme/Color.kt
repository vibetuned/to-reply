package com.vibetuned.to_reply.ui.theme

import androidx.compose.ui.graphics.Color

// "Theatrical Proscenium" palette — see DESIGN.md § Visual design. The Material 3 role
// table lives in Theme.kt; these are the named colours the design system assigns to
// specific components.

// Brand. Ruby and velvet are for fills and strokes (gauges, active borders, the speak-now
// pill), never small text: ruby on the canvas is 3.85:1, under WCAG AA for body copy —
// text accents use the scheme's tone-80 `primary` instead.
val Ruby = Color(0xFFE53935)
val Velvet = Color(0xFFB82638)
val ProsceniumGold = Color(0xFFD4AF37) // stage directions, cues, script metadata only
val DimRose = Color(0xFFA38287)

// Neutral elevation ladder: stage-pit canvas → elevated surface → card → hairline.
val StagePit = Color(0xFF0A0A0E)
val SurfaceElevated = Color(0xFF141418)
val CardContainer = Color(0xFF1E1E24)
val Hairline = Color(0xFF2B2B36)
val RowDivider = Color(0xFF1E1E26)

// Text levels.
val TextPrimary = Color(0xFFF8F8FA)
val TextSecondary = Color(0xFF9E9EA8)
val TextTimestamp = Color(0xFF7A7A88)

// Dialogue cards.
val OpposingCard = Color(0xFF1A1A22)
val OpposingCardBorder = Color(0xFF2C2C38)
val OpposingName = Color(0xFFD47A7A)
val MyCard = Color(0x408B1E2B)       // rgba(139, 30, 43, 0.25)
val MyCardActive = Color(0x598B1E2B) // rgba(139, 30, 43, 0.35) — the "stage highlight"

// Glass (floating controls and header badges). No backdrop blur behind Compose content,
// so these read as tinted translucency over the canvas.
val Glass = Color(0xBF1A1A22)        // rgba(26, 26, 34, 0.75)
val GlassBorder = Color(0x26FFFFFF)  // rgba(255, 255, 255, 0.15)
val GlassBadge = Color(0xA61E1E28)   // rgba(30, 30, 40, 0.65)
val GlassBadgeBorder = Color(0x1FFFFFFF) // rgba(255, 255, 255, 0.12)
