package dev.horizonlab.issues.data

import dev.horizonlab.issues.core.FixedWindow
import dev.horizonlab.issues.core.GitHubClient
import dev.horizonlab.issues.core.Issue
import dev.horizonlab.issues.core.Repo
import dev.horizonlab.issues.core.Scope
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class CachedIssues(
    val fetchedAt: Long,
    val issues: List<Issue>,
    /** For the fixed list: how many were fixed in each window, by window name. */
    val counts: Map<String, Int> = emptyMap(),
)

/** The last list per repository, so the app opens on it at once and works offline. */
class IssueCache(private val dir: File) {
    private fun file(repo: Repo, scope: Scope, window: FixedWindow) = File(
        dir,
        (if (scope == Scope.OPEN) "issues" else "fixed-${window.days}d") + "-${repo.owner}-${repo.name}.json",
    )

    fun read(repo: Repo, scope: Scope, window: FixedWindow = FixedWindow.WEEK): CachedIssues? = runCatching {
        GitHubClient.JSON.decodeFromString<CachedIssues>(file(repo, scope, window).readText())
    }.getOrNull()

    fun write(repo: Repo, scope: Scope, cached: CachedIssues, window: FixedWindow = FixedWindow.WEEK) {
        runCatching {
            val target = file(repo, scope, window)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(GitHubClient.JSON.encodeToString(cached))
            tmp.renameTo(target)
        }
    }
}
