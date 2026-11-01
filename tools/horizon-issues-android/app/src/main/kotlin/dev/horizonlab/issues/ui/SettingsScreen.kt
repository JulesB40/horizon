package dev.horizonlab.issues.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.horizonlab.issues.core.ClaimRules
import dev.horizonlab.issues.core.RateLimit
import dev.horizonlab.issues.core.Repo
import dev.horizonlab.issues.data.Settings
import dev.horizonlab.issues.ui.components.Pill
import dev.horizonlab.issues.ui.components.openUrl
import dev.horizonlab.issues.ui.theme.GeistMono
import dev.horizonlab.issues.ui.theme.Theme
import dev.horizonlab.issues.ui.theme.ThemeMode
import dev.horizonlab.issues.ui.theme.Type
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    settings: Settings,
    rateLimit: RateLimit?,
    onBack: () -> Unit,
    onSave: ((Settings) -> Settings) -> Unit,
) {
    val p = Theme.palette
    val context = LocalContext.current
    var repoText by remember(settings.repo) { mutableStateOf(settings.repo.slug) }
    var token by remember(settings.token) { mutableStateOf(settings.token) }
    var showToken by remember { mutableStateOf(false) }
    val parsedRepo = Repo.parse(repoText)
    val dirty = (parsedRepo != null && parsedRepo != settings.repo) || token.trim() != settings.token

    Column(
        Modifier
            .fillMaxSize()
            .background(p.screen)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(6.dp))
            Text("Settings", style = Type.base.copy(fontWeight = FontWeight.SemiBold), color = p.foreground)
            Spacer(Modifier.weight(1f))
            if (dirty) {
                Text(
                    "Save",
                    style = Type.sm.copy(fontWeight = FontWeight.SemiBold),
                    color = p.onProminent,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(CircleShape)
                        .background(p.prominent)
                        .clickable {
                            onSave { it.copy(repo = parsedRepo ?: it.repo, token = token.trim()) }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        HorizontalDivider(color = p.separator, thickness = 1.dp)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
        ) {
            Section("Repository") {
                Field(repoText, { repoText = it }, "owner/name", mono = true)
                Hint(
                    if (parsedRepo == null) "Write it as owner/name, or paste its GitHub link."
                    else "Open issues of ${parsedRepo.slug}.",
                    error = parsedRepo == null,
                )
                if (settings.repo != Repo.HORIZON) {
                    Text(
                        "Back to ${Repo.HORIZON.slug}",
                        style = Type.xs.copy(fontWeight = FontWeight.SemiBold), color = p.primaryText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            .clickable { onSave { it.copy(repo = Repo.HORIZON) } },
                    )
                }
            }

            Section("GitHub token") {
                Field(
                    token, { token = it }, "github_pat_…", mono = true,
                    hidden = !showToken,
                    trailing = {
                        Icon(
                            if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            if (showToken) "Hide" else "Show",
                            tint = p.tertiary,
                            modifier = Modifier.size(18.dp).clip(CircleShape).clickable { showToken = !showToken },
                        )
                    },
                )
                Hint(
                    "Optional. Without one GitHub allows 60 requests an hour, and a refresh takes about 7 " +
                        "(unchanged pages are free). A token gives 5,000, reads the sidebar's pull request links and " +
                        "claim times exactly, and lets you /claim from the app (fine-grained: Issues read and write).",
                )
                Text(
                    "Create a token on GitHub",
                    style = Type.xs.copy(fontWeight = FontWeight.SemiBold), color = p.primaryText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        .clickable { openUrl(context, "https://github.com/settings/personal-access-tokens/new", p) },
                )
            }

            Section("Appearance") {
                Row(
                    Modifier.padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.grouped).padding(3.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = settings.theme == mode
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) p.card else Color.Transparent)
                                .border(1.dp, if (selected) p.border else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { onSave { it.copy(theme = mode) } }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = Type.xs.copy(fontWeight = FontWeight.Medium),
                                color = if (selected) p.foreground else p.secondary,
                            )
                        }
                    }
                }
            }

            Section("GitHub API") {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            rateLimit?.let { "${it.remaining} of ${it.limit} requests left" } ?: "No request yet",
                            style = Type.sm.copy(fontWeight = FontWeight.Medium), color = p.foreground,
                        )
                        if (rateLimit != null) {
                            Text(
                                "Resets at " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(rateLimit.resetAt)),
                                style = Type.xs2, color = p.tertiary,
                            )
                        }
                    }
                    Pill(if (settings.token.isNotBlank()) "Token" else "Anonymous", if (settings.token.isNotBlank()) p.available else p.draft)
                }
            }

            Section("How statuses are read") {
                Legend("Available", p.available.content, "Nobody is assigned, no “${ClaimRules.KIRA_LABEL}” label, no open pull request links it.")
                Legend("Claimed", p.claimed.content, "Someone is assigned (a /claim comment, or by hand), or Kira's Discord claim added “${ClaimRules.KIRA_LABEL}”. A /claim is released after ${ClaimRules.STALE_DAYS} days without an open pull request.")
                Legend("Has PR", p.review.content, "An open or draft pull request links it: Fixes #N, the sidebar, a mention, or its number in the branch name.")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = Theme.palette
    Text(
        title.uppercase(),
        style = Type.xs3.copy(fontWeight = FontWeight.SemiBold, letterSpacing = Type.xs3.fontSize * 0.08f),
        color = p.tertiary,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
    )
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.card)
            .border(1.dp, p.border, RoundedCornerShape(18.dp)),
        content = content,
    )
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    mono: Boolean = false,
    hidden: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val p = Theme.palette
    val style = (if (mono) Type.mono.copy(fontSize = Type.sm.fontSize, fontFamily = GeistMono) else Type.sm).copy(color = p.foreground)
    Row(
        Modifier
            .padding(12.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.search)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(p.primary),
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (hidden) KeyboardType.Password else KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = style.copy(color = p.tertiary))
                    field()
                }
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    val p = Theme.palette
    Text(
        text,
        style = Type.xs2,
        color = if (error) p.danger.content else p.secondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    )
}

@Composable
private fun Legend(title: String, color: Color, text: String) {
    val p = Theme.palette
    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Type.sm.copy(fontWeight = FontWeight.Medium), color = p.foreground)
            Text(text, style = Type.xs2, color = p.secondary)
        }
    }
}
