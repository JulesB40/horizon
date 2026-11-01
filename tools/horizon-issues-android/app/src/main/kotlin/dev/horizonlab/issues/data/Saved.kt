package dev.horizonlab.issues.data

import android.content.Context
import dev.horizonlab.issues.core.GitHubClient
import dev.horizonlab.issues.core.Repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID

/** A named collection of issue numbers, newest additions first. */
@Serializable
data class Group(val id: String, val name: String, val issues: List<Int> = emptyList())

/** What the user keeps for one repository: pinned issues (newest pin first) and groups. */
@Serializable
data class Saved(val pinned: List<Int> = emptyList(), val groups: List<Group> = emptyList()) {
    fun isPinned(number: Int) = number in pinned
    fun groupsOf(number: Int) = groups.filter { number in it.issues }
}

/** Pins and groups, per repository, on the phone only (SharedPreferences, no backup). */
class SavedStore(context: Context, repo: Repo) {
    private val prefs = context.getSharedPreferences("saved", Context.MODE_PRIVATE)
    private var repo = repo
    private val state = MutableStateFlow(read(repo))
    val saved: StateFlow<Saved> = state.asStateFlow()

    private fun read(repo: Repo): Saved = runCatching {
        GitHubClient.JSON.decodeFromString<Saved>(prefs.getString(repo.slug, null)!!)
    }.getOrDefault(Saved())

    fun switchTo(repo: Repo) {
        this.repo = repo
        state.value = read(repo)
    }

    private fun update(change: (Saved) -> Saved) {
        val next = change(state.value)
        state.value = next
        prefs.edit().putString(repo.slug, GitHubClient.JSON.encodeToString(next)).apply()
    }

    fun togglePin(number: Int) = update { s ->
        s.copy(pinned = if (number in s.pinned) s.pinned - number else listOf(number) + s.pinned)
    }

    /** Creates a group (names are trimmed; an empty one is refused) holding `first` if given; returns its id. */
    fun createGroup(name: String, first: Int?): String? {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return null
        val id = UUID.randomUUID().toString()
        update { s -> s.copy(groups = s.groups + Group(id, clean, listOfNotNull(first))) }
        return id
    }

    fun renameGroup(id: String, name: String) {
        val clean = name.trim().take(40)
        if (clean.isEmpty()) return
        update { s -> s.copy(groups = s.groups.map { if (it.id == id) it.copy(name = clean) else it }) }
    }

    fun deleteGroup(id: String) = update { s -> s.copy(groups = s.groups.filterNot { it.id == id }) }

    fun setInGroup(id: String, number: Int, inGroup: Boolean) = update { s ->
        s.copy(
            groups = s.groups.map { g ->
                when {
                    g.id != id -> g
                    inGroup && number !in g.issues -> g.copy(issues = listOf(number) + g.issues)
                    !inGroup -> g.copy(issues = g.issues - number)
                    else -> g
                }
            },
        )
    }
}
