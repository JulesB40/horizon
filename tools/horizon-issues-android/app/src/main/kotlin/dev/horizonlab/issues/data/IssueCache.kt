package dev.horizonlab.issues.data

import dev.horizonlab.issues.core.GitHubClient
import dev.horizonlab.issues.core.Issue
import dev.horizonlab.issues.core.Repo
import dev.horizonlab.issues.core.Scope
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class CachedIssues(val fetchedAt: Long, val issues: List<Issue>)

/** The last list per repository, so the app opens on it at once and works offline. */
class IssueCache(private val dir: File) {
    private fun file(repo: Repo, scope: Scope) =
        File(dir, (if (scope == Scope.OPEN) "issues" else "fixed") + "-${repo.owner}-${repo.name}.json")

    fun read(repo: Repo, scope: Scope): CachedIssues? = runCatching {
        GitHubClient.JSON.decodeFromString<CachedIssues>(file(repo, scope).readText())
    }.getOrNull()

    fun write(repo: Repo, scope: Scope, cached: CachedIssues) {
        runCatching {
            val target = file(repo, scope)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(GitHubClient.JSON.encodeToString(cached))
            tmp.renameTo(target)
        }
    }
}
