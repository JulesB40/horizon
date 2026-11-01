package dev.horizonlab.issues.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import dev.horizonlab.issues.R

/** Geist, Horizon's UI font (crates/horizon-theme/fonts), copied in by the `copyThemeFonts` build task. */
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_semibold, FontWeight.Bold),
)

val GeistMono = FontFamily(Font(R.font.geistmono_regular, FontWeight.Normal))

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = 0.sp) =
    TextStyle(fontFamily = Geist, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking)

/** A compact type scale: 11/14 up to 30/36. */
object Type {
    val xs3 = style(11, 14)
    val xs2 = style(12, 16)
    val xs = style(13, 17)
    val sm = style(14, 19)
    val base = style(16, 23)
    val lg = style(18, 23, FontWeight.SemiBold)
    val xl = style(21, 28, FontWeight.SemiBold, (-0.2).sp)
    val xl2 = style(26, 32, FontWeight.SemiBold, (-0.4).sp)
    val xl3 = style(30, 36, FontWeight.SemiBold, (-0.6).sp)
    val mono = TextStyle(fontFamily = GeistMono, fontSize = 12.sp, lineHeight = 16.sp)
}

val AppTypography = Typography(
    displaySmall = Type.xl3,
    headlineSmall = Type.xl2,
    titleLarge = Type.xl,
    titleMedium = style(16, 23, FontWeight.Medium),
    titleSmall = style(14, 19, FontWeight.Medium),
    bodyLarge = Type.base,
    bodyMedium = Type.sm,
    bodySmall = Type.xs,
    labelLarge = style(14, 19, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium),
)
