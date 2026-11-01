package dev.horizonlab.issues.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Reads a repository's open issues with who claimed them and the pull requests that link them.
 *
 * Without a token it uses the REST API (60 requests an hour per IP; the HTTP cache makes unchanged pages free
 * 304s) and finds linked pull requests from their text with [Linker]. With a token it asks GraphQL, which knows
 * the sidebar links and the timeline, the same data tools/ci/claim_command.py reads.
 */
class GitHubClient(
    private val http: OkHttpClient,
    private val token: String?,
    private val apiBase: String = "https://api.github.com",
) {
    /** The rate limit GitHub reported on the last response. */
    @Volatile
    var rateLimit: RateLimit? = null
        private set

    val authenticated: Boolean get() = !token.isNullOrBlank()

    // --- The list ---------------------------------------------------------------------------------------------

    /** Every open issue, newest first. `fresh` revalidates cached pages instead of trusting them for a minute. */
    suspend fun openIssues(repo: Repo, fresh: Boolean = false): List<Issue> = issues(repo, Scope.OPEN, fresh)

    /** The open issues, newest first, or the fixed ones of the last week (see [fixedIssues]). */
    suspend fun issues(repo: Repo, scope: Scope, fresh: Boolean = false): List<Issue> = when (scope) {
        Scope.OPEN -> withContext(Dispatchers.IO) {
            if (authenticated) issuesGraphQl(repo, MAX_ISSUE_PAGES) else openIssuesRest(repo, fresh)
        }
        Scope.FIXED -> fixedIssues(repo, FixedWindow.WEEK, fresh).issues
    }

    /**
     * The issues closed as completed in `window`, most recently closed first, through GitHub's search (it alone
     * filters by close reason and date, and leaves pull requests out), with each window's exact count.
     */
    suspend fun fixedIssues(
        repo: Repo,
        window: FixedWindow,
        fresh: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): FixedIssues = withContext(Dispatchers.IO) {
        val query = { w: FixedWindow -> fixedQuery(repo, w, now) }
        val (issues, total) = if (authenticated) fixedGraphQl(query(window)) else fixedRest(repo, query(window), fresh)
        val sorted = issues.filter { it.closed }.distinctBy { it.number }.sortedByDescending { it.closedAt }
        val capped = sorted.size < total
        val counts = HashMap<FixedWindow, Int>()
        counts[window] = total
        val others = FixedWindow.entries.filter { it != window }
        // A shorter window's count comes from the list itself when it is complete; the rest ask GitHub.
        val asked = others.filter { capped || it.days > window.days }
        for (w in others - asked.toSet()) counts[w] = sorted.count { now - it.closedAt <= w.days * DAY_MILLIS }
        if (asked.isNotEmpty()) {
            val found = if (authenticated) countGraphQl(asked.associateWith(query)) else asked.associateWith { searchCount(query(it), fresh) }
            counts.putAll(found)
        }
        FixedIssues(window, sorted, counts)
    }

    private fun fixedQuery(repo: Repo, window: FixedWindow, now: Long): String {
        val since = Instant.ofEpochSecond((now - window.days * DAY_MILLIS) / 1000).toString()
        return "repo:${repo.slug} is:issue is:closed reason:completed closed:>=$since"
    }

    private fun searchPath(query: String, perPage: Int, page: Int) =
        "/search/issues?q=" + java.net.URLEncoder.encode(query, "UTF-8") + "&sort=updated&order=desc&per_page=$perPage&page=$page"

    private fun searchCount(query: String, fresh: Boolean): Int = getJson<RestSearch>(searchPath(query, 1, 1), fresh).totalCount

    private fun fixedRest(repo: Repo, query: String, fresh: Boolean): Pair<List<Issue>, Int> {
        val raw = ArrayList<RestIssue>()
        var total = 0
        for (page in 1..MAX_FIXED_PAGES) {
            val result = getJson<RestSearch>(searchPath(query, PAGE, page), fresh)
            total = result.totalCount
            raw += result.items.filter { it.pullRequest == null }
            if (result.items.size < PAGE) break
        }
        // Without a token, the fixing pull request comes from the text of the recently merged ones.
        val merged = pages<RestPull>("/repos/${repo.slug}/pulls?state=closed&sort=updated&direction=desc", MAX_MERGED_PAGES, fresh)
            .filter { it.mergedAt != null }
        val links = Linker.link(repo, merged.map { it.toLinkerPr() })
        return raw.map { it.toIssue(links[it.number].orEmpty()) } to total
    }

    private fun fixedGraphQl(query: String): Pair<List<Issue>, Int> {
        val out = ArrayList<Issue>()
        var cursor: String? = null
        var total = 0
        for (page in 1..MAX_FIXED_PAGES) {
            val variables = buildJsonObject {
                put("q", query)
                if (cursor != null) put("cursor", cursor)
            }
            val result = graphQl<GqlSearchData>(SEARCH_QUERY, variables).results
            total = result.issueCount
            result.nodes.filterNotNull().filter { it.number > 0 }.mapTo(out) { it.toIssue() }
            if (!result.pageInfo.hasNextPage) break
            cursor = result.pageInfo.endCursor
        }
        return out to total
    }

    private fun countGraphQl(queries: Map<FixedWindow, String>): Map<FixedWindow, Int> {
        val variables = buildJsonObject { queries.forEach { (w, q) -> put(w.name.lowercase(), q) } }
        val params = queries.keys.joinToString(", ") { "${'$'}${it.name.lowercase()}: String!" }
        val fields = queries.keys.joinToString("\n") { w ->
            val n = w.name.lowercase()
            "$n: search(query: ${'$'}$n, type: ISSUE, first: 1) { issueCount }"
        }
        val data = graphQl<Map<String, GqlCountOnly>>("query($params) {\n$fields\n}", variables)
        return queries.keys.associateWith { data[it.name.lowercase()]?.issueCount ?: 0 }
    }

    private fun openIssuesRest(repo: Repo, fresh: Boolean): List<Issue> {
        val raw = pages<RestIssue>("/repos/${repo.slug}/issues?state=open&sort=created&direction=desc", MAX_ISSUE_PAGES, fresh)
            .filter { it.pullRequest == null }
        val open = pages<RestPull>("/repos/${repo.slug}/pulls?state=open&sort=updated&direction=desc", MAX_PR_PAGES, fresh)
        // Recently closed pull requests too, so an open issue shows the one that merged without closing it.
        val closed = pages<RestPull>("/repos/${repo.slug}/pulls?state=closed&sort=updated&direction=desc", 1, fresh)
        val links = Linker.link(repo, (open + closed).distinctBy { it.number }.map { it.toLinkerPr() })
        return raw.map { it.toIssue(links[it.number].orEmpty()) }
    }

    private fun issuesGraphQl(repo: Repo, maxPages: Int): List<Issue> {
        val out = ArrayList<Issue>()
        var cursor: String? = null
        repeat(maxPages) {
            val variables = buildJsonObject {
                put("owner", repo.owner)
                put("name", repo.name)
                if (cursor != null) put("cursor", cursor)
            }
            val data = graphQl<GqlData>(ISSUES_QUERY, variables)
            val issues = data.repository?.issues ?: throw GitHubException("Repository ${repo.slug} not found", 404)
            issues.nodes.filterNotNull().mapTo(out) { it.toIssue() }
            if (!issues.pageInfo.hasNextPage) return out
            cursor = issues.pageInfo.endCursor
        }
        return out
    }

    // --- One issue --------------------------------------------------------------------------------------------

    /**
     * The issue with its comments, claims and linked pull requests from its timeline. `known` is the list's copy,
     * whose links (GraphQL's sidebar links, or the text of open pull requests) are merged in.
     */
    suspend fun issueDetail(repo: Repo, number: Int, known: Issue?): IssueDetail = withContext(Dispatchers.IO) {
        val issue = getJson<RestIssue>("/repos/${repo.slug}/issues/$number", fresh = true)
        val comments = pages<RestComment>("/repos/${repo.slug}/issues/$number/comments?", 3, fresh = true)
        val timeline = pages<RestEvent>("/repos/${repo.slug}/issues/$number/timeline?", 3, fresh = true)

        val fromTimeline = timeline.mapNotNull { it.crossReferencedPr(repo) }
        val linked = Linker.merge(known?.linkedPrs.orEmpty(), fromTimeline)
            .sortedWith(compareByDescending<LinkedPr> { it.state.active }.thenByDescending { it.updatedAt })
        val base = issue.toIssue(linked)
        IssueDetail(
            issue = base.copy(claims = claimsFrom(base.assignees, timeline), closedByPr = known?.closedByPr),
            comments = comments.map { it.toComment() },
        )
    }

    private fun claimsFrom(assignees: List<User>, timeline: List<RestEvent>): List<Claim> {
        val last = HashMap<String, RestEvent>()
        for (e in timeline) {
            val login = e.assignee?.login?.lowercase() ?: continue
            if (e.event == "assigned") last[login] = e
        }
        return assignees.map { user ->
            val e = last[user.login.lowercase()]
            Claim(
                login = user.login,
                avatarUrl = user.avatarUrl,
                claimedAt = e?.createdAt?.let(::millis) ?: 0,
                viaCommand = ClaimRules.isClaimBot(e?.actor?.login),
            )
        }
    }

    /** Comments `body` on the issue as the token's owner (`/claim` takes it). */
    suspend fun comment(repo: Repo, number: Int, body: String): Comment = withContext(Dispatchers.IO) {
        if (!authenticated) throw GitHubException("Add a GitHub token in Settings to comment")
        val payload = JSON.encodeToString(mapOf("body" to body)).toRequestBody(JSON_TYPE)
        val request = request("/repos/${repo.slug}/issues/$number/comments").post(payload).build()
        http.newCall(request).execute().use { response ->
            check(response)
            JSON.decodeFromString<RestComment>(response.body!!.string()).toComment()
        }
    }

    // --- HTTP -------------------------------------------------------------------------------------------------

    private fun request(path: String): Request.Builder {
        val builder = Request.Builder()
            .url(if (path.startsWith("http")) path else apiBase + path)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        if (authenticated) builder.header("Authorization", "Bearer ${token!!.trim()}")
        return builder
    }

    private inline fun <reified T> getJson(path: String, fresh: Boolean): T {
        val builder = request(path)
        if (fresh) builder.cacheControl(CacheControl.Builder().maxAge(0, TimeUnit.SECONDS).build())
        http.newCall(builder.build()).execute().use { response ->
            check(response)
            return JSON.decodeFromString(response.body!!.string())
        }
    }

    /** Follows `page=` until a short page, at most `maxPages` of 100. */
    private inline fun <reified T> pages(path: String, maxPages: Int, fresh: Boolean): List<T> {
        val out = ArrayList<T>()
        val sep = if (path.endsWith("?")) "" else "&"
        for (page in 1..maxPages) {
            val items = getJson<List<T>>("$path${sep}per_page=$PAGE&page=$page", fresh)
            out += items
            if (items.size < PAGE) break
        }
        return out
    }

    private inline fun <reified T> graphQl(query: String, variables: JsonObject): T {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }
        val request = request("/graphql").post(payload.toString().toRequestBody(JSON_TYPE)).build()
        http.newCall(request).execute().use { response ->
            check(response)
            val parsed = JSON.decodeFromString<GqlResponse<T>>(response.body!!.string())
            parsed.errors?.firstOrNull()?.let { throw GitHubException(it.message) }
            return parsed.data ?: throw GitHubException("GitHub returned no data")
        }
    }

    private fun check(response: Response) {
        val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull()
        val limit = response.header("X-RateLimit-Limit")?.toIntOrNull()
        val reset = response.header("X-RateLimit-Reset")?.toLongOrNull()
        if (remaining != null && limit != null && reset != null) rateLimit = RateLimit(remaining, limit, reset * 1000)
        if (response.isSuccessful) return
        val code = response.code
        val apiMessage = runCatching { JSON.decodeFromString<RestError>(response.body!!.string()).message }.getOrNull()
        throw when {
            code == 401 -> GitHubException("GitHub rejected the token. Check it in Settings.", code)
            (code == 403 || code == 429) && (remaining == 0 || apiMessage?.contains("rate limit", true) == true) -> {
                val at = reset?.let { " until " + Instant.ofEpochSecond(it).toString().substring(11, 16) + " UTC" } ?: ""
                val hint = if (authenticated) "" else " Add a token in Settings for 5,000 requests an hour."
                GitHubException("GitHub's rate limit is used up$at.$hint", code, rateLimited = true)
            }
            code == 404 -> GitHubException("Not found on GitHub. Check the repository in Settings.", code)
            else -> GitHubException(apiMessage ?: "GitHub answered HTTP $code", code)
        }
    }

    companion object {
        const val PAGE = 100
        const val MAX_ISSUE_PAGES = 10
        const val MAX_PR_PAGES = 5
        /** GitHub's search returns at most 1,000 results. */
        const val MAX_FIXED_PAGES = 10
        const val MAX_MERGED_PAGES = 3
        private const val DAY_MILLIS = 86_400_000L

        val JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        /** An HTTP client with a disk cache, so GitHub answers unchanged pages with a 304 that costs nothing. */
        fun httpClient(cacheDir: File?): OkHttpClient = OkHttpClient.Builder()
            .apply { if (cacheDir != null) cache(Cache(cacheDir, 20L * 1024 * 1024)) }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        fun millis(iso: String): Long = runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0)

        /** A friendly message for anything a call throws. */
        fun describe(error: Throwable): String = when (error) {
            is GitHubException -> error.message ?: "GitHub error"
            is IOException -> "Can't reach GitHub. Check your connection."
            else -> error.message ?: error.javaClass.simpleName
        }

        private val ISSUES_QUERY = """
            query(${'$'}owner: String!, ${'$'}name: String!, ${'$'}cursor: String) {
              repository(owner: ${'$'}owner, name: ${'$'}name) {
                issues(first: 100, states: OPEN, after: ${'$'}cursor, orderBy: {field: CREATED_AT, direction: DESC}) {
                  pageInfo { hasNextPage endCursor }
                  nodes { ...issue }
                }
              }
            }
        """.trimIndent() + ISSUE_FRAGMENTS

        private val SEARCH_QUERY = """
            query(${'$'}q: String!, ${'$'}cursor: String) {
              results: search(query: ${'$'}q, type: ISSUE, first: 100, after: ${'$'}cursor) {
                issueCount
                pageInfo { hasNextPage endCursor }
                nodes { ...issue }
              }
            }
        """.trimIndent() + ISSUE_FRAGMENTS

        // A getter, so the queries above can use it while the companion initialises.
        private val ISSUE_FRAGMENTS: String get() = "\n" + """
            fragment issue on Issue {
                    number title url createdAt updatedAt closedAt stateReason
                    author { login avatarUrl }
                    comments { totalCount }
                    labels(first: 20) { nodes { name color } }
                    assignees(first: 10) { nodes { login avatarUrl } }
                    closedByPullRequestsReferences(first: 10, includeClosedPrs: true) { nodes { ...pr } }
                    timelineItems(last: 50, itemTypes: [ASSIGNED_EVENT, CROSS_REFERENCED_EVENT, CLOSED_EVENT]) {
                      nodes {
                        __typename
                        ... on AssignedEvent { createdAt actor { login } assignee { ... on User { login } } }
                        ... on CrossReferencedEvent { source { __typename ... on PullRequest { ...pr } } }
                        ... on ClosedEvent { createdAt closer { __typename ... on PullRequest { ...pr } } }
                      }
                    }
            }
            fragment pr on PullRequest {
              number title url state isDraft updatedAt
              author { login avatarUrl }
              repository { nameWithOwner }
            }
        """.trimIndent()
    }
}

