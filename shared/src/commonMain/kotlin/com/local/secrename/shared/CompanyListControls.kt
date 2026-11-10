package com.local.secrename.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun CompanyListControls(
    state: CompanyListState, busy: Boolean, onImport: () -> Unit,
    onSelect: (Int, Int) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    var manage by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Company list", style = MaterialTheme.typography.labelMedium)
            Text(if (state.count == 0) "Optional" else "${state.filename} · ${state.count} companies",
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = { if (state.count == 0) onImport() else manage = true }, enabled = !busy) {
            Text(if (state.count == 0) "Import Excel" else "Change")
        }
    }
    if (manage) AlertDialog(
        onDismissRequest = { manage = false }, title = { Text("Company list") },
        text = { Text("${state.filename}\n${state.count} companies stored on this device.") },
        confirmButton = { TextButton(onClick = { manage = false; onImport() }, enabled = !busy) { Text("Replace list") } },
        dismissButton = {
            Row {
                TextButton(onClick = { manage = false; onRemove() }, enabled = !busy) { Text("Remove list") }
                TextButton(onClick = { manage = false }) { Text("Cancel") }
            }
        },
    )
    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!busy) onCancel() },
            title = { Text(if (state.count > 0) "Replace company list?" else "Import company list") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(preview.filename, style = MaterialTheme.typography.bodySmall)
                    ListChoice("Worksheet", preview.sheets, preview.sheetIndex, !busy) { onSelect(it, -1) }
                    ListChoice("Company column", preview.columns, preview.columnIndex, !busy) { onSelect(preview.sheetIndex, it) }
                    preview.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (preview.count > 0) Text("${preview.count} companies", style = MaterialTheme.typography.titleSmall)
                    preview.names.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (preview.count > preview.names.size) Text("…", style = MaterialTheme.typography.bodySmall)
                    if (preview.note.isNotBlank()) Text(preview.note, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = onConfirm, enabled = !busy && preview.error == null && preview.count > 0) {
                Text(if (state.count > 0) "Replace list" else "Use list")
            } },
            dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ListChoice(label: String, options: List<String>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${options.getOrNull(selected) ?: "Choose"}", maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 240.dp)) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(index) })
            }
        }
    }
}
