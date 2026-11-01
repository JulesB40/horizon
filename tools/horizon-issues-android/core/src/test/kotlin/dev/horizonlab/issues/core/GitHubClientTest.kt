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
