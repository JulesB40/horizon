package dev.horizonlab.issues.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.horizonlab.issues.core.ClaimRules
import dev.horizonlab.issues.core.ClaimVerdict
import dev.horizonlab.issues.core.Comment
import dev.horizonlab.issues.core.Issue
import dev.horizonlab.issues.core.IssueStatus
import dev.horizonlab.issues.core.LinkKind
import dev.horizonlab.issues.core.LinkedPr
import dev.horizonlab.issues.core.PrState
import dev.horizonlab.issues.core.Repo
import dev.horizonlab.issues.ui.components.Avatar
import dev.horizonlab.issues.ui.components.AvatarStack
import dev.horizonlab.issues.ui.components.LabelChip
import dev.horizonlab.issues.ui.components.Markdown
import dev.horizonlab.issues.ui.components.Pill
import dev.horizonlab.issues.ui.components.SkeletonBox
import dev.horizonlab.issues.ui.components.ago
import dev.horizonlab.issues.ui.components.countdown
import dev.horizonlab.issues.ui.components.openUrl
import dev.horizonlab.issues.ui.components.plural
import dev.horizonlab.issues.ui.components.prIcon
import dev.horizonlab.issues.ui.components.prStateLabel
import dev.horizonlab.issues.ui.components.shortAgo
import dev.horizonlab.issues.ui.components.statusIcon
import dev.horizonlab.issues.ui.theme.GeistMono
import dev.horizonlab.issues.ui.theme.Theme
import dev.horizonlab.issues.ui.theme.Type

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IssueDetailScreen(
    number: Int,
    repo: Repo,
    listCopy: Issue?,
    state: DetailUi,
    canComment: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onClaim: () -> Unit,
) {
    val p = Theme.palette
    val context = LocalContext.current
    val issue = state.detail?.issue ?: listCopy
    val url = issue?.url ?: "${repo.url}/issues/$number"
    var confirmClaim by remember { mutableStateOf(false) }
    val comments = state.detail?.comments.orEmpty()
    val assignees = issue?.assignees.orEmpty()
    // What came of each /claim in the thread: held, refused by the workflow, or over.
    val verdicts = remember(comments, assignees) { ClaimRules.verdicts(comments, assignees) }
    val heldSince = remember(comments, verdicts) {
        comments.filter { verdicts[it.id] == ClaimVerdict.HOLDS }.associate { it.author!!.login.lowercase() to it.createdAt }
    }

    Scaffold(
        containerColor = p.screen,
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column(Modifier.background(p.screen).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
                    Spacer(Modifier.width(6.dp))
                    Text("#$number", style = Type.sm.copy(fontFamily = GeistMono), color = p.secondary)
                    Spacer(Modifier.weight(1f))
                    RoundIcon(Icons.Rounded.Share, "Share", {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, (issue?.title?.let { "$it\n" } ?: "") + url)
                        context.startActivity(Intent.createChooser(send, null))
                    })
                    RoundIcon(Icons.AutoMirrored.Rounded.OpenInNew, "Open on GitHub", { openUrl(context, url, p) })
                }
                HorizontalDivider(color = p.separator, thickness = 1.dp)
            }
        },
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.detail != null,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(inner),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                if (issue == null) {
                    item {
                        if (state.error != null) {
                            EmptyState(Icons.Rounded.ErrorOutline, p.danger, "Couldn't load #$number", state.error, "Try again", onRefresh)
                        } else {
                            DetailSkeleton()
                        }
                    }
                    return@LazyColumn
                }

                item(key = "title") {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                        Text(issue.title, style = Type.xl, color = p.foreground)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(issue.author, 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                listOfNotNull(
                                    issue.author?.login,
                                    "opened ${ago(issue.createdAt)}".takeIf { issue.createdAt > 0 },
                                    issue.comments.plural("comment"),
                                ).joinToString(" · "),
                                style = Type.xs, color = p.secondary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (issue.labels.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                issue.labels.forEach { LabelChip(it) }
                            }
                        }
                    }
                }

                item(key = "status") {
                    StatusCard(issue, heldSince, canComment, state.posting, detailLoaded = state.detail != null, onClaim = { confirmClaim = true }) {
                        openUrl(context, url, p)
                    }
                }

                item(key = "prs-title") { SectionTitle("Pull requests", issue.linkedPrs.size) }
                item(key = "prs") {
                    Card {
                        if (issue.linkedPrs.isEmpty()) {
                            Text(
                                if (state.detail == null) "Checking the timeline…" else "No pull request links this issue yet.",
                                style = Type.sm, color = p.tertiary, modifier = Modifier.padding(16.dp),
                            )
                        }
                        issue.linkedPrs.forEachIndexed { i, pr ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = p.separator, thickness = 1.dp)
                            PrRow(pr) { openUrl(context, pr.url, p) }
                        }
                    }
                }

                item(key = "body-title") { SectionTitle("Description", null) }
                item(key = "body") {
                    Card {
                        val body = state.detail?.issue?.body ?: issue.body
                        if (body == null && state.detail == null) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SkeletonBox(Modifier.fillMaxWidth().height(12.dp))
                                SkeletonBox(Modifier.fillMaxWidth(0.9f).height(12.dp))
                                SkeletonBox(Modifier.fillMaxWidth(0.6f).height(12.dp))
                            }
                        } else {
                            Markdown(body.orEmpty(), repo.url, Modifier.padding(16.dp))
                        }
                    }
                }

                if (comments.isNotEmpty()) {
                    item(key = "comments-title") { SectionTitle("Comments", comments.size) }
                    items(comments, key = { "c" + it.id }) { CommentItem(it, verdicts[it.id], repo) { openUrl(context, it.url, p) } }
                }

                if (state.error != null) {
                    item(key = "error") {
                        Text(state.error, style = Type.xs, color = p.danger.content, modifier = Modifier.padding(20.dp))
                    }
                }
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (confirmClaim) {
        AlertDialog(
            onDismissRequest = { confirmClaim = false },
            containerColor = p.card,
            title = { Text("Claim #$number?", style = Type.lg, color = p.foreground) },
            text = {
                Text(
                    "This comments /claim on the issue as you. The claim workflow assigns you; " +
                        "open a pull request within ${ClaimRules.STALE_DAYS} days or the claim is released.",
                    style = Type.sm, color = p.secondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClaim = false
                    onClaim()
                }) { Text("Claim", color = p.primaryText, style = Type.sm.copy(fontWeight = FontWeight.SemiBold)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClaim = false }) { Text("Cancel", color = p.secondary, style = Type.sm) }
            },
        )
    }
}

