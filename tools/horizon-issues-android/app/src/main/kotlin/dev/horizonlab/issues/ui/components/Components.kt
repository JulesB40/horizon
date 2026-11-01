package dev.horizonlab.issues.ui.components

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.horizonlab.issues.core.ClaimRules
import dev.horizonlab.issues.core.Issue
import dev.horizonlab.issues.core.IssueStatus
import dev.horizonlab.issues.core.Label
import dev.horizonlab.issues.core.PrState
import dev.horizonlab.issues.core.User
import dev.horizonlab.issues.ui.theme.Palette
import dev.horizonlab.issues.ui.theme.Theme
import dev.horizonlab.issues.ui.theme.Tone
import dev.horizonlab.issues.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

// --- Pills and badges -----------------------------------------------------------------------------------------

/** A rounded tinted badge, with an optional leading dot or icon. */
@Composable
fun Pill(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dot: Boolean = false,
    compact: Boolean = true,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(tone.background)
            .border(1.dp, tone.border, CircleShape)
            .padding(if (compact) PaddingValues(horizontal = 9.dp, vertical = 3.dp) else PaddingValues(horizontal = 12.dp, vertical = 6.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(tone.content))
            Spacer(Modifier.width(6.dp))
        }
        if (icon != null) {
            Icon(icon, null, tint = tone.content, modifier = Modifier.size(if (compact) 12.dp else 14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = (if (compact) Type.xs3 else Type.xs).copy(fontWeight = FontWeight.SemiBold),
            color = tone.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun statusIcon(status: IssueStatus): ImageVector = when (status) {
    IssueStatus.AVAILABLE -> Icons.Rounded.RadioButtonUnchecked
    IssueStatus.CLAIMED -> Icons.Rounded.Schedule
    IssueStatus.HAS_PR -> Icons.Rounded.CallMerge
    IssueStatus.FIXED -> Icons.Rounded.DoneAll
}

fun prIcon(state: PrState): ImageVector = when (state) {
    PrState.OPEN -> Icons.Rounded.CallMerge
    PrState.DRAFT -> Icons.Rounded.Edit
    PrState.MERGED -> Icons.Rounded.DoneAll
    PrState.CLOSED -> Icons.Rounded.Close
}

fun prStateLabel(state: PrState): String = when (state) {
    PrState.OPEN -> "Open"
    PrState.DRAFT -> "Draft"
    PrState.MERGED -> "Merged"
    PrState.CLOSED -> "Closed"
}

/** The one-line status of an issue: who holds it, which pull request, or that it is free. */
fun statusText(issue: Issue, now: Long = System.currentTimeMillis()): String = when (issue.status) {
    IssueStatus.AVAILABLE -> "Available"
    IssueStatus.CLAIMED -> {
        val who = issue.assignees.firstOrNull()?.let { "@" + it.login } ?: "On Discord"
        val more = if (issue.assignees.size > 1) " +${issue.assignees.size - 1}" else ""
        val left = issue.claims.mapNotNull { ClaimRules.releaseAt(it, issue) }.minOrNull()
            ?.let { " · " + countdown(it - now) }.orEmpty()
        who + more + left
    }
    IssueStatus.HAS_PR -> {
        val pr = issue.primaryPr!!
        (if (pr.state == PrState.DRAFT) "Draft #" else "PR #") + pr.number
    }
    IssueStatus.FIXED -> "Fixed" + (issue.fixingPr?.let { " · #${it.number}" } ?: "")
}

@Composable
fun StatusPill(issue: Issue, modifier: Modifier = Modifier, compact: Boolean = true) {
    val p = Theme.palette
    val tone = if (issue.status == IssueStatus.HAS_PR) p.tone(issue.primaryPr!!.state) else p.tone(issue.status)
    Pill(statusText(issue), tone, modifier, icon = statusIcon(issue.status), compact = compact)
}

/** A GitHub label: its own colour as a dot, on a neutral chip. */
@Composable
fun LabelChip(label: Label, modifier: Modifier = Modifier, selected: Boolean = false) {
    val p = Theme.palette
    val color = labelColor(label)
    Row(
        modifier
            .clip(CircleShape)
            .background(if (selected) color.copy(alpha = 0.18f) else p.grouped)
            .border(1.dp, if (selected) color.copy(alpha = 0.5f) else p.border, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label.name, style = Type.xs3.copy(fontWeight = FontWeight.Medium), color = p.secondary, maxLines = 1)
    }
}

fun labelColor(label: Label): Color =
    runCatching { Color(android.graphics.Color.parseColor("#" + label.color.removePrefix("#"))) }
        .getOrDefault(Color(0xFF8B8B8B))

// --- Avatars --------------------------------------------------------------------------------------------------

@Composable
fun Avatar(user: User?, size: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val p = Theme.palette
    val login = user?.login.orEmpty()
    Box(
        modifier
            .size(size)
            .then(if (ring != null) Modifier.border(1.5.dp, ring, CircleShape) else Modifier)
            .padding(if (ring != null) 1.5.dp else 0.dp)
            .clip(CircleShape)
            .background(p.grouped),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            login.take(1).uppercase(),
            style = Type.xs3.copy(fontWeight = FontWeight.SemiBold),
            color = p.secondary,
        )
        val url = user?.avatarUrl.orEmpty()
        if (url.isNotEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(sizedAvatar(url, (size.value * 3).toInt()))
                    .crossfade(true)
                    .build(),
                contentDescription = login,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

private fun sizedAvatar(url: String, px: Int): String = url + (if ('?' in url) "&" else "?") + "s=$px"

/** Overlapping avatars, at most 3. */
@Composable
fun AvatarStack(users: List<User>, size: Dp = 22.dp) {
    val p = Theme.palette
    val shown = users.take(3)
    val step = size * 2 / 3
    Box(Modifier.width(size + step * (shown.size - 1).coerceAtLeast(0))) {
        shown.forEachIndexed { i, user -> Avatar(user, size, Modifier.offset(x = step * i), ring = p.screen) }
    }
}

// --- Loading --------------------------------------------------------------------------------------------------

@Composable
fun SkeletonBox(modifier: Modifier, shape: RoundedCornerShape = RoundedCornerShape(6.dp)) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Box(modifier.alpha(alpha).clip(shape).background(Theme.palette.skeleton))
}

// --- Time -----------------------------------------------------------------------------------------------------

/** `now`, `5m`, `3h`, `2d`, `Oct 12`, `Oct 12, 2025`. */
fun shortAgo(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0) return ""
    val s = (now - millis) / 1000
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        s < 7 * 86_400 -> "${s / 86_400}d"
        else -> date(millis, now)
    }
}

fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
    val short = shortAgo(millis, now)
    return when {
        short.isEmpty() -> ""
        short == "now" -> "just now"
        short.last().isDigit() -> "on $short"
        else -> "$short ago"
    }
}

private fun date(millis: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val then = Instant.ofEpochMilli(millis).atZone(zone)
    val pattern = if (then.year == Instant.ofEpochMilli(now).atZone(zone).year) "MMM d" else "MMM d, yyyy"
    return DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(then)
}

/** `2d left`, `5h left`, `due` for a time span in milliseconds. */
fun countdown(span: Long): String {
    if (span <= 0) return "releasing"
    val h = span / 3_600_000
    return if (h >= 24) "${h / 24}d left" else "${maxOf(1, h)}h left"
}

fun Long.plural(word: String): String = "$this $word" + if (abs(this) == 1L) "" else "s"
fun Int.plural(word: String): String = toLong().plural(word)

// --- Links ----------------------------------------------------------------------------------------------------

/** Opens a link in a Custom Tab tinted like the app. */
fun openUrl(context: Context, url: String, palette: Palette) {
    val colors = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(palette.screen.toArgb())
        .setNavigationBarColor(palette.screen.toArgb())
        .build()
    val intent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setDefaultColorSchemeParams(colors)
        .setColorScheme(if (palette.dark) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
        .build()
    runCatching { intent.launchUrl(context, Uri.parse(url)) }
}
