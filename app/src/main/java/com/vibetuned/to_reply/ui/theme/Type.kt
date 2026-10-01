package com.vibetuned.to_reply.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vibetuned.to_reply.R

// Bundled (res/font, OFL — licenses in assets/licenses) rather than downloadable: the app has
// no network access and must render the same on devices without Play services.
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

// The design system's type scale, then mapped onto the Material roles below.
private val Display = TextStyle(
    fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold,
    fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = (-0.02).em
)
private val HeadlineLg = TextStyle(
    fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold,
    fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.015).em
)
private val HeadlineMd = TextStyle(
    fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold,
    fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.01).em
)
private val HeadlineSm = TextStyle(
    fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp, lineHeight = 22.sp
)
private val BodyLg = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Normal,
    fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = (-0.01).em
)
private val BodyMd = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Normal,
    fontSize = 15.sp, lineHeight = 21.sp
)
private val BodySm = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Normal,
    fontSize = 13.sp, lineHeight = 18.sp
)
private val LabelSm = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Medium,
    fontSize = 11.sp, lineHeight = 14.sp
)

val Typography = Typography(
    displayLarge = Display,
    displayMedium = Display,
    displaySmall = Display,
    headlineLarge = HeadlineLg,
    headlineMedium = HeadlineMd,
    headlineSmall = HeadlineSm,
    titleLarge = HeadlineLg,
    titleMedium = HeadlineMd,
    titleSmall = HeadlineSm,
    bodyLarge = BodyLg,
    bodyMedium = BodyMd,
    bodySmall = BodySm,
    // Buttons and chips — not in the design system's scale, so Inter at Material's sizes.
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp
    ),
    labelSmall = LabelSm,
)

/** Character names and badges (`label-character`). Callers pass the text uppercased. */
val LabelCharacter = TextStyle(
    fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold,
    fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em
)

/** A line's spoken duration beside its waveform; tabular digits keep `0:09` / `0:45` aligned. */
val LineDuration = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Medium,
    fontSize = 13.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"
)

/** Stage directions, staging notes and cues (`stage-direction`). */
val StageDirection = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic,
    fontSize = 13.sp, lineHeight = 18.sp
)
