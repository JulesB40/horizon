package dev.horizonlab.issues.core

/**
 * Finds the issues a pull request points at from its own text, for when GitHub's link data is out of reach
 * (no token: GraphQL needs one). GitHub links an issue to a pull request through closing keywords in the
 * description; the claim workflow also counts plain mentions.
 */
object Linker {
    private const val KEYWORDS = "close|closes|closed|fix|fixes|fixed|resolve|resolves|resolved"

    /** `Fixes #12`, `fixes: #12`, `Closes owner/repo#12`, `Resolves https://github.com/owner/repo/issues/12`. */
    private fun closingPattern(repo: Repo): Regex {
        val slug = Regex.escape(repo.slug)
        return Regex(
            """(?i)\b(?:$KEYWORDS)\b:?\s+(?:(?:https?://github\.com/$slug/issues/)|(?:$slug)?#)(\d+)\b""",
        )
    }

    /** A bare `#12`, `owner/repo#12` or issue URL anywhere (not inside a word such as `abc#12`). */
    private fun mentionPattern(repo: Repo): Regex {
        val slug = Regex.escape(repo.slug)
        return Regex("""(?i)(?:https?://github\.com/$slug/issues/|(?<![\w/#])(?:$slug)?#)(\d+)\b""")
    }

    /** An issue number of 3+ digits as its own segment of a branch name: `fix/5835-menu-stutter`, `issue-812`. */
    private val BRANCH_NUMBER = Regex("""(?:^|[/_-])(?:issue-?|gh-?|i)?(\d{3,7})(?=$|[/_-])""", RegexOption.IGNORE_CASE)

    /** Issue numbers this pull request's title, description and branch reference, with the strongest link kind. */
    fun references(repo: Repo, title: String, body: String?, branch: String?): Map<Int, LinkKind> {
        val text = stripNoise(title + "\n" + (body ?: ""))
        val out = LinkedHashMap<Int, LinkKind>()
        closingPattern(repo).findAll(text).forEach { m -> m.groupValues[1].toIntOrNull()?.let { out[it] = LinkKind.CLOSES } }
        mentionPattern(repo).findAll(text).forEach { m ->
            m.groupValues[1].toIntOrNull()?.let { out.putIfAbsent(it, LinkKind.MENTIONS) }
        }
        branch?.let { b ->
            BRANCH_NUMBER.findAll(b).forEach { m -> m.groupValues[1].toIntOrNull()?.let { out.putIfAbsent(it, LinkKind.MENTIONS) } }
        }
        return out
    }

    /** Drops HTML comments (pull request templates) and fenced code, where `#N` rarely means an issue. */
    private fun stripNoise(text: String): String =
        text.replace(Regex("(?s)<!--.*?-->"), " ")
            .replace(Regex("(?s)```.*?```"), " ")

    /** A pull request as the linker sees it. */
    data class Pr(
        val number: Int,
        val title: String,
        val body: String?,
        val branch: String?,
        val url: String,
        val state: PrState,
        val author: User?,
        val updatedAt: Long,
    )

    /** Each issue number's linked pull requests, from the pull requests' own text. */
    fun link(repo: Repo, prs: List<Pr>): Map<Int, List<LinkedPr>> {
        val out = HashMap<Int, MutableList<LinkedPr>>()
        for (pr in prs) {
            for ((issue, kind) in references(repo, pr.title, pr.body, pr.branch)) {
                if (issue == pr.number) continue
                out.getOrPut(issue) { mutableListOf() } +=
                    LinkedPr(pr.number, pr.title, pr.url, pr.state, kind, pr.author, pr.updatedAt)
            }
        }
        return out
    }

    /** Merges two views of an issue's pull requests, one entry per number, keeping the strongest link. */
    fun merge(a: List<LinkedPr>, b: List<LinkedPr>): List<LinkedPr> {
        val byNumber = LinkedHashMap<Int, LinkedPr>()
        for (pr in a + b) {
            val seen = byNumber[pr.number]
            byNumber[pr.number] = when {
                seen == null -> pr
                pr.kind < seen.kind -> pr.copy(author = pr.author ?: seen.author)
                else -> seen.copy(author = seen.author ?: pr.author)
            }
        }
        return byNumber.values.toList()
    }
}
