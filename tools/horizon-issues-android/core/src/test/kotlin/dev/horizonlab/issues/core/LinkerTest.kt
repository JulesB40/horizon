package dev.horizonlab.issues.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkerTest {
    private val repo = Repo.HORIZON

    private fun refs(title: String, body: String? = null, branch: String? = null) =
        Linker.references(repo, title, body, branch)

    @Test
    fun closingKeywords() {
        assertEquals(mapOf(5835 to LinkKind.CLOSES), refs("Smooth sliders", "Fixes #5835"))
        assertEquals(mapOf(12 to LinkKind.CLOSES), refs("x", "closes: #12"))
        assertEquals(mapOf(7 to LinkKind.CLOSES), refs("x", "Resolves horizon-rust/horizon#7"))
        assertEquals(mapOf(9 to LinkKind.CLOSES), refs("x", "Fixed https://github.com/horizon-rust/horizon/issues/9"))
    }

    @Test
    fun mentionsAreWeaker() {
        assertEquals(mapOf(1514 to LinkKind.MENTIONS), refs("Port the server thread (#1514)"))
        assertEquals(mapOf(3 to LinkKind.CLOSES, 4 to LinkKind.MENTIONS), refs("x", "Fixes #3, see #4"))
        // The closing keyword wins over an earlier bare mention of the same issue.
        assertEquals(mapOf(5 to LinkKind.CLOSES), refs("About #5", "Fixes #5"))
    }

    @Test
    fun ignoresNoise() {
        assertEquals(emptyMap(), refs("x", "<!-- Fixes #123 -->\n```\ncolor #333\n```"))
        assertEquals(emptyMap(), refs("x", "abc#12 and other/repo#13 and a/b#14"))
        assertEquals(emptyMap(), refs("x", "prefix fixes nothing"))
    }

    @Test
    fun branchNumbers() {
        assertEquals(mapOf(5835 to LinkKind.MENTIONS), refs("x", null, "fix/5835-menu-stutter"))
        assertEquals(mapOf(812 to LinkKind.MENTIONS), refs("x", null, "issue-812"))
        assertEquals(emptyMap(), refs("x", null, "bump-wgpu-25-0"))
        assertEquals(emptyMap(), refs("x", null, "perf/dedicated-no-sprite-pixels"))
    }

    @Test
    fun linkAndMerge() {
        val prs = listOf(
            Linker.Pr(10, "Fix it", "Fixes #1", "fix/1", "u10", PrState.OPEN, null, 1),
            Linker.Pr(11, "Related", "See #1 and #2", "x", "u11", PrState.DRAFT, null, 2),
        )
        val links = Linker.link(repo, prs)
        assertEquals(listOf(10 to LinkKind.CLOSES, 11 to LinkKind.MENTIONS), links[1]!!.map { it.number to it.kind })
        assertEquals(listOf(11), links[2]!!.map { it.number })

        val mention = LinkedPr(10, "Fix it", "u10", PrState.OPEN, LinkKind.MENTIONS, User("a"))
        val closing = LinkedPr(10, "Fix it", "u10", PrState.OPEN, LinkKind.CLOSES)
        val merged = Linker.merge(listOf(mention), listOf(closing)).single()
        assertEquals(LinkKind.CLOSES, merged.kind)
        assertEquals("a", merged.author?.login)
    }

    @Test
    fun statusAndRelease() {
        val base = Issue(1, "t", "u")
        assertEquals(IssueStatus.AVAILABLE, base.status)
        val claimed = base.copy(assignees = listOf(User("me")))
        assertEquals(IssueStatus.CLAIMED, claimed.status)
        assertEquals(IssueStatus.CLAIMED, base.copy(labels = listOf(Label("In progress"))).status)
        val pr = LinkedPr(2, "p", "u", PrState.DRAFT, LinkKind.CLOSES)
        assertEquals(IssueStatus.HAS_PR, claimed.copy(linkedPrs = listOf(pr)).status)
        assertEquals(IssueStatus.CLAIMED, claimed.copy(linkedPrs = listOf(pr.copy(state = PrState.MERGED))).status)

        val claim = Claim("me", claimedAt = 1_000, viaCommand = true)
        assertEquals(1_000 + ClaimRules.STALE_MILLIS, ClaimRules.releaseAt(claim, claimed))
        assertNull(ClaimRules.releaseAt(claim.copy(viaCommand = false), claimed))
        assertNull(ClaimRules.releaseAt(claim, claimed.copy(linkedPrs = listOf(pr))))
    }

    @Test
    fun parsesRepos() {
        assertEquals(Repo.HORIZON, Repo.parse("horizon-rust/horizon"))
        assertEquals(Repo.HORIZON, Repo.parse("https://github.com/horizon-rust/horizon/issues/5835"))
        assertNull(Repo.parse("horizon"))
    }
}
