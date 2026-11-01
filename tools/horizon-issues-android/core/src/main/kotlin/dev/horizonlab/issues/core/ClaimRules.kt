package dev.horizonlab.issues.core

import java.util.concurrent.TimeUnit

/** The claim rules of CONTRIBUTING.md "Claim before you start", as tools/ci/claim_command.py applies them. */
object ClaimRules {
    const val KIRA_LABEL = "in progress"
    const val STALE_DAYS = 5L
    val STALE_MILLIS: Long = TimeUnit.DAYS.toMillis(STALE_DAYS)

    /** The login the `/claim` workflow assigns with: `github-actions[bot]` in REST, `github-actions` in GraphQL. */
    private val BOT_LOGINS = setOf("github-actions", "github-actions[bot]")

    fun isClaimBot(login: String?): Boolean = login != null && login.lowercase() in BOT_LOGINS

    /**
     * When the daily run releases this claim, or null if it never will: claims made by hand never expire, and an
     * open pull request that links the issue keeps every claim.
     */
    fun releaseAt(claim: Claim, issue: Issue): Long? {
        if (!claim.viaCommand || claim.claimedAt <= 0) return null
        if (issue.closed || issue.activePrs.isNotEmpty()) return null
        return claim.claimedAt + STALE_MILLIS
    }

    /** The workflow's answers start with this; a refusal names the asker: `(asked by @login)`. */
    private const val REFUSED_PREFIX = "🤖 Claims: refused"

    /** Agents start every comment with this (CLAUDE.md), so `🤖 Agent: /claim` is a claim too. */
    private val AGENT_PREFIX = Regex("""^🤖\s*Agent:\s*""")

    private val COMMAND = Regex("""^/(claim|unclaim)\b""", RegexOption.IGNORE_CASE)

    /** `claim` or `unclaim` when the comment's first line is that command, else null. */
    fun command(body: String): String? {
        val line = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        return COMMAND.find(line.replace(AGENT_PREFIX, ""))?.groupValues?.get(1)?.lowercase()
    }

    fun isClaimComment(body: String): Boolean = command(body) == "claim"

    /**
     * What came of each `/claim` and `/unclaim` comment, by comment id, read from the thread itself: a claim the
     * workflow refused, the claim its author still holds, or one that no longer holds (released, unclaimed, or
     * taken by someone else first).
     */
    fun verdicts(comments: List<Comment>, assignees: List<User>): Map<Long, ClaimVerdict> {
        val holders = assignees.map { it.login.lowercase() }.toSet()
        val sorted = comments.sortedBy { it.createdAt }
        val out = HashMap<Long, ClaimVerdict>()
        for ((i, c) in sorted.withIndex()) {
            val verb = command(c.body) ?: continue
            val login = c.author?.login?.lowercase() ?: continue
            if (verb == "unclaim") {
                out[c.id] = ClaimVerdict.UNCLAIM
                continue
            }
            val later = sorted.subList(i + 1, sorted.size)
            // The answer to this comment comes before its author's next command.
            val window = later.takeWhile { command(it.body) == null || it.author?.login?.lowercase() != login }
            val refused = window.any { reply ->
                (reply.author?.isBot == true || isClaimBot(reply.author?.login)) &&
                    reply.body.trimStart().startsWith(REFUSED_PREFIX) &&
                    reply.body.contains("asked by @$login)", ignoreCase = true)
            }
            val latest = later.none { command(it.body) != null && it.author?.login?.lowercase() == login }
            out[c.id] = when {
                refused -> ClaimVerdict.REFUSED
                login in holders && latest -> ClaimVerdict.HOLDS
                else -> ClaimVerdict.ENDED
            }
        }
        return out
    }
}

enum class ClaimVerdict {
    /** Its author holds the issue now. */
    HOLDS,

    /** The workflow refused it (someone held the issue, too many claims...). */
    REFUSED,

    /** It took, but its author no longer holds the issue. */
    ENDED,

    /** An `/unclaim`. */
    UNCLAIM,
}
