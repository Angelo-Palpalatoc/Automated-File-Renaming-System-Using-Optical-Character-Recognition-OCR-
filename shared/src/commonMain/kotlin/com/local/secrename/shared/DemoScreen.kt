package com.local.secrename.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// PSBank-inspired working palette; not an official brand specification.
private val BankBlue = Color(0xFF0054A4)
private val AccentRed = Color(0xFFB51F32)
private val Ink = Color(0xFF162B43)
private val Muted = Color(0xFF536477)
private val Canvas = Color(0xFFF3F6FA)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DemoScreen(
    state: DemoState,
    onSource: () -> Unit, onOutput: () -> Unit, onScan: () -> Unit,
    onSelect: (String) -> Unit, onAnalyze: () -> Unit, onName: (String) -> Unit,
    onVerify: (Boolean) -> Unit, onExport: () -> Unit, onHistory: () -> Unit,
) {
    val snackbars = remember { SnackbarHostState() }
    val reviewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    fun act(action: NextAction) {
        when (action) {
            NextAction.SOURCE -> onSource()
            NextAction.OUTPUT -> onOutput()
            NextAction.ANALYZE -> onAnalyze()
            NextAction.REVIEW -> scope.launch { reviewRequester.bringIntoView() }
            NextAction.SCAN -> onScan()
            NextAction.HISTORY -> { historyOpen = true; onHistory() }
            NextAction.NONE -> Unit
        }
    }
    LaunchedEffect(state.notice.id) {
        if (state.notice.announce) {
            val result = snackbars.showSnackbar(
                message = state.notice.title,
                actionLabel = state.notice.action.takeIf { it != NextAction.NONE }?.label,
                withDismissAction = true,
                duration = if (state.notice.kind == NoticeKind.ERROR) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) act(state.notice.action)
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = BankBlue, secondary = AccentRed, background = Canvas, surface = Color.White, onSurface = Ink, onBackground = Ink)) {
        if (historyOpen) AlertDialog(
            onDismissRequest = { historyOpen = false },
            title = { Text("Export history") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!state.historyLoaded) Text("Loading local history…")
                    else if (state.history.isEmpty()) Text("No exports yet. Your verified copies will appear here.")
                    state.history.forEach { item ->
                        Text(if (item.completed) "✓ Saved and verified" else "! Incomplete — check output", color = if (item.completed) Color(0xFF14643C) else AccentRed, fontWeight = FontWeight.SemiBold)
                        Text(item.target.ifBlank { "Filename not confirmed" })
                        Text("From ${item.source}\n${item.date}", style = MaterialTheme.typography.bodySmall, color = Muted)
                        HorizontalDivider()
                    }
                    if (state.history.any { !it.completed }) Text("A partial or unconfirmed file may exist. Inspect the output folder before repeating an export.", color = AccentRed)
                }
            },
            confirmButton = { TextButton(onClick = { historyOpen = false }) { Text("Close") } },
            dismissButton = { TextButton(onClick = onHistory, enabled = !state.busy) { Text("Refresh") } },
        )
        Scaffold(containerColor = Canvas, snackbarHost = { SnackbarHost(snackbars) }, topBar = {
            Surface(shadowElevation = 1.dp) {
                Column(Modifier.safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(BankBlue, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Text("SEC", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Document Rename", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Scan · Review · Save a copy", style = MaterialTheme.typography.bodySmall, color = Muted)
                        }
                        Text("PILOT", color = AccentRed, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    StatusPanel(state, ::act)
                }
            }
        }) { insets ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(insets).imePadding()) {
                val wide = maxWidth >= 840.dp
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).widthIn(max = 1200.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Workflow(state)
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Column(Modifier.weight(0.43f)) { ImportCard(state, onSource, onOutput, onScan, onSelect) }
                            Column(Modifier.weight(0.57f).bringIntoViewRequester(reviewRequester)) { ReviewCard(state, onAnalyze, onName, onVerify, onExport) }
                        }
                    } else {
                        ImportCard(state, onSource, onOutput, onScan, onSelect)
                        Box(Modifier.bringIntoViewRequester(reviewRequester)) { ReviewCard(state, onAnalyze, onName, onVerify, onExport) }
                    }
                    SectionCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Export history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Completed and incomplete copies", style = MaterialTheme.typography.bodySmall, color = Muted)
                            }
                            TextButton(onClick = { historyOpen = true; onHistory() }, enabled = !state.busy) { Text("View") }
                        }
                    }
                    Text("On-device document processing · Imported originals stay unchanged", style = MaterialTheme.typography.labelMedium, color = BankBlue)
                    Text("Pilot: one document at a time. Use fictional or bank-approved test documents. Camera scans in app storage are removed if the app is uninstalled.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun StatusPanel(state: DemoState, onAction: (NextAction) -> Unit) {
    val notice = state.notice
    val tint = when (notice.kind) {
        NoticeKind.SUCCESS -> Color(0xFF14643C)
        NoticeKind.WARNING -> Color(0xFF805300)
        NoticeKind.ERROR -> AccentRed
        NoticeKind.INFO -> BankBlue
    }
    Surface(color = tint.copy(alpha = 0.07f), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.Top) {
                Text(when (notice.kind) { NoticeKind.SUCCESS -> "✓"; NoticeKind.WARNING, NoticeKind.ERROR -> "!"; else -> "i" }, color = tint, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(notice.title, color = tint, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(notice.detail, color = Ink, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.busy) {
                Spacer(Modifier.height(3.dp))
                if (state.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text(state.operation, color = Muted, style = MaterialTheme.typography.labelSmall)
            } else if (notice.action != NextAction.NONE) {
                TextButton(onClick = { onAction(notice.action) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) { Text(notice.action.label) }
            }
        }
    }
}

@Composable
private fun Workflow(state: DemoState) {
    val stage = when { state.savedCopy != null -> 3; state.analyzed -> 2; state.selectedId != null -> 1; else -> 0 }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Import", "Review", "Export").forEachIndexed { index, label ->
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = if (index <= stage) Color(0xFFE3EDF9) else Color(0xFFE9EDF2)) {
                Text("${index + 1}  $label", Modifier.padding(12.dp), color = if (index <= stage) BankBlue else Muted, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ImportCard(state: DemoState, onSource: () -> Unit, onOutput: () -> Unit, onScan: () -> Unit, onSelect: (String) -> Unit) {
    var query by rememberSaveable(state.sourceLabel) { mutableStateOf("") }
    SectionCard {
        Text("1. Choose your documents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Start with a camera scan, an existing photo, or a local folder.", style = MaterialTheme.typography.bodySmall, color = Muted)
        Button(onClick = onScan, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Scan with camera") }
        Text("Existing photo? Use the gallery button inside the scanner.", style = MaterialTheme.typography.bodySmall, color = Muted)
        OutlinedButton(onClick = onSource, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.files.isEmpty()) "Choose source folder" else "Change source folder") }
        Text("Source\n${state.sourceLabel}", style = MaterialTheme.typography.bodySmall, color = Muted)
        if (state.files.isEmpty()) {
            Surface(color = Canvas, shape = RoundedCornerShape(10.dp)) { Text("No documents listed\nChoose a folder containing PDFs, JPGs, or PNGs. Subfolders are not scanned.", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = Muted) }
        } else {
            Text("${state.files.size} document${if (state.files.size == 1) "" else "s"} available", style = MaterialTheme.typography.labelLarge, color = BankBlue)
            if (state.files.size > 5) OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Find a document") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val visible = state.files.filter { it.name.contains(query, ignoreCase = true) }
            if (visible.isEmpty()) Text("No filenames match. Try another search.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 210.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.id }) { file ->
                    val selected = file.id == state.selectedId
                    OutlinedButton(onClick = { onSelect(file.id) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Color(0xFFE3EDF9) else Color.Transparent)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (selected) "✓" else "○", modifier = Modifier.padding(end = 10.dp))
                            Text(file.name, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Text("Save copies to", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = onOutput, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.outputReady) "Change output location" else "Choose output location") }
        Text(state.outputLabel, style = MaterialTheme.typography.bodySmall, color = Muted)
        Text("A new batch folder keeps exports separate from your originals.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun ReviewCard(state: DemoState, onAnalyze: () -> Unit, onName: (String) -> Unit, onVerify: (Boolean) -> Unit, onExport: () -> Unit) {
    var evidenceOpen by rememberSaveable(state.selectedId) { mutableStateOf(false) }
    SectionCard {
        Text("2. Review the company name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val file = state.files.find { it.id == state.selectedId }
        if (file == null) Text("Select a document to see its preview and company-name suggestion here.", color = Muted)
        else {
            Text(file.name, color = BankBlue, style = MaterialTheme.typography.labelLarge)
            Button(onClick = onAnalyze, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.analyzed) "Read company name again" else "Read company name") }
            state.preview?.let {
                Surface(color = Canvas, shape = RoundedCornerShape(10.dp)) { Image(it, "First page of selected document", Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(10.dp)) }
                Text("First-page preview. Check every relevant page before confirming a multipage document.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (state.analyzed) {
                if (state.candidates.size > 1) {
                    Text("Choose a candidate, then verify it against the document:", style = MaterialTheme.typography.bodySmall)
                    state.candidates.forEach { candidate -> OutlinedButton(onClick = { onName(candidate) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(candidate) } }
                }
                OutlinedTextField(value = state.company, onValueChange = onName, enabled = !state.busy, isError = state.nameError != null, label = { Text("Company name") }, supportingText = { Text(state.nameError ?: "Edit this to match the certificate holder. OCR suggestions require your review.") }, modifier = Modifier.fillMaxWidth())
                Surface(color = Canvas, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("PROPOSED FILENAME", style = MaterialTheme.typography.labelSmall, color = Muted)
                        Text(state.proposal.ifBlank { "Enter a valid company name" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Existing names get (1), (2), … when saved.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                Row(Modifier.fillMaxWidth().toggleable(value = state.verifiedByUser, enabled = !state.busy && state.nameError == null && state.company.isNotBlank(), role = Role.Checkbox, onValueChange = onVerify).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.verifiedByUser, onCheckedChange = null)
                    Text("I checked the company name against the document.", Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
                Text("3. Save a verified copy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = onExport, enabled = state.canExport, modifier = Modifier.fillMaxWidth()) { Text(if (state.savedCopy != null) "Copy saved ✓" else "Save verified renamed copy") }
                state.exportBlocker?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                state.savedCopy?.let { saved ->
                    Surface(color = Color(0xFFEAF6EE), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("✓ Copy saved and verified", color = Color(0xFF14643C), fontWeight = FontWeight.Bold)
                            SelectionContainer { Text(saved.name, fontWeight = FontWeight.Medium) }
                            Text("Batch folder: ${saved.batch}\nContent verified against the source. Original unchanged.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                TextButton(onClick = { evidenceOpen = !evidenceOpen }) { Text(if (evidenceOpen) "Hide OCR evidence" else "Show OCR evidence") }
                if (evidenceOpen) SelectionContainer { Text(state.evidence.ifBlank { "No readable text. Check the original and enter the name manually." }, style = MaterialTheme.typography.bodySmall, color = Muted) }
            }
        }
    }
}