// --- REST shapes ----------------------------------------------------------------------------------------------

@Serializable
internal data class RestUser(val login: String, @SerialName("avatar_url") val avatarUrl: String = "") {
    fun toUser() = User(login, avatarUrl)
}

@Serializable
internal data class RestLabel(val name: String, val color: String = "8b8b8b")

@Serializable
internal data class RestPrRef(@SerialName("merged_at") val mergedAt: String? = null)

@Serializable
internal data class RestIssue(
    val number: Int,
    val title: String,
    @SerialName("html_url") val htmlUrl: String,
    val user: RestUser? = null,
    val labels: List<RestLabel> = emptyList(),
    val assignees: List<RestUser> = emptyList(),
    val comments: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val body: String? = null,
    val state: String = "open",
    val draft: Boolean = false,
    @SerialName("pull_request") val pullRequest: RestPrRef? = null,
    @SerialName("repository_url") val repositoryUrl: String = "",
    @SerialName("closed_at") val closedAt: String? = null,
    @SerialName("state_reason") val stateReason: String? = null,
) {
    fun toIssue(links: List<LinkedPr>) = Issue(
        number = number,
        title = title,
        url = htmlUrl,
        author = user?.toUser(),
        labels = labels.map { Label(it.name, it.color) },
        assignees = assignees.map { it.toUser() },
        comments = comments,
        createdAt = GitHubClient.millis(createdAt),
        updatedAt = GitHubClient.millis(updatedAt),
        linkedPrs = links,
        body = body,
        closedAt = if (state == "closed") closedAt?.let(GitHubClient::millis) ?: 0 else 0,
        stateReason = stateReason.takeIf { state == "closed" },
    )

    /** The state of the pull request this issue object stands for. */
    fun prState(): PrState = when {
        pullRequest?.mergedAt != null -> PrState.MERGED
        state == "closed" -> PrState.CLOSED
        draft -> PrState.DRAFT
        else -> PrState.OPEN
    }
}

