package dev.horizonlab.issues.core

import kotlinx.serialization.Serializable

/** A GitHub repository, `owner/name`. */
@Serializable
data class Repo(val owner: String, val name: String) {
    val slug: String get() = "$owner/$name"
    val url: String get() = "https://github.com/$slug"

    companion object {
        val HORIZON = Repo("horizon-rust", "horizon")

        /** Reads `owner/name`, or a github.com URL to the repository or one of its pages. */
        fun parse(text: String): Repo? {
            val cleaned = text.trim().removeSuffix("/").removeSuffix(".git")
                .substringAfter("github.com/")
            val parts = cleaned.split('/').filter { it.isNotBlank() }
            if (parts.size < 2) return null
            val ok = Regex("^[A-Za-z0-9_.-]+$")
            if (!ok.matches(parts[0]) || !ok.matches(parts[1])) return null
            return Repo(parts[0], parts[1])
        }
    }
}

@Serializable
data class User(val login: String, val avatarUrl: String = "") {
    val isBot: Boolean get() = login.endsWith("[bot]") || login == "github-actions"
}

@Serializable
data class Label(val name: String, val color: String = "8b8b8b")

enum class PrState { OPEN, DRAFT, MERGED, CLOSED;

    val active: Boolean get() = this == OPEN || this == DRAFT
}

/** How a pull request points at an issue. */
enum class LinkKind {
    /** `Fixes #N`, `Closes #N`, or linked in the sidebar: merging it closes the issue. */
    CLOSES,

    /** Names the issue (`#N`, its URL, or its number in the branch name) without closing it. */
    MENTIONS,
}

@Serializable
data class LinkedPr(
    val number: Int,
    val title: String,
    val url: String,
    val state: PrState,
    val kind: LinkKind,
    val author: User? = null,
    val updatedAt: Long = 0,
)

/** Which issues a list holds: the open ones, or the ones recently closed as fixed. */
enum class Scope { OPEN, FIXED }

/** The 3 states the list filters on, in the order an issue moves through them. */
enum class IssueStatus {
    /** Nobody holds it and no open pull request links it: free to `/claim`. */
    AVAILABLE,

    /** Assigned (a `/claim`, or by hand) or marked `in progress` by Kira's Discord claim. */
    CLAIMED,

    /** An open or draft pull request links it. */
    HAS_PR,

    /** Closed as completed: fixed, usually by a merged pull request. */
    FIXED,
}

/** One assignee's claim, from the issue's timeline. */
@Serializable
data class Claim(
    val login: String,
    val avatarUrl: String = "",
    /** When this assignee was last assigned; 0 when the timeline did not say. */
    val claimedAt: Long = 0,
    /** Assigned by the `/claim` workflow: only those claims expire (tools/ci/claim_command.py). */
    val viaCommand: Boolean = false,
)

@Serializable
data class Issue(
    val number: Int,
    val title: String,
    val url: String,
    val author: User? = null,
    val labels: List<Label> = emptyList(),
    val assignees: List<User> = emptyList(),
    val comments: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val linkedPrs: List<LinkedPr> = emptyList(),
    /** Known only when the list came from GraphQL (a token is set); the detail screen always fills it. */
    val claims: List<Claim> = emptyList(),
    val body: String? = null,
    /** When it was closed; 0 while it is open. */
    val closedAt: Long = 0,
    /** GitHub's state reason of a closed issue: `completed`, `not_planned`, `duplicate`. */
    val stateReason: String? = null,
    /** The pull request whose merge closed it, when the timeline says. */
    val closedByPr: Int? = null,
) {
    val closed: Boolean get() = closedAt > 0

    /** Kira's Discord `/claim` adds this label instead of an assignee. */
    val claimedOnDiscord: Boolean get() = labels.any { it.name.equals(ClaimRules.KIRA_LABEL, ignoreCase = true) }

    val claimed: Boolean get() = assignees.isNotEmpty() || claimedOnDiscord

    val activePrs: List<LinkedPr> get() = linkedPrs.filter { it.state.active }

    val status: IssueStatus
        get() = when {
            closed -> IssueStatus.FIXED
            activePrs.isNotEmpty() -> IssueStatus.HAS_PR
            claimed -> IssueStatus.CLAIMED
            else -> IssueStatus.AVAILABLE
        }

    /** The merged pull request that fixed a closed issue: the one that closed it, else a merged closing one. */
    val fixingPr: LinkedPr?
        get() = linkedPrs.firstOrNull { it.number == closedByPr }
            ?: linkedPrs.filter { it.state == PrState.MERGED }.minWithOrNull(compareBy<LinkedPr> { it.kind }.thenByDescending { it.updatedAt })

    /** The pull request to show on the row: the fixing one of a closed issue, an open closing one, any open one. */
    val primaryPr: LinkedPr?
        get() = (if (closed) fixingPr else null) ?: linkedPrs.sortedWith(
            compareByDescending<LinkedPr> { it.state.active }
                .thenBy { it.kind }
                .thenByDescending { it.state == PrState.MERGED }
                .thenByDescending { it.updatedAt },
        ).firstOrNull()
}

@Serializable
data class Comment(
    val id: Long,
    val author: User?,
    val body: String,
    val createdAt: Long,
    val url: String = "",
)

data class IssueDetail(
    val issue: Issue,
    val comments: List<Comment>,
)

data class RateLimit(val remaining: Int, val limit: Int, val resetAt: Long)

/** An error to show as it is: rate limits, a bad token, no network. */
class GitHubException(message: String, val code: Int = 0, val rateLimited: Boolean = false) : Exception(message)
