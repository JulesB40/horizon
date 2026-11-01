package dev.horizonlab.issues.core

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitHubClientTest {
    private val server = MockWebServer()
    private val routes = HashMap<String, String>()

    @BeforeTest
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val key = request.method + " " + request.path!!.substringBefore("?")
                val body = routes[key] ?: return MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}""")
                return MockResponse().setBody(body)
                    .addHeader("X-RateLimit-Remaining", "57")
                    .addHeader("X-RateLimit-Limit", "60")
                    .addHeader("X-RateLimit-Reset", "1800000000")
            }
        }
        server.start()
    }

    @AfterTest
    fun stop() = server.shutdown()

    private fun client(token: String? = null) =
        GitHubClient(OkHttpClient(), token, server.url("").toString().removeSuffix("/"))

    private fun user(login: String) = """{"login":"$login","avatar_url":"https://a/$login"}"""

    @Test
    fun restListLinksPullRequestsFromTheirText() = runTest {
        routes["GET /repos/horizon-rust/horizon/issues"] = """[
          {"number":5835,"title":"Random Stutters in Main Menu","html_url":"https://github.com/horizon-rust/horizon/issues/5835",
           "user":${user("kira-horizon")},"labels":[{"name":"bug","color":"d73a4a"}],"assignees":[],"comments":1,
           "created_at":"2026-11-01T09:00:00Z","updated_at":"2026-11-01T10:00:00Z","body":"It stutters"},
          {"number":5830,"title":"Claimed one","html_url":"u","assignees":[${user("dev")}],
           "created_at":"2026-10-30T09:00:00Z","updated_at":"2026-10-30T09:00:00Z"},
          {"number":5829,"title":"A pull request","html_url":"u","pull_request":{"merged_at":null}}
        ]"""
        routes["GET /repos/horizon-rust/horizon/pulls"] = """[
          {"number":5840,"title":"Debounce video sliders","body":"Fixes #5835","html_url":"p","state":"open","draft":true,
           "user":${user("dev")},"updated_at":"2026-11-01T11:00:00Z","head":{"ref":"fix/sliders"}}
        ]"""
        val issues = client().openIssues(Repo.HORIZON)
        assertEquals(listOf(5835, 5830), issues.map { it.number })
        val stutter = issues[0]
        assertEquals(IssueStatus.HAS_PR, stutter.status)
        assertEquals(PrState.DRAFT, stutter.primaryPr!!.state)
        assertEquals(LinkKind.CLOSES, stutter.primaryPr!!.kind)
        assertEquals(IssueStatus.CLAIMED, issues[1].status)
        assertEquals(57, client().also { it.openIssues(Repo.HORIZON) }.rateLimit!!.remaining)
    }

    @Test
    fun graphQlListReadsSidebarLinksAndClaims() = runTest {
        routes["POST /graphql"] = """{"data":{"repository":{"issues":{
          "pageInfo":{"hasNextPage":false,"endCursor":null},
          "nodes":[{"number":7,"title":"T","url":"https://github.com/horizon-rust/horizon/issues/7",
            "createdAt":"2026-10-20T00:00:00Z","updatedAt":"2026-10-21T00:00:00Z",
            "author":{"login":"a","avatarUrl":""},"comments":{"totalCount":3},
            "labels":{"nodes":[{"name":"bug","color":"d73a4a"}]},
            "assignees":{"nodes":[{"login":"dev","avatarUrl":"x"}]},
            "closedByPullRequestsReferences":{"nodes":[]},
            "timelineItems":{"nodes":[
              {"__typename":"AssignedEvent","createdAt":"2026-10-22T00:00:00Z","actor":{"login":"github-actions"},"assignee":{"login":"dev"}},
              {"__typename":"CrossReferencedEvent","source":{"__typename":"PullRequest","number":9,"title":"P",
                "url":"p","state":"MERGED","isDraft":false,"updatedAt":"2026-10-23T00:00:00Z","author":{"login":"dev"},
                "repository":{"nameWithOwner":"horizon-rust/horizon"}}},
              {"__typename":"CrossReferencedEvent","source":{"__typename":"Issue"}}
            ]}}]}}}}"""
        val issue = client("tok").openIssues(Repo.HORIZON).single()
        assertEquals(IssueStatus.CLAIMED, issue.status)
        assertEquals(PrState.MERGED, issue.linkedPrs.single().state)
        val claim = issue.claims.single()
        assertTrue(claim.viaCommand)
        assertEquals(GitHubClient.millis("2026-10-22T00:00:00Z"), claim.claimedAt)
        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun detailReadsTimeline() = runTest {
        routes["GET /repos/horizon-rust/horizon/issues/7"] = """{"number":7,"title":"T","html_url":"u",
          "assignees":[${user("dev")}],"body":"b"}"""
        routes["GET /repos/horizon-rust/horizon/issues/7/comments"] = """[
          {"id":1,"user":${user("dev")},"body":"/claim","created_at":"2026-10-22T00:00:00Z"}]"""
        routes["GET /repos/horizon-rust/horizon/issues/7/timeline"] = """[
          {"event":"assigned","created_at":"2026-10-22T00:00:01Z","actor":${user("github-actions[bot]")},"assignee":${user("dev")}},
          {"event":"cross-referenced","created_at":"2026-10-23T00:00:00Z","source":{"type":"issue","issue":{
            "number":9,"title":"P","html_url":"p","state":"open","draft":false,"pull_request":{"merged_at":null},
            "repository_url":"https://api.github.com/repos/horizon-rust/horizon","updated_at":"2026-10-23T00:00:00Z"}}},
          {"event":"cross-referenced","source":{"type":"issue","issue":{"number":3,"title":"Elsewhere","html_url":"x",
            "pull_request":{},"repository_url":"https://api.github.com/repos/other/repo"}}}
        ]"""
        val detail = client().issueDetail(Repo.HORIZON, 7, known = null)
        assertEquals(IssueStatus.HAS_PR, detail.issue.status)
        assertEquals(listOf(9), detail.issue.linkedPrs.map { it.number })
        assertTrue(detail.issue.claims.single().viaCommand)
        assertTrue(ClaimRules.isClaimComment(detail.comments.single().body))
    }

    @Test
    fun restFixedListSearchesTheWindowAndCountsEachWindow() = runTest {
        val now = GitHubClient.millis("2026-11-01T12:00:00Z")
        routes["GET /search/issues"] = """{"total_count":2,"items":[
          {"number":10,"title":"Older fix","html_url":"u","state":"closed","state_reason":"completed",
           "closed_at":"2026-10-28T00:00:00Z","updated_at":"2026-10-31T00:00:00Z"},
          {"number":12,"title":"New fix","html_url":"u","state":"closed","state_reason":"completed",
           "closed_at":"2026-11-01T10:00:00Z"}
        ]}"""
        routes["GET /repos/horizon-rust/horizon/pulls"] = """[
          {"number":20,"title":"Fix it","body":"Fixes #12","html_url":"p","state":"closed","merged_at":"2026-11-01T10:00:00Z"},
          {"number":21,"title":"Abandoned","body":"Fixes #10","html_url":"p","state":"closed","merged_at":null}
        ]"""
        val fixed = client().fixedIssues(Repo.HORIZON, FixedWindow.WEEK, now = now)
        assertEquals(listOf(12, 10), fixed.issues.map { it.number })
        assertTrue(fixed.issues.all { it.status == IssueStatus.FIXED })
        assertEquals(20, fixed.issues[0].primaryPr!!.number)
        assertEquals(null, fixed.issues[1].primaryPr)
        // The day comes from the complete week list; the month is asked (the mock answers 2 again).
        assertEquals(mapOf(FixedWindow.WEEK to 2, FixedWindow.DAY to 1, FixedWindow.MONTH to 2), fixed.counts)
        assertTrue(!fixed.capped)

        val first = server.takeRequest().requestUrl!!
        assertEquals("/search/issues", first.encodedPath)
        val q = first.queryParameter("q")!!
        assertTrue(q.contains("repo:horizon-rust/horizon is:issue is:closed reason:completed closed:>=2026-10-25T12:00:00Z"), q)
    }

    @Test
    fun graphQlFixedListReadsTheCloser() = runTest {
        routes["POST /graphql"] = """{"data":{"results":{"issueCount":1,
          "pageInfo":{"hasNextPage":false,"endCursor":null},
          "nodes":[{"number":7,"title":"T","url":"https://github.com/horizon-rust/horizon/issues/7",
            "closedAt":"2026-10-31T00:00:00Z","stateReason":"COMPLETED",
            "closedByPullRequestsReferences":{"nodes":[]},
            "timelineItems":{"nodes":[
              {"__typename":"ClosedEvent","createdAt":"2026-10-31T00:00:00Z","closer":{"__typename":"PullRequest",
                "number":9,"title":"P","url":"p","state":"MERGED","isDraft":false,"updatedAt":"2026-10-31T00:00:00Z",
                "repository":{"nameWithOwner":"horizon-rust/horizon"}}}
            ]}}]}}}"""
        val fixed = client("tok").fixedIssues(Repo.HORIZON, FixedWindow.WEEK, now = GitHubClient.millis("2026-11-01T00:00:00Z"))
        val issue = fixed.issues.single()
        assertEquals(9, issue.closedByPr)
        assertEquals(PrState.MERGED, issue.primaryPr!!.state)
        assertTrue(server.takeRequest().body.readUtf8().contains("reason:completed"))
    }

    @Test
    fun rateLimitIsExplained() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(403)
                .addHeader("X-RateLimit-Remaining", "0").addHeader("X-RateLimit-Limit", "60")
                .addHeader("X-RateLimit-Reset", "1800000000")
                .setBody("""{"message":"API rate limit exceeded for 1.2.3.4."}""")
        }
        val error = assertFailsWith<GitHubException> { client().openIssues(Repo.HORIZON) }
        assertTrue(error.rateLimited)
        assertTrue(error.message!!.contains("token"))
    }
}