@Serializable
internal data class RestRef(val ref: String = "")

@Serializable
internal data class RestPull(
    val number: Int,
    val title: String,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val state: String = "open",
    val draft: Boolean = false,
    @SerialName("merged_at") val mergedAt: String? = null,
    val user: RestUser? = null,
    @SerialName("updated_at") val updatedAt: String = "",
    val head: RestRef? = null,
) {
    fun toLinkerPr() = Linker.Pr(
        number = number,
        title = title,
        body = body,
        branch = head?.ref,
        url = htmlUrl,
        state = when {
            mergedAt != null -> PrState.MERGED
            state == "closed" -> PrState.CLOSED
            draft -> PrState.DRAFT
            else -> PrState.OPEN
        },
        author = user?.toUser(),
        updatedAt = GitHubClient.millis(updatedAt),
    )
}

@Serializable
internal data class RestComment(
    val id: Long,
    val user: RestUser? = null,
    val body: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
) {
    fun toComment() = Comment(id, user?.toUser(), body, GitHubClient.millis(createdAt), htmlUrl)
}

@Serializable
internal data class RestSource(val type: String = "", val issue: RestIssue? = null)

@Serializable
internal data class RestEvent(
    val event: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val actor: RestUser? = null,
    val assignee: RestUser? = null,
    val source: RestSource? = null,
) {
    /** A pull request of this repository that mentioned the issue. */
    fun crossReferencedPr(repo: Repo): LinkedPr? {
        if (event != "cross-referenced") return null
        val pr = source?.issue ?: return null
        if (pr.pullRequest == null) return null
        if (!pr.repositoryUrl.endsWith("/repos/${repo.slug}", ignoreCase = true)) return null
        return LinkedPr(
            number = pr.number,
            title = pr.title,
            url = pr.htmlUrl,
            state = pr.prState(),
            kind = LinkKind.MENTIONS,
            author = pr.user?.toUser(),
            updatedAt = GitHubClient.millis(pr.updatedAt),
        )
    }
}