// --- Status ---------------------------------------------------------------------------------------------------

@Composable
private fun StatusCard(
    issue: Issue,
    heldSince: Map<String, Long>,
    canComment: Boolean,
    posting: Boolean,
    detailLoaded: Boolean,
    onClaim: () -> Unit,
    onOpen: () -> Unit,
) {
    val p = Theme.palette
    val status = issue.status
    val pr = issue.primaryPr
    val tone = if (status == IssueStatus.HAS_PR && pr != null) p.tone(pr.state) else p.tone(status)
    val shape = RoundedCornerShape(20.dp)
    val now = System.currentTimeMillis()
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(tone.background)
            .border(1.dp, tone.border, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(tone.content.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(statusIcon(status), null, tint = tone.content, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (status) {
                        IssueStatus.AVAILABLE -> "Available"
                        IssueStatus.CLAIMED -> "Claimed"
                        IssueStatus.HAS_PR -> if (pr?.state == PrState.DRAFT) "Draft in progress" else "In review"
                        IssueStatus.FIXED -> "Fixed"
                    },
                    style = Type.base.copy(fontWeight = FontWeight.SemiBold), color = tone.content,
                )
                Text(
                    when (status) {
                        IssueStatus.AVAILABLE -> "Nobody holds it and no pull request links it."
                        IssueStatus.CLAIMED -> claimLine(issue, heldSince)
                        IssueStatus.HAS_PR -> "Pull request #${pr!!.number}" + (pr.author?.let { " by @${it.login}" } ?: "") +
                            if (pr.kind == LinkKind.CLOSES) " closes it" else " mentions it"
                        IssueStatus.FIXED -> "Closed ${ago(issue.closedAt)}" + (
                            issue.fixingPr?.let { fix -> " by pull request #${fix.number}" + (fix.author?.let { " from @${it.login}" } ?: "") }
                                ?: ", no merged pull request found"
                            ) + "."
                    },
                    style = Type.xs, color = tone.content.copy(alpha = 0.85f),
                )
            }
            if (issue.assignees.isNotEmpty()) AvatarStack(issue.assignees, 26.dp)
        }

        // The release clock of claims made with /claim (tools/ci/claim_command.py releases them after 5 days).
        issue.claims.forEach { claim ->
            val releaseAt = ClaimRules.releaseAt(claim, issue) ?: return@forEach
            val progress = ((now - claim.claimedAt).toFloat() / ClaimRules.STALE_MILLIS).coerceIn(0f, 1f)
            Spacer(Modifier.height(14.dp))
            Row {
                Text("@${claim.login}", style = Type.xs2.copy(fontWeight = FontWeight.SemiBold), color = tone.content)
                Spacer(Modifier.weight(1f))
                Text(
                    "${countdown(releaseAt - now)} without a PR",
                    style = Type.xs2, color = tone.content.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = tone.content,
                trackColor = tone.content.copy(alpha = 0.15f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }

        if (status == IssueStatus.AVAILABLE) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val enabled = !posting
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(p.prominent)
                        .clickable(enabled = enabled) { if (canComment) onClaim() else onOpen() }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (posting) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = p.onProminent, strokeWidth = 1.5.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (canComment) "Claim this issue" else "Claim on GitHub",
                        style = Type.sm.copy(fontWeight = FontWeight.SemiBold), color = p.onProminent,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (canComment) "Posts /claim as you" else "Comment /claim there",
                    style = Type.xs2, color = tone.content.copy(alpha = 0.8f),
                )
            }
        } else if (status == IssueStatus.CLAIMED && !detailLoaded && issue.claims.isEmpty() && issue.assignees.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Reading the claim's timeline…", style = Type.xs2, color = tone.content.copy(alpha = 0.7f))
        }
    }
}

