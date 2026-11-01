package dev.horizonlab.issues.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.horizonlab.issues.core.GitHubClient
import dev.horizonlab.issues.core.Issue
import dev.horizonlab.issues.core.IssueDetail
import dev.horizonlab.issues.core.IssueStatus
import dev.horizonlab.issues.core.Label
import dev.horizonlab.issues.core.RateLimit
import dev.horizonlab.issues.core.Repo
import dev.horizonlab.issues.core.Scope
import dev.horizonlab.issues.data.CachedIssues
import dev.horizonlab.issues.data.Group
import dev.horizonlab.issues.data.IssueCache
import dev.horizonlab.issues.data.Saved
import dev.horizonlab.issues.data.SavedStore
import dev.horizonlab.issues.data.Settings
import dev.horizonlab.issues.data.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class Filter(val label: String) {
    ALL("All"),
    AVAILABLE("Available"),
    CLAIMED("Claimed"),
    HAS_PR("Has PR");

    fun matches(issue: Issue): Boolean = when (this) {
        ALL -> true
        AVAILABLE -> issue.status == IssueStatus.AVAILABLE
        CLAIMED -> issue.status == IssueStatus.CLAIMED
        HAS_PR -> issue.status == IssueStatus.HAS_PR
    }
}

enum class Sort(val label: String) {
    NEWEST("Newest"),
    UPDATED("Recently updated"),
    DISCUSSED("Most discussed"),
    OLDEST("Oldest"),
}

data class ListUi(
    val repo: Repo = Repo.HORIZON,
    /** Open issues, or the ones recently fixed. */
    val scope: Scope = Scope.OPEN,
    val lists: Map<Scope, List<Issue>> = emptyMap(),
    val fetched: Map<Scope, Long> = emptyMap(),
    val visible: List<Issue> = emptyList(),
    val counts: Map<Filter, Int> = emptyMap(),
    val labels: List<Pair<Label, Int>> = emptyList(),
    val filter: Filter = Filter.ALL,
    val sort: Sort = Sort.NEWEST,
    val query: String = "",
    val label: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Fixed issues closed in the last day, week and month. */
    val recent: List<Int> = listOf(0, 0, 0),
    /** Pins and groups of this repository. */
    val saved: Saved = Saved(),
    /** The group shown instead of the scope's list, by id. */
    val group: String? = null,
    /** The pinned issues that pass the filters, shown first (newest pin first). */
    val pinnedVisible: List<Issue> = emptyList(),
    /** Numbers of the shown group that neither list holds (closed long ago, not planned...). */
    val missing: List<Int> = emptyList(),
) {
    val selectedGroup: Group? get() = saved.groups.firstOrNull { it.id == group }

    /** The issues of the current scope. */
    val all: List<Issue> get() = lists[scope].orEmpty()
    val fetchedAt: Long get() = fetched[scope] ?: 0

    fun withList(scope: Scope, issues: List<Issue>, at: Long? = null) = copy(
        lists = lists + (scope to issues),
        fetched = if (at != null) fetched + (scope to at) else fetched,
    )

    fun recompute(now: Long = System.currentTimeMillis()): ListUi {
        val q = query.trim().removePrefix("#").lowercase()
        val shownGroup = selectedGroup
        // A group gathers its issues from both lists, whatever their status.
        val base = if (shownGroup == null) all else {
            val members = shownGroup.issues.toSet()
            (lists[Scope.OPEN].orEmpty() + lists[Scope.FIXED].orEmpty()).distinctBy { it.number }.filter { it.number in members }
        }
        // Every fixed issue has the same status: the status filters apply to open issues only.
        val statusFilter = if (scope == Scope.FIXED || shownGroup != null) Filter.ALL else filter
        val filtered = base.filter { issue ->
            statusFilter.matches(issue) &&
                (label == null || issue.labels.any { it.name == label }) &&
                (q.isEmpty() || issue.number.toString().startsWith(q) || issue.title.lowercase().contains(q) ||
                    issue.assignees.any { it.login.lowercase().contains(q) } ||
                    issue.labels.any { it.name.lowercase().contains(q) })
        }
        // Fixed issues order by when they were closed.
        val time: (Issue) -> Long = if (scope == Scope.FIXED) Issue::closedAt else Issue::createdAt
        val sorted = when (sort) {
            Sort.NEWEST -> filtered.sortedByDescending(time)
            Sort.UPDATED -> filtered.sortedByDescending { it.updatedAt }
            Sort.DISCUSSED -> filtered.sortedByDescending { it.comments }
            Sort.OLDEST -> filtered.sortedBy(time)
        }
        val day = 86_400_000L
        val recent = listOf(1L, 7L, 30L).map { days -> lists[Scope.FIXED].orEmpty().count { now - it.closedAt <= days * day } }
        val labelCounts = all.flatMap { it.labels }.groupBy { it.name }
            .map { (_, same) -> same.first() to same.size }
            .sortedByDescending { it.second }
        val pins = saved.pinned
        val missing = if (shownGroup == null || q.isNotEmpty() || label != null) emptyList()
        else shownGroup.issues.filter { n -> base.none { it.number == n } }
        return copy(
            pinnedVisible = sorted.filter { it.number in pins }.sortedBy { pins.indexOf(it.number) },
            visible = sorted.filterNot { it.number in pins },
            missing = missing,
            counts = Filter.entries.associateWith { f -> all.count(f::matches) },
            labels = labelCounts,
            recent = recent,
        )
    }
}