@Serializable
internal data class RestError(val message: String = "")

@Serializable
internal data class RestSearch(
    @SerialName("total_count") val totalCount: Int = 0,
    val items: List<RestIssue> = emptyList(),
)

@Serializable
internal data class GqlSearch(
    val issueCount: Int = 0,
    val pageInfo: GqlPageInfo = GqlPageInfo(),
    val nodes: List<GqlIssue?> = emptyList(),
)

@Serializable
internal data class GqlSearchData(val results: GqlSearch = GqlSearch())

@Serializable
internal data class GqlCountOnly(val issueCount: Int = 0)

// --- GraphQL shapes -------------------------------------------------------------------------------------------

@Serializable
internal data class GqlResponse<T>(val data: T? = null, val errors: List<GqlError>? = null)

@Serializable
internal data class GqlError(val message: String = "GraphQL error")

@Serializable
internal data class GqlData(val repository: GqlRepository? = null)

@Serializable
internal data class GqlRepository(val issues: GqlIssues? = null)

@Serializable
internal data class GqlPageInfo(val hasNextPage: Boolean = false, val endCursor: String? = null)

@Serializable
internal data class GqlIssues(val pageInfo: GqlPageInfo = GqlPageInfo(), val nodes: List<GqlIssue?> = emptyList())

