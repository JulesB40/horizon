package dev.horizonlab.issues.data

import dev.horizonlab.issues.core.GitHubClient
import dev.horizonlab.issues.core.Issue
import dev.horizonlab.issues.core.Repo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File

@Serializable
data class CachedIssues(val fetchedAt: Long, val issues: List<Issue>)

/** The last list per repository, so the app opens on it at once and works offline. */
class IssueCache(private val dir: File) {
    private fun file(repo: Repo) = File(dir, "issues-${repo.owner}-${repo.name}.json")

    fun read(repo: Repo): CachedIssues? = runCatching {
        GitHubClient.JSON.decodeFromString<CachedIssues>(file(repo).readText())
    }.getOrNull()

    fun write(repo: Repo, cached: CachedIssues) {
        runCatching {
            val tmp = File(dir, file(repo).name + ".tmp")
            tmp.writeText(GitHubClient.JSON.encodeToString(cached))
            tmp.renameTo(file(repo))
        }
    }
}