data class DetailUi(
    val loading: Boolean = false,
    val detail: IssueDetail? = null,
    val error: String? = null,
    val posting: Boolean = false,
)

class IssuesViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val cache = IssueCache(app.filesDir)
    private val savedStore = SavedStore(app, store.settings.value.repo)
    private val http = GitHubClient.httpClient(File(app.cacheDir, "http"))
    private var client = GitHubClient(http, store.settings.value.token.ifBlank { null })

    val settings: StateFlow<Settings> = store.settings

    private val _list = MutableStateFlow(ListUi(repo = store.settings.value.repo))
    val list: StateFlow<ListUi> = _list.asStateFlow()

    private val _details = MutableStateFlow<Map<Int, DetailUi>>(emptyMap())
    val details: StateFlow<Map<Int, DetailUi>> = _details.asStateFlow()

    private val _rateLimit = MutableStateFlow<RateLimit?>(null)
    val rateLimit: StateFlow<RateLimit?> = _rateLimit.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private var refreshJob: Job? = null

    init {
        loadCached()
        refresh(fresh = false)
    }

    private fun loadCached() {
        val repo = store.settings.value.repo
        _list.update { old ->
            var ui = ListUi(repo = repo, scope = old.scope, filter = old.filter, sort = old.sort, saved = savedStore.saved.value)
            for (scope in Scope.entries) {
                cache.read(repo, scope)?.let { ui = ui.withList(scope, it.issues, it.fetchedAt) }
            }
            ui.recompute()
        }
    }

    /** Reloads the current list; `fresh` revalidates every page (pull to refresh) instead of trusting a minute-old copy. */
    fun refresh(fresh: Boolean = true) {
        refreshJob?.cancel()
        val repo = store.settings.value.repo
        val scope = _list.value.scope
        _list.update { it.copy(loading = it.all.isEmpty(), refreshing = it.all.isNotEmpty() && fresh, error = null) }
        refreshJob = viewModelScope.launch {
            try {
                val issues = client.issues(repo, scope, fresh)
                val now = System.currentTimeMillis()
                cache.write(repo, scope, CachedIssues(now, issues))
                _list.update { it.withList(scope, issues, now).copy(loading = false, refreshing = false).recompute() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _list.update { it.copy(loading = false, refreshing = false, error = GitHubClient.describe(e)) }
            } finally {
                _rateLimit.value = client.rateLimit ?: _rateLimit.value
            }
        }
    }

    /** Switches between open and fixed issues; a list never loaded, or older than a minute, reloads. */
    fun setScope(scope: Scope) {
        if (scope == _list.value.scope) return
        _list.update { it.copy(scope = scope, error = null, loading = false, refreshing = false).recompute() }
        val age = System.currentTimeMillis() - _list.value.fetchedAt
        if (_list.value.all.isEmpty() || age > 60_000) refresh(fresh = false) else refreshJob?.cancel()
    }

    fun setFilter(filter: Filter) = _list.update { it.copy(filter = filter).recompute() }
    fun setSort(sort: Sort) = _list.update { it.copy(sort = sort).recompute() }
    fun setQuery(query: String) = _list.update { it.copy(query = query).recompute() }
    fun setLabel(label: String?) = _list.update { it.copy(label = label).recompute() }

    /** The list's copy of an issue, open or fixed. */
    fun issue(number: Int): Issue? = _list.value.lists.values.asSequence().flatten().firstOrNull { it.number == number }

    fun loadDetail(number: Int) {
        val repo = store.settings.value.repo
        _details.update { it + (number to (it[number] ?: DetailUi()).copy(loading = true, error = null)) }
        viewModelScope.launch {
            try {
                val detail = client.issueDetail(repo, number, issue(number))
                _details.update { it + (number to (it[number] ?: DetailUi()).copy(loading = false, detail = detail)) }
                // Keep the row in step with what the detail learnt (fresh assignees, timeline links).
                _list.update { ui ->
                    ui.copy(
                        lists = ui.lists.mapValues { (_, issues) ->
                            issues.map { if (it.number == number) detail.issue.copy(body = null) else it }
                        },
                    ).recompute()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _details.update { it + (number to (it[number] ?: DetailUi()).copy(loading = false, error = GitHubClient.describe(e))) }
            } finally {
                _rateLimit.value = client.rateLimit ?: _rateLimit.value
            }
        }
    }

    /** Comments `/claim`; the claim workflow assigns the commenter a few seconds later. */
    fun claim(number: Int) {
        val repo = store.settings.value.repo
        _details.update { it + (number to (it[number] ?: DetailUi()).copy(posting = true)) }
        viewModelScope.launch {
            try {
                client.comment(repo, number, "/claim")
                _messages.tryEmit("Claim posted. The bot assigns you in a few seconds.")
                delay(6_000)
                loadDetail(number)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(GitHubClient.describe(e))
            } finally {
                _details.update { it + (number to (it[number] ?: DetailUi()).copy(posting = false)) }
            }
        }
    }

    // --- Pins and groups -------------------------------------------------------------------------------------

    private fun savedChanged() = _list.update { ui ->
        val saved = savedStore.saved.value
        ui.copy(saved = saved, group = ui.group?.takeIf { id -> saved.groups.any { it.id == id } }).recompute()
    }

    fun togglePin(number: Int) {
        val pinned = !savedStore.saved.value.isPinned(number)
        savedStore.togglePin(number)
        savedChanged()
        _messages.tryEmit(if (pinned) "Pinned #$number to the top" else "Unpinned #$number")
    }

    fun setGroup(id: String?) = _list.update { it.copy(group = id).recompute() }

    /** Creates a group, with `number` in it when given, and returns its id. */
    fun createGroup(name: String, number: Int? = null): String? {
        val id = savedStore.createGroup(name, number)
        savedChanged()
        return id
    }

    fun renameGroup(id: String, name: String) {
        savedStore.renameGroup(id, name)
        savedChanged()
    }

    fun deleteGroup(id: String) {
        savedStore.deleteGroup(id)
        savedChanged()
    }

    fun setInGroup(id: String, number: Int, inGroup: Boolean) {
        savedStore.setInGroup(id, number, inGroup)
        savedChanged()
    }

    fun updateSettings(change: (Settings) -> Settings) {
        val before = store.settings.value
        store.update(change)
        val after = store.settings.value
        if (after.token != before.token) client = GitHubClient(http, after.token.ifBlank { null })
        if (after.repo != before.repo) {
            _details.value = emptyMap()
            savedStore.switchTo(after.repo)
            loadCached()
        }
        if (after.token != before.token || after.repo != before.repo) refresh(fresh = true)
    }
}