@Serializable
internal data class GqlActor(val login: String? = null, val avatarUrl: String = "") {
    fun toUser(): User? = login?.let { User(it, avatarUrl) }
}

@Serializable
internal data class GqlCount(val totalCount: Int = 0)

@Serializable
internal data class GqlNodes<T>(val nodes: List<T?> = emptyList())

@Serializable
internal data class GqlLabel(val name: String, val color: String = "8b8b8b")

@Serializable
internal data class GqlRepoName(val nameWithOwner: String = "")

@Serializable
internal data class GqlPr(
    @SerialName("__typename") val typename: String = "PullRequest",
    val number: Int = 0,
    val title: String = "",
    val url: String = "",
    val state: String = "OPEN",
    val isDraft: Boolean = false,
    val updatedAt: String = "",
    val author: GqlActor? = null,
    val repository: GqlRepoName? = null,
) {
    val isPr: Boolean get() = typename == "PullRequest" && number > 0

    fun toLinked(kind: LinkKind) = LinkedPr(
        number = number,
        title = title,
        url = url,
        state = when {
            state == "MERGED" -> PrState.MERGED
            state == "CLOSED" -> PrState.CLOSED
            isDraft -> PrState.DRAFT
            else -> PrState.OPEN
        },
        kind = kind,
        author = author?.toUser(),
        updatedAt = GitHubClient.millis(updatedAt),
    )
}

