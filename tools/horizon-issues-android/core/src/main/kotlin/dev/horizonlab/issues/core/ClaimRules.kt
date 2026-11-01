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
        if (issue.activePrs.isNotEmpty()) return null
        return claim.claimedAt + STALE_MILLIS
    }

    /** Whether a comment is a claim command, as the workflow reads the comment's first line. */
    fun isClaimComment(body: String): Boolean = body.trimStart().startsWith("/claim")
}
