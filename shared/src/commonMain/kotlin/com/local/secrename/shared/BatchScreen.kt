package com.local.secrename.shared

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun BatchScreen(
    state: DemoState,
    onBatchSource: () -> Unit,
    onBatchOutput: () -> Unit,
    onStartBatch: () -> Unit,
    onBatchCompany: (String, String) -> Unit,
    onBatchSave: (String) -> Unit,
    onBatchPreview: (String) -> Unit,
    onImportCompanies: () -> Unit,
    onSelectCompanyColumn: (Int, Int) -> Unit,
    onConfirmCompanies: () -> Unit,
    onCancelCompanies: () -> Unit,
    onRemoveCompanies: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(state.batchSourceLabel, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onBatchSource, enabled = !state.busy) { Text("Choose source folder") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(state.batchOutputLabel, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onBatchOutput, enabled = !state.busy) { Text("Save renamed files to") }
        }
        CompanyListControls(
            state.companyList, state.busy, onImportCompanies, onSelectCompanyColumn,
            onConfirmCompanies, onCancelCompanies, onRemoveCompanies,
        )
        Button(onClick = onStartBatch, enabled = !state.busy && state.batchQueue != null, modifier = Modifier.fillMaxWidth()) {
            Text("Start batch rename")
        }
        if (state.batchStarted) state.batchQueue?.let { queue ->
            Text(
                if (queue.total == 0) "No supported documents in this folder" else "${queue.finishedCount} of ${queue.total} complete",
                style = MaterialTheme.typography.bodySmall,
            )
            queue.pending.take(10).forEach { file ->
                val review = state.batchReviews[file.id] ?: BatchReview()
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = Color.White) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { onBatchPreview(file.id) }, enabled = !review.analyzing && !state.busy,
                                contentPadding = PaddingValues(0.dp),
                            ) { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            if (review.analyzing) LinearProgressIndicator(Modifier.fillMaxWidth())
                            else OutlinedTextField(
                                value = review.company, onValueChange = { onBatchCompany(file.id, it) },
                                label = { Text("Company name") }, singleLine = true, enabled = !review.saving,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            review.issue?.let {
                                Text(it, color = Color(0xFFB51F32), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Button(
                            onClick = { onBatchSave(file.id) },
                            enabled = !review.analyzing && !review.saving && review.company.isNotBlank(),
                        ) { Text(if (review.saving) "Saving" else "Save") }
                    }
                }
            }
        }
    }
}
