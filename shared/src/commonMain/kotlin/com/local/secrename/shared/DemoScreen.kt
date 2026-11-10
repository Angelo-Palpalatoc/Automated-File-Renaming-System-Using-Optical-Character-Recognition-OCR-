package com.local.secrename.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.panpf.zoomimage.ZoomImage
import kotlinx.coroutines.delay

private val Blue = Color(0xFF0054A4)
private val Red = Color(0xFFB51F32)
private val Ink = Color(0xFF162B43)
private val Muted = Color(0xFF536477)
private val Background = Color(0xFFF3F6FA)
private fun noticeColor(kind: NoticeKind) = when (kind) {
    NoticeKind.SUCCESS -> Color(0xFF14643C)
    NoticeKind.WARNING -> Color(0xFF805300)
    NoticeKind.ERROR -> Red
    NoticeKind.INFO -> Blue
}

@Composable
fun DemoScreen(
    state: DemoState, onSingle: () -> Unit, onHome: () -> Unit,
    onBatch: () -> Unit, onBatchSource: () -> Unit, onBatchOutput: () -> Unit, onStartBatch: () -> Unit,
    onBatchCompany: (String, String) -> Unit, onBatchSave: (String) -> Unit,
    onBatchConfirm: () -> Unit, onBatchCancel: () -> Unit, onBatchPreview: (String) -> Unit,
    onFile: () -> Unit, onOutput: () -> Unit, onScan: () -> Unit,
    onChooseInputFolder: () -> Unit, onCancelInput: () -> Unit,
    onAnalyze: () -> Unit, onName: (String) -> Unit,
    onExport: (SaveConfirmation) -> Unit, onHistory: () -> Unit, onMoreHistory: () -> Unit, onDismissNotice: () -> Unit,
    onCheckFile: (SavedCopy) -> Unit,
    onImportCompanies: () -> Unit, onSelectCompanyColumn: (Int, Int) -> Unit,
    onConfirmCompanies: () -> Unit, onCancelCompanies: () -> Unit, onRemoveCompanies: () -> Unit,
    onView: () -> Unit, onPage: (Int, Boolean) -> Unit, onCloseViewer: () -> Unit,
) {
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var saveMessageVisible by rememberSaveable(state.notice.id) { mutableStateOf(state.notice.announce) }
    var held by remember(state.notice.id) { mutableStateOf(false) }
    val accessibility = LocalAccessibilityManager.current
    val timeout = accessibility?.calculateRecommendedTimeoutMillis(3000L, containsIcons = true, containsText = true, containsControls = true) ?: 3000L
    val messageAvailable = !state.viewer.open && !historyOpen && !aboutOpen
    LaunchedEffect(state.notice.id, saveMessageVisible, held, messageAvailable, timeout) {
        if (saveMessageVisible && !held && messageAvailable) { delay(timeout); saveMessageVisible = false }
    }
    fun action(next: NextAction, savedCopy: SavedCopy? = null) {
        when (next) {
            NextAction.OUTPUT -> onOutput()
            NextAction.SOURCE -> { onSingle(); onFile() }
            NextAction.SCAN -> onScan()
            NextAction.ANALYZE -> { onSingle(); onAnalyze() }
            NextAction.REVIEW -> { onSingle(); onView() }
            NextAction.HISTORY -> { historyOpen = true; onHistory() }
            NextAction.CHECK_FILE -> savedCopy?.let(onCheckFile)
            NextAction.NONE -> Unit
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = Red, background = Background, surface = Color.White, onSurface = Ink, onBackground = Ink)) {
        if (state.inputRequest?.step == InputStep.ASK_FOLDER) AlertDialog(
            onDismissRequest = onCancelInput,
            title = { Text("Where should renamed files be saved?") },
            text = { Text("Choose a folder. We’ll remember it.") },
            confirmButton = { TextButton(onClick = onChooseInputFolder) { Text("Choose folder") } },
            dismissButton = { TextButton(onClick = onCancelInput) { Text("Cancel") } },
        )
        if (aboutOpen) AlertDialog(
            onDismissRequest = { aboutOpen = false }, title = { Text("About this demo") },
            text = { Text("Documents are processed on this device. Imported originals stay unchanged. Unused private imports are cleaned up; private camera scans are removed after verified export. Use fictional or bank-approved test files.") },
            confirmButton = { TextButton(onClick = { aboutOpen = false }) { Text("Close") } },
        )
        state.batchConfirmId?.let { id ->
            val review = state.batchReviews[id]
            AlertDialog(onDismissRequest = onBatchCancel, title = { Text("Is the company name correct?") },
                text = { Text(review?.company.orEmpty()) },
                confirmButton = { TextButton(onClick = onBatchConfirm) { Text("Yes, save") } },
                dismissButton = { TextButton(onClick = onBatchCancel) { Text("Edit name") } })
        }
        if (historyOpen) AlertDialog(
            onDismissRequest = { historyOpen = false }, title = { Text("Export history") },
            text = { LazyColumn(Modifier.heightIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (NoticePolicy.shouldDisplayInline(state.notice.kind)) item {
                    InlineIssue(state.notice, !state.busy, onDismissNotice) { next -> historyOpen = false; action(next) }
                }
                if (!state.historyLoaded && state.busy) item { Text("Loading local records…") }
                else if (state.historyLoaded && state.history.isEmpty()) item { Text("No exports yet.") }
                ExportHistory.days(state.history).forEach { day ->
                    item(key = "day:${day.key}") {
                        HorizontalDivider()
                        Text(day.label, Modifier.padding(top = 10.dp), color = Blue, fontWeight = FontWeight.Bold)
                    }
                    items(day.items, key = { "export:${it.id}" }) { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.target, fontWeight = FontWeight.Medium)
                            Text("${entry.date} · ${entry.source}", style = MaterialTheme.typography.bodySmall, color = Muted)
                            if (!entry.completed) Text("Incomplete — check output", color = Red, style = MaterialTheme.typography.bodySmall)
                            entry.checkFileCopy?.let { copy ->
                                Text(copy.folder, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { historyOpen = false; onCheckFile(copy) }, enabled = !state.busy) { Text("Check file") }
                            }
                        }
                    }
                }
                if (state.historyHasMore) item { TextButton(onClick = onMoreHistory, enabled = !state.busy) { Text("More") } }
            } }, confirmButton = { TextButton(onClick = { historyOpen = false }) { Text("Close") } },
            dismissButton = { TextButton(onClick = onHistory, enabled = !state.busy) { Text("Refresh") } },
        )
        if (state.viewer.open) FullscreenViewer(state, onPage, onCloseViewer)
        Box(Modifier.fillMaxSize()) {
            Scaffold(containerColor = Background, topBar = {
                Surface(shadowElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.screen == AppScreen.SINGLE) IconButton(onClick = onHome, enabled = !state.busy, colors = IconButtonDefaults.iconButtonColors(contentColor = Blue)) {
                            House(Modifier.size(24.dp).semantics { contentDescription = "Home" })
                        }
                        Box(Modifier.weight(1f).padding(start = 4.dp)) {
                          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                           Column(Modifier.weight(1f)) {
                            val logoSize = with(LocalDensity.current) { MaterialTheme.typography.titleLarge.fontSize.toDp() }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AppLogo(Modifier.size(logoSize))
                                Text("SEC Rename", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text("Scan · Review · Save a copy", color = Muted, style = MaterialTheme.typography.labelSmall)
                           }
                           Text("DEMO", color = Red, style = MaterialTheme.typography.labelSmall)
                          }
                        }
                    }
                }
            }, bottomBar = {
                // Scaffold gives the body this bar's measured height, so feedback covers no controls.
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 16.dp)) {
                    val saved = state.notice.savedCopy
                    if (saveMessageVisible && messageAvailable && saved != null) Snackbar(
                        // Let Snackbar measure its action with loose width constraints.
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp)
                            .pointerInput(state.notice.id) {
                                awaitEachGesture { awaitFirstDown(requireUnconsumed = false); held = true; try { waitForUpOrCancellation() } finally { held = false } }
                            }.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                        shape = RoundedCornerShape(10.dp), containerColor = Ink, contentColor = Color.White,
                        action = {
                            TextButton(onClick = { saveMessageVisible = false; onCheckFile(saved) }, enabled = !state.busy,
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB9D9FF))) { Text("Check file") }
                        },
                    ) {
                        Text("✓ File saved", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { historyOpen = true; onHistory() }, enabled = !state.busy) { Text("Export history") }
                        TextButton(onClick = { aboutOpen = true }) { Text("About") }
                    }
                }
            }) { padding ->
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val wide = maxWidth >= 600.dp
                    val compact = maxHeight < if (wide) 220.dp else 340.dp
                    val minimumHeight = if (wide) 280.dp else when {
                        state.screen == AppScreen.HOME -> 280.dp
                        state.analyzed -> 620.dp
                        state.selectedId != null -> 380.dp
                        else -> 260.dp
                    }
                    val scrollContent = state.screen == AppScreen.BATCH || maxHeight < minimumHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)
                    val scrollState = rememberScrollState()
                    Column(Modifier.fillMaxSize().then(if (scrollContent) Modifier.verticalScroll(scrollState) else Modifier), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.busy) {
                            Text(state.operation.ifBlank { "Working locally…" }, color = Blue, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            if (state.progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        }
                        if (!state.busy && !(state.exportNeedsInspection && state.notice.action == NextAction.HISTORY) && NoticePolicy.shouldDisplayInline(state.notice.kind)) {
                            InlineIssue(state.notice, true, onDismissNotice) { action(it) }
                        }
                        if (state.screen == AppScreen.HOME) {
                            Text("How would you like to start?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Panel {
                                Button(onClick = onSingle, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.selectedId != null) "Continue Single Document" else "Single Document") }
                                if (!compact) Text("Camera, photo or file", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Panel {
                                OutlinedButton(onClick = onBatch, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Batch files") }
                            }
                        } else if (state.screen == AppScreen.BATCH) {
                            BatchScreen(
                                state, onBatchSource, onBatchOutput, onStartBatch,
                                onBatchCompany, onBatchSave, onBatchPreview,
                                onImportCompanies, onSelectCompanyColumn, onConfirmCompanies,
                                onCancelCompanies, onRemoveCompanies,
                            )
                        } else {
                            val file = state.files.find { it.id == state.selectedId }
                            val inputActions: @Composable () -> Unit = {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = onScan, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Scan with camera") }
                                    OutlinedButton(onClick = onFile, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Open file") }
                                }
                            }
                            val source: @Composable (Boolean) -> Unit = { includeActions ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutputLocation(state, onOutput)
                                    CompanyListControls(state.companyList, state.busy, onImportCompanies, onSelectCompanyColumn, onConfirmCompanies, onCancelCompanies, onRemoveCompanies)
                                    if (file != null) FileAttachment(file, enabled = !state.busy, onPreview = onView)
                                    if (file != null && state.duplicate == null) state.matchIssue?.let { issue ->
                                        Text(issue, color = noticeColor(NoticeKind.WARNING), style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                                    }
                                    if (includeActions) {
                                        inputActions()
                                    }
                                }
                            }
                            val review: @Composable () -> Unit = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    when {
                                        state.savedCopy != null || state.duplicate != null || state.exportNeedsInspection -> DocumentResult(state, { historyOpen = true; onHistory() }, onCheckFile)
                                        file == null -> Unit
                                        state.analyzed -> ReviewForm(state, onName, onExport, onAnalyze, compact)
                                        else -> {
                                            if (!state.outputReady) Text("Choose an output folder", color = Muted, style = MaterialTheme.typography.bodySmall)
                                            Button(onClick = onAnalyze, enabled = state.canAnalyze, modifier = Modifier.fillMaxWidth()) { Text("Read company name") }
                                        }
                                    }
                                }
                            }
                            if (wide) Row((if (scrollContent) Modifier else Modifier.weight(1f)).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Box(Modifier.weight(1f).then(if (scrollContent) Modifier else Modifier.fillMaxHeight())) { source(true) }
                                Box(Modifier.weight(1f)) { review() }
                            } else {
                                source(false)
                                review()
                                inputActions()
                                if (!scrollContent) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable private fun InlineIssue(notice: UserNotice, enabled: Boolean, onDismiss: () -> Unit, onAction: (NextAction) -> Unit) {
    var details by remember(notice.id) { mutableStateOf(false) }
    if (details) AlertDialog(
        onDismissRequest = { details = false }, title = { Text(notice.title) },
        text = { SelectionContainer { Text(notice.detail, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton(onClick = { details = false }) { Text("Close") } },
        dismissButton = {
            if (notice.action != NextAction.NONE) TextButton(onClick = { details = false; onAction(notice.action) }, enabled = enabled) { Text(notice.action.label) }
        },
    )
    Surface(color = noticeColor(notice.kind).copy(alpha = 0.07f), shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically) {
            Text(notice.title, Modifier.weight(1f), color = noticeColor(notice.kind), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { details = true }) { Text("Details") }
            TextButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "Dismiss message" }) { Text("×") }
        }
    }
}

@Composable private fun OutputLocation(state: DemoState, onOutput: () -> Unit) {
    var details by remember { mutableStateOf(false) }
    if (details) AlertDialog(
        onDismissRequest = { details = false }, title = { Text("Save copies to") },
        text = { SelectionContainer { Text(state.outputLabel) } },
        confirmButton = { TextButton(onClick = { details = false }) { Text("Close") } },
        dismissButton = { TextButton(onClick = { details = false; onOutput() }, enabled = !state.busy) { Text("Save copies to") } },
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClickLabel = "Output folder details") { details = true }.padding(vertical = 8.dp)) {
            Text(state.outputLabel, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onOutput, enabled = !state.busy) { Text("Save copies to") }
    }
}

@Composable private fun DocumentResult(state: DemoState, onHistory: () -> Unit, onCheckFile: (SavedCopy) -> Unit) {
    var details by remember(state.selectedId) { mutableStateOf(false) }
    val saved = state.savedCopy
    val duplicate = state.duplicate
    val (title, detail, kind) = when {
        saved != null -> Triple("File saved", "${saved.name} saved in ${saved.folder}", NoticeKind.SUCCESS)
        duplicate != null -> Triple("Document already renamed", "${duplicate.name}\nFolder: ${duplicate.folder}\nMatching file content was verified. Reading and saving are blocked.", NoticeKind.WARNING)
        else -> Triple("Check previous export", state.notice.takeIf { it.kind == NoticeKind.ERROR && it.action == NextAction.HISTORY }?.detail
            ?: "An earlier save was not confirmed. Inspect history and the output folder before repeating it.", NoticeKind.ERROR)
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false }, title = { Text(title) },
        text = { SelectionContainer { Text(detail) } },
        confirmButton = { TextButton(onClick = { details = false }) { Text("Close") } },
        dismissButton = {
            val copy = saved ?: duplicate
            if (copy != null && copy.folderUri.isNotBlank()) TextButton(onClick = { details = false; onCheckFile(copy) }, enabled = !state.busy) { Text("Check file") }
        },
    )
    Surface(color = noticeColor(kind).copy(alpha = 0.07f), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = noticeColor(kind), style = MaterialTheme.typography.titleSmall)
                if (saved != null) TextButton(onClick = { onCheckFile(saved) }, enabled = !state.busy) { Text("Check file") }
                else TextButton(onClick = { details = true }) { Text("Details") }
            }
            if (saved != null) SelectionContainer { Text(detail, Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
    }
    if (state.exportNeedsInspection) OutlinedButton(onClick = onHistory, enabled = !state.busy) { Text("Export history") }
}
@Composable private fun FileAttachment(file: DocumentItem, enabled: Boolean, onPreview: () -> Unit) {
    val type = when (file.mime) {
        "application/pdf" -> "PDF"
        "image/jpeg" -> "JPG"
        "image/png" -> "PNG"
        else -> file.name.substringAfterLast('.', "FILE").uppercase()
    }
    val image = file.mime.startsWith("image/") || (file.mime.isBlank() && type in setOf("JPG", "JPEG", "PNG"))
    val tint = if (image) Blue else Red
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(enabled = enabled, role = Role.Button, onClickLabel = "Preview document", onClick = onPreview),
        shape = shape, color = Background, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp).alpha(if (enabled) 1f else 0.38f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).background(tint.copy(alpha = 0.09f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
                AttachmentIcon(image, tint, Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(file.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(type, style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Canvas(Modifier.size(16.dp)) {
                val path = Path().apply { moveTo(size.width * 0.35f, size.height * 0.2f); lineTo(size.width * 0.7f, size.height * 0.5f); lineTo(size.width * 0.35f, size.height * 0.8f) }
                drawPath(path, Muted, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable private fun AttachmentIcon(image: Boolean, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        val sx = size.width / 24f; val sy = size.height / 24f
        val stroke = Stroke(1.6f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (image) {
            drawRect(tint, Offset(3 * sx, 4 * sy), Size(18 * sx, 16 * sy), style = stroke)
            drawCircle(tint, 1.6f * sx, Offset(16 * sx, 9 * sy))
            val mountains = Path().apply { moveTo(4 * sx, 18 * sy); lineTo(9 * sx, 12 * sy); lineTo(13 * sx, 16 * sy); lineTo(16 * sx, 13 * sy); lineTo(20 * sx, 18 * sy) }
            drawPath(mountains, tint, style = stroke)
        } else {
            val page = Path().apply {
                moveTo(5 * sx, 2 * sy); lineTo(14 * sx, 2 * sy); lineTo(20 * sx, 8 * sy); lineTo(20 * sx, 22 * sy); lineTo(5 * sx, 22 * sy); close()
                moveTo(14 * sx, 2 * sy); lineTo(14 * sx, 8 * sy); lineTo(20 * sx, 8 * sy)
                moveTo(8 * sx, 13 * sy); lineTo(17 * sx, 13 * sy)
                moveTo(8 * sx, 17 * sy); lineTo(14 * sx, 17 * sy)
            }
            drawPath(page, tint, style = stroke)
        }
    }
}
@Composable private fun House(modifier: Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier) {
        val sx = size.width / 24f; val sy = size.height / 24f
        val path = Path().apply {
            moveTo(3 * sx, 10 * sy); lineTo(12 * sx, 3 * sy); lineTo(21 * sx, 10 * sy)
            moveTo(5 * sx, 9 * sy); lineTo(5 * sx, 21 * sy); lineTo(10 * sx, 21 * sy)
            lineTo(10 * sx, 14 * sy); lineTo(14 * sx, 14 * sy); lineTo(14 * sx, 21 * sy)
            lineTo(19 * sx, 21 * sy); lineTo(19 * sx, 9 * sy)
        }
        drawPath(path, color, style = Stroke(1.8f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Shared rendering of the existing Android app_icon.xml mark; decorative beside the title. */
@Composable private fun AppLogo(modifier: Modifier) {
    Canvas(modifier) {
        val sx = size.width / 108f; val sy = size.height / 108f
        drawRect(Blue)
        val document = Path().apply {
            moveTo(30 * sx, 20 * sy); lineTo(63 * sx, 20 * sy); lineTo(78 * sx, 35 * sy)
            lineTo(78 * sx, 88 * sy); lineTo(30 * sx, 88 * sy); close()
        }
        drawPath(document, Color.White)
        drawRect(Red, Offset(30 * sx, 20 * sy), Size(7 * sx, 68 * sy))
        drawRect(Blue, Offset(45 * sx, 47 * sy), Size(24 * sx, 4 * sy))
        drawRect(Blue, Offset(45 * sx, 58 * sy), Size(24 * sx, 4 * sy))
        drawRect(Blue, Offset(45 * sx, 69 * sy), Size(17 * sx, 4 * sy))
    }
}
@Composable private fun ReviewForm(state: DemoState, onName: (String) -> Unit, onExport: (SaveConfirmation) -> Unit, onAnalyze: () -> Unit, compact: Boolean) {
    var evidenceOpen by remember(state.selectedId) { mutableStateOf(false) }
    var candidatesOpen by remember(state.selectedId) { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    // Consent stays in memory and expires when the reviewed content changes.
    var pending by remember(state.selectedId, state.reviewRevision, state.outputId) { mutableStateOf<SaveConfirmation?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var editRequest by remember { mutableStateOf(0) }
    var field by remember(state.selectedId) { mutableStateOf(TextFieldValue(state.company)) }
    LaunchedEffect(state.company) { if (field.text != state.company) field = TextFieldValue(state.company, TextRange(state.company.length)) }
    LaunchedEffect(editRequest) {
        if (editRequest > 0) {
            field = TextFieldValue(state.company, TextRange(state.company.length))
            // Allow the dialog window to release focus before opening the input method.
            delay(100)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val confirmation = pending
    if (confirmation != null) AlertDialog(
        onDismissRequest = { pending = null }, title = { Text("Is the company name correct?") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(confirmation.company, fontWeight = FontWeight.Bold)
                Text("File name", style = MaterialTheme.typography.labelSmall, color = Muted)
                Text(confirmation.filename)
                Text("Save copies to", style = MaterialTheme.typography.labelSmall, color = Muted)
                Text(state.outputLabel, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { pending = null; onExport(confirmation) }, enabled = state.acceptsConfirmation(confirmation)) { Text("Yes, save") } },
        dismissButton = { TextButton(onClick = { pending = null; editRequest++ }) { Text("Edit name") } },
    )
    if (evidenceOpen) AlertDialog(
        onDismissRequest = { evidenceOpen = false }, title = { Text("OCR evidence") },
        text = { SelectionContainer { Text(state.evidence.ifBlank { "No readable text. Check the document and enter the company name manually." }, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = { evidenceOpen = false }) { Text("Close") } },
    )
    if (candidatesOpen) AlertDialog(
        onDismissRequest = { candidatesOpen = false }, title = { Text("Possible company names") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            state.candidates.forEach { candidate -> TextButton(onClick = { candidatesOpen = false; onName(candidate) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(candidate) } }
        } },
        confirmButton = { TextButton(onClick = { candidatesOpen = false }) { Text("Close") } },
    )
    OutlinedTextField(
        value = field, onValueChange = { field = it; if (it.text != state.company) onName(it.text) },
        enabled = !state.busy, singleLine = true, isError = state.nameError != null,
        label = { Text("Company name") },
        supportingText = state.nameError?.let { error -> { Text(error) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focusManager.clearFocus() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
    if (!compact) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("File name", color = Muted, style = MaterialTheme.typography.labelSmall)
                Text(state.proposal.ifBlank { "Enter a company name" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box {
                TextButton(onClick = { optionsOpen = true }, enabled = !state.busy) { Text("More") }
                DropdownMenu(expanded = optionsOpen, onDismissRequest = { optionsOpen = false }) {
                    if (state.candidates.size > 1) DropdownMenuItem(text = { Text("Possible names (${state.candidates.size})") }, onClick = { optionsOpen = false; candidatesOpen = true })
                    DropdownMenuItem(text = { Text("OCR evidence") }, onClick = { optionsOpen = false; evidenceOpen = true })
                    DropdownMenuItem(text = { Text("Read again") }, enabled = state.canAnalyze, onClick = { optionsOpen = false; onAnalyze() })
                }
            }
        }
    }
    Button(onClick = { keyboard?.hide(); focusManager.clearFocus(); pending = state.saveConfirmation() }, enabled = state.canExport, modifier = Modifier.fillMaxWidth()) { Text("Save renamed copy") }
}
@Composable private fun FullscreenViewer(state: DemoState, onPage: (Int, Boolean) -> Unit, onClose: () -> Unit) {
    val viewer = state.viewer
    var reset by remember { mutableStateOf(0) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF17202B)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.files.find { it.id == state.selectedId }?.name ?: "Document", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = onClose) { Text("Close", color = Color.White) }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        viewer.busy -> CircularProgressIndicator(color = Color.White)
                        viewer.error != null -> Column(Modifier.padding(24.dp)) {
                            Text(viewer.error, color = Color.White)
                            TextButton(onClick = { onPage(viewer.page, false) }) { Text("Retry at normal detail", color = Color.White) }
                        }
                        viewer.image != null -> key(state.selectedId, viewer.page, viewer.highDetail, reset) {
                            ZoomImage(painter = BitmapPainter(viewer.image), contentDescription = "Document page ${viewer.page + 1} of ${viewer.pages}", modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onPage(viewer.page - 1, false) }, enabled = !viewer.busy && viewer.page > 0) { Text("Previous", color = if (viewer.page > 0) Color.White else Color.Gray) }
                    Text("${viewer.page + 1} / ${viewer.pages}", color = Color.White)
                    TextButton(onClick = { onPage(viewer.page + 1, false) }, enabled = !viewer.busy && viewer.page + 1 < viewer.pages) { Text("Next", color = if (viewer.page + 1 < viewer.pages) Color.White else Color.Gray) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { reset++ }, enabled = !viewer.busy) { Text("Reset zoom", color = Color.White) }
                    TextButton(onClick = { onPage(viewer.page, !viewer.highDetail) }, enabled = !viewer.busy) { Text(if (viewer.highDetail) "Normal detail" else "Higher detail", color = Color.White) }
                }
            }
        }
    }
}