private fun claimLine(issue: Issue, heldSince: Map<String, Long>): String {
    if (issue.assignees.isEmpty()) return "Taken on Discord (Kira's “in progress” label)."
    val who = issue.assignees.joinToString(", ") { "@" + it.login }
    val claim = issue.claims.filter { it.claimedAt > 0 }.maxByOrNull { it.claimedAt }
    val comment = issue.assignees.mapNotNull { heldSince[it.login.lowercase()] }.maxOrNull()
    return when {
        claim == null && comment != null -> "$who claimed it ${ago(comment)}."
        claim == null -> "Assigned to $who."
        claim.viaCommand -> "$who claimed it with /claim ${ago(claim.claimedAt)}."
        else -> "Assigned to $who ${ago(claim.claimedAt)}, by hand: no expiry."
    }
}

// --- Pull requests and comments -------------------------------------------------------------------------------

@Composable
private fun PrRow(pr: LinkedPr, onClick: () -> Unit) {
    val p = Theme.palette
    val tone = p.tone(pr.state)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(tone.background), contentAlignment = Alignment.Center) {
            Icon(prIcon(pr.state), null, tint = tone.content, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(pr.title, style = Type.sm.copy(fontWeight = FontWeight.Medium), color = p.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull("#${pr.number}", pr.author?.login, shortAgo(pr.updatedAt).ifEmpty { null }).joinToString(" · "),
                style = Type.xs2, color = p.tertiary, maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill(prStateLabel(pr.state), tone)
            Text(
                if (pr.kind == LinkKind.CLOSES) "closes" else "mentions",
                style = Type.xs3, color = p.tertiary,
            )
        }
    }
}

@Composable
private fun CommentItem(comment: Comment, verdict: ClaimVerdict?, repo: Repo, onOpen: () -> Unit) {
    val p = Theme.palette
    val badge = when (verdict) {
        ClaimVerdict.HOLDS -> "claim" to p.claimed
        ClaimVerdict.REFUSED -> "refused" to p.danger
        ClaimVerdict.ENDED -> "claim · over" to p.draft
        ClaimVerdict.UNCLAIM -> "unclaim" to p.draft
        null -> null
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Avatar(comment.author, 30.dp, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(p.card)
                .border(1.dp, if (verdict == ClaimVerdict.HOLDS) p.claimed.border else p.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.author?.login ?: "ghost",
                    style = Type.xs.copy(fontWeight = FontWeight.SemiBold), color = p.foreground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (comment.author?.isBot == true) {
                    Spacer(Modifier.width(6.dp))
                    Pill("bot", p.draft)
                }
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Pill(badge.first, badge.second)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    shortAgo(comment.createdAt), style = Type.xs2, color = p.tertiary,
                    modifier = Modifier.clickable(onClick = onOpen),
                )
            }
            Spacer(Modifier.height(6.dp))
            Markdown(comment.body, repo.url)
        }
    }
}

// --- Bits -----------------------------------------------------------------------------------------------------

@Composable
private fun SectionTitle(title: String, count: Int?) {
    val p = Theme.palette
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = Type.xs3.copy(fontWeight = FontWeight.SemiBold, letterSpacing = Type.xs3.fontSize * 0.08f), color = p.tertiary)
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), style = Type.xs3, color = p.tertiary)
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    val p = Theme.palette
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 18.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .border(1.dp, p.border, shape),
        content = content,
    )
}

@Composable
private fun DetailSkeleton() {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkeletonBox(Modifier.fillMaxWidth(0.9f).height(22.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.6f).height(22.dp))
        Spacer(Modifier.height(6.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.5f).height(12.dp))
        Spacer(Modifier.height(10.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(110.dp), RoundedCornerShape(20.dp))
        Spacer(Modifier.height(10.dp))
        SkeletonBox(Modifier.fillMaxWidth().height(160.dp), RoundedCornerShape(18.dp))
    }
}