@Serializable
internal data class GqlEvent(
    @SerialName("__typename") val typename: String = "",
    val createdAt: String? = null,
    val actor: GqlActor? = null,
    val assignee: GqlActor? = null,
    val source: GqlPr? = null,
    val closer: GqlPr? = null,
)

@Serializable
internal data class GqlIssue(
    val number: Int = 0,
    val title: String = "",
    val url: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val closedAt: String? = null,
    val stateReason: String? = null,
    val author: GqlActor? = null,
    val comments: GqlCount = GqlCount(),
    val labels: GqlNodes<GqlLabel> = GqlNodes(),
    val assignees: GqlNodes<GqlActor> = GqlNodes(),
    val closedByPullRequestsReferences: GqlNodes<GqlPr> = GqlNodes(),
    val timelineItems: GqlNodes<GqlEvent> = GqlNodes(),
) {
    fun toIssue(): Issue {
        val repoName = url.substringAfter("github.com/").substringBefore("/issues/")
        val closing = closedByPullRequestsReferences.nodes.filterNotNull().filter { it.isPr }
            .map { it.toLinked(LinkKind.CLOSES) }
        val events = timelineItems.nodes.filterNotNull()
        val mentions = events.filter { it.typename == "CrossReferencedEvent" }
            .mapNotNull { it.source }
            .filter { it.isPr && it.repository?.nameWithOwner.equals(repoName, ignoreCase = true) }
            .map { it.toLinked(LinkKind.MENTIONS) }
        // The pull request whose merge closed it: the newest close, in case it was reopened and closed again.
        val closer = if (closedAt == null) null else events.lastOrNull { it.typename == "ClosedEvent" }?.closer?.takeIf { it.isPr }
        val users = assignees.nodes.filterNotNull().mapNotNull { it.toUser() }
        val lastAssigned = HashMap<String, GqlEvent>()
        for (e in events) {
            val login = e.assignee?.login?.lowercase() ?: continue
            if (e.typename == "AssignedEvent") lastAssigned[login] = e
        }
        val claims = users.map { u ->
            val e = lastAssigned[u.login.lowercase()]
            Claim(u.login, u.avatarUrl, e?.createdAt?.let(GitHubClient::millis) ?: 0, ClaimRules.isClaimBot(e?.actor?.login))
        }
        return Issue(
            number = number,
            title = title,
            url = url,
            author = author?.toUser(),
            labels = labels.nodes.filterNotNull().map { Label(it.name, it.color) },
            assignees = users,
            comments = comments.totalCount,
            createdAt = GitHubClient.millis(createdAt),
            updatedAt = GitHubClient.millis(updatedAt),
            linkedPrs = Linker.merge(listOfNotNull(closer?.toLinked(LinkKind.CLOSES)) + closing, mentions),
            claims = claims,
            closedAt = closedAt?.let(GitHubClient::millis) ?: 0,
            stateReason = stateReason?.lowercase(),
            closedByPr = closer?.number,
        )
    }
}
