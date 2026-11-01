package dev.horizonlab.issues.data

import android.content.Context
import dev.horizonlab.issues.core.Repo
import dev.horizonlab.issues.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val repo: Repo = Repo.HORIZON,
    val token: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
)

/** The few settings, in private SharedPreferences (the app opts out of backups, so the token stays on the phone). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<Settings> = state.asStateFlow()

    private fun read() = Settings(
        repo = prefs.getString("repo", null)?.let(Repo::parse) ?: Repo.HORIZON,
        token = prefs.getString("token", "") ?: "",
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
    )

    fun update(change: (Settings) -> Settings) {
        val next = change(state.value)
        prefs.edit()
            .putString("repo", next.repo.slug)
            .putString("token", next.token.trim())
            .putString("theme", next.theme.name)
            .apply()
        state.value = next.copy(token = next.token.trim())
    }
}
