package dev.horizonlab.issues.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.horizonlab.issues.data.Saved
import dev.horizonlab.issues.ui.theme.Theme
import dev.horizonlab.issues.ui.theme.Type

/** Pin an issue and choose its groups; a new group can be made here, holding the issue at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSheet(
    number: Int,
    title: String?,
    saved: Saved,
    onPin: () -> Unit,
    onToggle: (groupId: String, inGroup: Boolean) -> Unit,
    onCreate: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = Theme.palette
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newName by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = p.card) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Save #$number", style = Type.lg, color = p.foreground, modifier = Modifier.padding(horizontal = 20.dp))
            if (title != null) {
                Text(title, style = Type.xs, color = p.secondary, maxLines = 1, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            }
            Spacer(Modifier.height(12.dp))
            val pinned = saved.isPinned(number)
            SheetRow(
                icon = { Icon(Icons.Rounded.PushPin, null, tint = if (pinned) p.primaryText else p.secondary, modifier = Modifier.size(18.dp)) },
                label = if (pinned) "Pinned to the top" else "Pin to the top",
                checked = pinned,
                onClick = onPin,
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.separator, thickness = 1.dp)
            Text(
                "GROUPS",
                style = Type.xs3.copy(fontWeight = FontWeight.SemiBold, letterSpacing = Type.xs3.fontSize * 0.08f),
                color = p.tertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            if (saved.groups.isEmpty()) {
                Text("No group yet: name one below.", style = Type.sm, color = p.tertiary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            saved.groups.forEach { g ->
                val inGroup = number in g.issues
                SheetRow(
                    icon = { Icon(Icons.Rounded.Folder, null, tint = if (inGroup) p.primaryText else p.secondary, modifier = Modifier.size(18.dp)) },
                    label = g.name,
                    detail = g.issues.size.plural("issue"),
                    checked = inGroup,
                    onClick = { onToggle(g.id, !inGroup) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                NameField(newName, { newName = it }, "New group", Modifier.weight(1f)) {
                    onCreate(newName)
                    newName = ""
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (newName.isBlank()) p.grouped else p.prominent)
                        .clickable(enabled = newName.isNotBlank()) {
                            onCreate(newName)
                            newName = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, "Create group", tint = if (newName.isBlank()) p.tertiary else p.onProminent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: @Composable () -> Unit, label: String, checked: Boolean, onClick: () -> Unit, detail: String? = null) {
    val p = Theme.palette
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.sm.copy(fontWeight = FontWeight.Medium), color = p.foreground, maxLines = 1)
            if (detail != null) Text(detail, style = Type.xs2, color = p.tertiary)
        }
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) p.primary else Color.Transparent)
                .border(1.5.dp, if (checked) p.primary else p.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun NameField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val p = Theme.palette
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.search)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(40)) },
            singleLine = true,
            textStyle = Type.sm.copy(color = p.foreground),
            cursorBrush = SolidColor(p.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onDone() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = Type.sm, color = p.tertiary)
                    field()
                }
            },
        )
    }
}

/** Names a new group, or renames one. */
@Composable
fun GroupNameDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val p = Theme.palette
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(title, style = Type.lg, color = p.foreground) },
        text = { NameField(name, { name = it }, "Group name") { onConfirm(name) } },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirm, style = Type.sm.copy(fontWeight = FontWeight.SemiBold), color = if (name.isBlank()) p.tertiary else p.primaryText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = Type.sm, color = p.secondary) }
        },
    )
}

/** What the screens can do with pins and groups. */
class SavedActions(
    val pin: (number: Int) -> Unit,
    val toggle: (groupId: String, number: Int, inGroup: Boolean) -> Unit,
    val create: (name: String, number: Int?) -> Unit,
    val rename: (groupId: String, name: String) -> Unit,
    val delete: (groupId: String) -> Unit,
    val show: (groupId: String?) -> Unit,
)
