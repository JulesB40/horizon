package dev.horizonlab.issues.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.horizonlab.issues.core.IssueStatus
import dev.horizonlab.issues.core.PrState

/** A tinted badge: background, text, hairline. */
@Immutable
data class Tone(val background: Color, val content: Color, val border: Color)

/** Neutral zinc surfaces, near-black at night, one blue accent and soft tinted status tones. */
@Immutable
data class Palette(
    val dark: Boolean,
    val screen: Color,
    val card: Color,
    val grouped: Color,
    val pressed: Color,
    val border: Color,
    val separator: Color,
    val foreground: Color,
    val secondary: Color,
    val tertiary: Color,
    val primary: Color,
    val primaryText: Color,
    val prominent: Color,
    val onProminent: Color,
    val search: Color,
    val skeleton: Color,
    val available: Tone,
    val claimed: Tone,
    val review: Tone,
    val draft: Tone,
    val merged: Tone,
    val danger: Tone,
) {
    fun tone(status: IssueStatus): Tone = when (status) {
        IssueStatus.AVAILABLE -> available
        IssueStatus.CLAIMED -> claimed
        IssueStatus.HAS_PR -> review
        IssueStatus.FIXED -> merged
    }

    fun tone(state: PrState): Tone = when (state) {
        PrState.OPEN -> review
        PrState.DRAFT -> draft
        PrState.MERGED -> merged
        PrState.CLOSED -> danger
    }
}

private fun tone(bg: Long, fg: Long, borderAlpha: Float = 0.28f) =
    Tone(Color(bg), Color(fg), Color(fg).copy(alpha = borderAlpha))

val LightPalette = Palette(
    dark = false,
    screen = Color(0xFFFCFCFC),
    card = Color(0xFFFFFFFF),
    grouped = Color(0xFFF4F4F5),
    pressed = Color(0xFFF4F4F5),
    border = Color(0xFFE4E4E7),
    separator = Color(0xFFE4E4E7).copy(alpha = 0.75f),
    foreground = Color(0xFF27272A),
    secondary = Color(0xFF6F6F79),
    tertiary = Color(0xFFA1A1AA),
    primary = Color(0xFF1B4ED8),
    primaryText = Color(0xFF1B4ED8),
    prominent = Color(0xFF27272A),
    onProminent = Color(0xFFFCFCFC),
    search = Color(0xFFF4F4F5),
    skeleton = Color(0xFFEDEDEF),
    available = tone(0xFFE9F7EF, 0xFF15803D),
    claimed = tone(0xFFFCF4E8, 0xFFBB4D00),
    review = tone(0xFFE0E6F7, 0xFF1B4ED8),
    draft = tone(0xFFF4F4F5, 0xFF71717B, 0.22f),
    merged = tone(0xFFF3EBFD, 0xFF7E22CE),
    danger = tone(0xFFFCEBEC, 0xFFC10007),
)

val DarkPalette = Palette(
    dark = true,
    screen = Color(0xFF0A0A0A),
    card = Color(0xFF111111),
    grouped = Color(0xFF1A1B1B),
    pressed = Color(0xFF141414),
    border = Color(0xFF1F1F1F),
    separator = Color(0xFF191919),
    foreground = Color(0xFFF5F5F5),
    secondary = Color(0xFF838383),
    tertiary = Color(0xFF5E5E5E),
    primary = Color(0xFF346BF1),
    primaryText = Color(0xFF4B7CF3),
    prominent = Color(0xFFF5F5F5),
    onProminent = Color(0xFF0A0A0A),
    search = Color(0xFF141414),
    skeleton = Color(0xFF171717),
    available = tone(0xFF0C2416, 0xFF4ADE80, 0.22f),
    claimed = tone(0xFF312108, 0xFFFFB900, 0.26f),
    review = tone(0xFF121B34, 0xFF51A2FF, 0.26f),
    draft = tone(0xFF1A1B1B, 0xFFA3A3A3, 0.18f),
    merged = tone(0xFF1F1433, 0xFFC084FC, 0.26f),
    danger = tone(0xFF301214, 0xFFFF6467, 0.26f),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }
