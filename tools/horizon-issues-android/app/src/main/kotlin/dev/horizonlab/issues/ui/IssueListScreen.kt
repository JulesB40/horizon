package dev.horizonlab.issues.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizonlab.issues.core.Issue
import dev.horizonlab.issues.core.IssueStatus
import dev.horizonlab.issues.ui.components.AvatarStack
import dev.horizonlab.issues.ui.components.LabelChip
import dev.horizonlab.issues.ui.components.SkeletonBox
import dev.horizonlab.issues.ui.components.StatusPill
import dev.horizonlab.issues.ui.components.ago
import dev.horizonlab.issues.ui.components.shortAgo
import dev.horizonlab.issues.ui.components.statusIcon
import dev.horizonlab.issues.ui.theme.GeistMono
import dev.horizonlab.issues.ui.theme.Theme
import dev.horizonlab.issues.ui.theme.Tone
import dev.horizonlab.issues.ui.theme.Type

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IssueListScreen(
    ui: ListUi,
    snackbar: SnackbarHostState,
    onRefresh: () -> Unit,
    onFilter: (Filter) -> Unit,
    onSort: (Sort) -> Unit,
    onQuery: (String) -> Unit,
    onLabel: (String?) -> Unit,
    onOpen: (Issue) -> Unit,
    onSettings: () -> Unit,
) {
    val p = Theme.palette
    val listState = rememberLazyListState()
    Scaffold(
        containerColor = p.screen,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .statusBarsPadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                item(key = "header") { Header(ui, onSettings) }
                item(key = "overview") { Overview(ui, onFilter) }
                stickyHeader(key = "controls") { Controls(ui, onFilter, onSort, onQuery, onLabel) }

                if (ui.error != null && ui.all.isNotEmpty()) {
                    item(key = "banner") { ErrorBanner(ui.error, ui.fetchedAt, onRefresh) }
                }

                when {
                    ui.loading && ui.all.isEmpty() -> items(8, key = { "skeleton$it" }) { SkeletonRow() }
                    ui.error != null && ui.all.isEmpty() -> item(key = "error") {
                        EmptyState(Icons.Rounded.ErrorOutline, p.danger, "Couldn't load issues", ui.error, "Try again", onRefresh)
                    }
                    ui.visible.isEmpty() -> item(key = "empty") {
                        EmptyState(
                            Icons.Rounded.Inbox, p.draft, "Nothing here",
                            if (ui.query.isNotBlank()) "No issue matches “${ui.query}”." else "No open issue in this view.",
                            if (ui.filter != Filter.ALL || ui.label != null || ui.query.isNotBlank()) "Clear filters" else null,
                        ) {
                            onFilter(Filter.ALL)
                            onLabel(null)
                            onQuery("")
                        }
                    }
                    else -> items(ui.visible, key = { it.number }) { issue ->
                        IssueRow(issue, Modifier.animateItem()) { onOpen(issue) }
                    }
                }
            }
        }
    }
}

// --- Header and overview --------------------------------------------------------------------------------------

@Composable
private fun Header(ui: ListUi, onSettings: () -> Unit) {
    val p = Theme.palette
    Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.width(8.dp))
            Text("Horizon", style = Type.sm.copy(fontWeight = FontWeight.Medium), color = p.secondary)
            Spacer(Modifier.weight(1f))
            RoundIcon(Icons.Rounded.Settings, "Settings", onSettings)
        }
        Spacer(Modifier.height(10.dp))
        Text("Issues", style = Type.xl3, color = p.foreground)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ui.repo.slug, style = Type.mono, color = p.tertiary)
            if (ui.fetchedAt > 0) {
                Text("  ·  updated ${ago(ui.fetchedAt)}", style = Type.xs2, color = p.tertiary)
            }
        }
    }
}

/** A sun on the horizon: the app's mark. */
@Composable
fun BrandMark(size: Int = 22) {
    val p = Theme.palette
    Canvas(Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(p.prominent)) {
        val w = this.size.width
        val r = w * 0.26f
        val horizon = w * 0.62f
        drawArc(p.primary, 180f, 180f, true, topLeft = Offset(w / 2 - r, horizon - r), size = Size(r * 2, r * 2))
        drawRoundRect(
            p.onProminent, topLeft = Offset(w * 0.18f, horizon + w * 0.04f), size = Size(w * 0.64f, w * 0.07f),
            cornerRadius = CornerRadius(w * 0.04f),
        )
    }
}

@Composable
fun RoundIcon(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = Theme.palette
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(p.grouped), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = p.foreground, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun Overview(ui: ListUi, onFilter: (Filter) -> Unit) {
    val p = Theme.palette
    val total = ui.all.size
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.card)
            .border(1.dp, p.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (ui.loading && total == 0) "—" else total.toString(), style = Type.xl2, color = p.foreground)
            Spacer(Modifier.width(6.dp))
            Text("open issues", style = Type.sm, color = p.secondary, modifier = Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(ui.loading || ui.refreshing, enter = fadeIn(), exit = fadeOut()) {
                CircularProgressIndicator(Modifier.size(14.dp).padding(bottom = 2.dp), color = p.tertiary, strokeWidth = 1.5.dp)
            }
        }
        Spacer(Modifier.height(12.dp))
        val segments = listOf(
            Filter.AVAILABLE to p.available,
            Filter.CLAIMED to p.claimed,
            Filter.HAS_PR to p.review,
        )
        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (total == 0) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(p.grouped))
            } else {
                segments.forEach { (filter, tone) ->
                    val count = ui.counts[filter] ?: 0
                    val weight by animateFloatAsState(count.toFloat(), label = "segment")
                    if (count > 0) {
                        Box(
                            Modifier
                                .weight(weight.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(tone.content.copy(alpha = if (ui.filter == Filter.ALL || ui.filter == filter) 1f else 0.3f)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Available", ui.counts[Filter.AVAILABLE] ?: 0, p.available, ui.filter == Filter.AVAILABLE) {
                onFilter(if (ui.filter == Filter.AVAILABLE) Filter.ALL else Filter.AVAILABLE)
            }
            StatTile("Claimed", ui.counts[Filter.CLAIMED] ?: 0, p.claimed, ui.filter == Filter.CLAIMED) {
                onFilter(if (ui.filter == Filter.CLAIMED) Filter.ALL else Filter.CLAIMED)
            }
            StatTile("Has PR", ui.counts[Filter.HAS_PR] ?: 0, p.review, ui.filter == Filter.HAS_PR) {
                onFilter(if (ui.filter == Filter.HAS_PR) Filter.ALL else Filter.HAS_PR)
            }
        }
    }
}

@Composable
private fun RowScope.StatTile(label: String, count: Int, tone: Tone, selected: Boolean, onClick: () -> Unit) {
    val p = Theme.palette
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(if (selected) tone.background else p.grouped)
            .border(1.dp, if (selected) tone.border else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(tone.content))
            Spacer(Modifier.width(6.dp))
            Text(label, style = Type.xs2, color = if (selected) tone.content else p.secondary, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(count.toString(), style = Type.lg, color = p.foreground)
    }
}

// --- Search, filters, sort ------------------------------------------------------------------------------------

@Composable
private fun Controls(
    ui: ListUi,
    onFilter: (Filter) -> Unit,
    onSort: (Sort) -> Unit,
    onQuery: (String) -> Unit,
    onLabel: (String?) -> Unit,
) {
    val p = Theme.palette
    Column(Modifier.fillMaxWidth().background(p.screen).padding(top = 4.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(ui.query, onQuery, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SortButton(ui.sort, onSort)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Filter.entries.forEach { f ->
                FilterPill(f.label, ui.counts[f] ?: 0, ui.filter == f) { onFilter(f) }
            }
            LabelMenu(ui, onLabel)
        }
        HorizontalDivider(color = p.separator, thickness = 1.dp)
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier) {
    val p = Theme.palette
    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.search)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = p.tertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = Type.sm.copy(color = p.foreground),
            cursorBrush = SolidColor(p.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search title, #number, @user, label", style = Type.sm, color = p.tertiary, maxLines = 1)
                    field()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close, "Clear", tint = p.secondary,
                modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onQuery("") },
            )
        }
    }
}

@Composable
private fun SortButton(sort: Sort, onSort: (Sort) -> Unit) {
    val p = Theme.palette
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(p.search)
                .border(1.dp, p.border, RoundedCornerShape(12.dp))
                .clickable { open = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Sort, "Sort", tint = p.foreground, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = p.card) {
            Sort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label, style = Type.sm, color = p.foreground) },
                    trailingIcon = { if (s == sort) Icon(Icons.Rounded.Check, null, tint = p.primaryText, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        onSort(s)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val p = Theme.palette
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) p.prominent else Color.Transparent)
            .border(1.dp, if (selected) p.prominent else p.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.xs.copy(fontWeight = FontWeight.Medium), color = if (selected) p.onProminent else p.foreground)
        Spacer(Modifier.width(5.dp))
        Text(
            count.toString(), style = Type.xs2,
            color = if (selected) p.onProminent.copy(alpha = 0.6f) else p.tertiary,
        )
    }
}

@Composable
private fun LabelMenu(ui: ListUi, onLabel: (String?) -> Unit) {
    val p = Theme.palette
    var open by remember { mutableStateOf(false) }
    val selected = ui.labels.firstOrNull { it.first.name == ui.label }?.first
    Box {
        if (selected != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(CircleShape).clickable { onLabel(null) }) {
                LabelChip(selected, selected = true)
            }
        } else {
            Row(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, p.border, CircleShape)
                    .clickable { open = true }
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Label", style = Type.xs.copy(fontWeight = FontWeight.Medium), color = p.foreground)
                Icon(Icons.Rounded.ExpandMore, null, tint = p.tertiary, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = p.card) {
            ui.labels.take(30).forEach { (label, count) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LabelChip(label)
                            Spacer(Modifier.width(8.dp))
                            Text(count.toString(), style = Type.xs2, color = p.tertiary)
                        }
                    },
                    onClick = {
                        onLabel(label.name)
                        open = false
                    },
                )
            }
        }
    }
}

// --- Rows -----------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IssueRow(issue: Issue, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = Theme.palette
    val tone = p.tone(issue.status)
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Box(
                Modifier.padding(top = 1.dp).size(22.dp).clip(CircleShape).background(tone.background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(statusIcon(issue.status), null, tint = tone.content, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    issue.title,
                    style = Type.base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                    color = p.foreground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${issue.number}", style = Type.xs2.copy(fontFamily = GeistMono), color = p.secondary)
                    val meta = listOfNotNull(issue.author?.login, shortAgo(issue.createdAt).ifEmpty { null })
                    if (meta.isNotEmpty()) {
                        Text(" · " + meta.joinToString(" · "), style = Type.xs2, color = p.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    StatusPill(issue)
                    issue.labels.take(3).forEach { LabelChip(it) }
                    if (issue.labels.size > 3) {
                        Text("+${issue.labels.size - 3}", style = Type.xs3, color = p.tertiary, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (issue.assignees.isNotEmpty()) AvatarStack(issue.assignees)
                if (issue.comments > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ChatBubbleOutline, null, tint = p.tertiary, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(issue.comments.toString(), style = Type.xs2, color = p.tertiary)
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 52.dp), color = p.separator, thickness = 1.dp)
    }
}

@Composable
private fun SkeletonRow() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
        SkeletonBox(Modifier.size(22.dp), CircleShape)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBox(Modifier.fillMaxWidth(0.85f).height(14.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.4f).height(10.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBox(Modifier.width(72.dp).height(18.dp), CircleShape)
                SkeletonBox(Modifier.width(48.dp).height(18.dp), CircleShape)
            }
        }
    }
}

// --- States ---------------------------------------------------------------------------------------------------

@Composable
fun EmptyState(icon: ImageVector, tone: Tone, title: String, body: String, action: String?, onAction: () -> Unit) {
    val p = Theme.palette
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(tone.background), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tone.content, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = Type.lg, color = p.foreground)
        Spacer(Modifier.height(6.dp))
        Text(body, style = Type.sm, color = p.secondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            Text(
                action,
                style = Type.sm.copy(fontWeight = FontWeight.SemiBold),
                color = p.onProminent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(p.prominent)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ErrorBanner(error: String, fetchedAt: Long, onRetry: () -> Unit) {
    val p = Theme.palette
    val tone = p.claimed
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tone.background)
            .border(1.dp, tone.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onRetry)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = tone.content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (fetchedAt > 0) "Showing the list from ${ago(fetchedAt)}" else "Showing a saved list",
                style = Type.xs.copy(fontWeight = FontWeight.SemiBold), color = tone.content,
            )
            Text(error, style = Type.xs2, color = tone.content.copy(alpha = 0.85f))
        }
        Spacer(Modifier.width(8.dp))
        Text("Retry", style = Type.xs.copy(fontWeight = FontWeight.SemiBold), color = tone.content)
    }
}

/** Kept for previews and the detail screen: the colour of a status. */
@Composable
fun statusTone(status: IssueStatus): Tone = Theme.palette.tone(status)
