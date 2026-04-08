package au.roman.bloodpressuretracker

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import au.roman.bloodpressuretracker.data.BloodPressureDao
import au.roman.bloodpressuretracker.data.BloodPressureRecord
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed interface ExplanationState {
    data object Idle : ExplanationState
    data object Loading : ExplanationState
    data class Success(val text: String) : ExplanationState
    data class Error(val message: String) : ExplanationState
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
private val csvDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val reportDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val reportTimeFormatter = DateTimeFormatter.ofPattern("h:mma")
private val averagesDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

private fun exportCsv(context: Context, records: List<BloodPressureRecord>) {
    val csv = buildString {
        appendLine("Date/Time,Systolic (mmHg),Diastolic (mmHg),Pulse (bpm)")
        for (record in records.reversed()) {
            val dateTime = Instant.ofEpochMilli(record.timestamp)
                .atZone(ZoneId.systemDefault())
                .format(csvDateFormatter)
            val pulse = if (record.pulse > 0) record.pulse.toString() else "NA"
            appendLine("$dateTime,${record.systolic},${record.diastolic},$pulse")
        }
    }

    val file = File(context.cacheDir, "blood_pressure_export.csv")
    file.writeText(csv)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export Blood Pressure Data"))
}

private fun exportGroupedCsv(context: Context, records: List<BloodPressureRecord>) {
    val zone = ZoneId.systemDefault()

    fun formatRecordString(record: BloodPressureRecord): String {
        val time = Instant.ofEpochMilli(record.timestamp).atZone(zone).format(reportTimeFormatter).padStart(7)
        val sys = record.systolic.toString().padStart(3)
        val dia = record.diastolic.toString().padStart(2)
        val pulse = if (record.pulse > 0) " / ${record.pulse}" else ""
        return "$time $sys / $dia$pulse"
    }

    val csv = buildString {
        val grouped = records.reversed().groupBy { record ->
            Instant.ofEpochMilli(record.timestamp).atZone(zone).toLocalDate()
        }

        for ((date, dayRecords) in grouped) {
            val dateStr = date.format(reportDateFormatter)
            val chunks = dayRecords.chunked(3)
            for (chunk in chunks) {
                val col1 = if (chunk.isNotEmpty()) "\"${formatRecordString(chunk[0])}\"" else ""
                val col2 = if (chunk.size > 1) "\"${formatRecordString(chunk[1])}\"" else ""
                val col3 = if (chunk.size > 2) "\"${formatRecordString(chunk[2])}\"" else ""
                appendLine("$dateStr,$col1,$col2,$col3")
            }
        }
    }

    val file = File(context.cacheDir, "blood_pressure_report.csv")
    file.writeText(csv)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export Blood Pressure Report"))
}

private fun exportAveragesCsv(context: Context, records: List<BloodPressureRecord>) {
    val zone = ZoneId.systemDefault()
    val csv = buildString {
        appendLine("Date,Systolic (mmHg),Diastolic (mmHg),Pulse (bpm)")
        val grouped = records.reversed().groupBy { record ->
            Instant.ofEpochMilli(record.timestamp).atZone(zone).toLocalDate()
        }
        for ((date, dayRecords) in grouped) {
            val avgSys = dayRecords.map { it.systolic }.average()
            val avgDia = dayRecords.map { it.diastolic }.average()
            val pulseRecords = dayRecords.filter { it.pulse > 0 }
            val avgPulse = if (pulseRecords.isNotEmpty()) pulseRecords.map { it.pulse }.average() else null
            val pulseStr = if (avgPulse != null) "%.1f".format(avgPulse) else "NA"
            appendLine("${date.format(averagesDateFormatter)},${"%.1f".format(avgSys)},${"%.1f".format(avgDia)},$pulseStr")
        }
    }

    val file = File(context.cacheDir, "blood_pressure_averages.csv")
    file.writeText(csv)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export Blood Pressure Averages"))
}

private fun parseCsv(inputStream: InputStream): List<BloodPressureRecord> {
    val records = mutableListOf<BloodPressureRecord>()
    val lines = inputStream.bufferedReader().readLines()
    for (line in lines.drop(1)) { // skip header
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        val parts = trimmed.split(",")
        if (parts.size < 3) continue
        val dateTime = LocalDateTime.parse(parts[0].trim(), csvDateFormatter)
        val timestamp = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val systolic = parts[1].trim().toIntOrNull() ?: continue
        val diastolic = parts[2].trim().toIntOrNull() ?: continue
        val pulse = if (parts.size > 3) parts[3].trim().toIntOrNull() ?: 0 else 0
        records.add(BloodPressureRecord(systolic = systolic, diastolic = diastolic, pulse = pulse, timestamp = timestamp))
    }
    return records
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(dao: BloodPressureDao, modifier: Modifier = Modifier) {
    val records by dao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var recordToDelete by remember { mutableStateOf<BloodPressureRecord?>(null) }
    var selectedRecord by remember { mutableStateOf<BloodPressureRecord?>(null) }
    var explanationRecord by remember { mutableStateOf<BloodPressureRecord?>(null) }
    var explanationState by remember { mutableStateOf<ExplanationState>(ExplanationState.Idle) }
    var importUri by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            importUri = uri
        }
    }

    if (importUri != null) {
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("Import CSV") },
            text = { Text("This will replace all existing records with the imported data. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = importUri!!
                    importUri = null
                    scope.launch {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val parsed = parseCsv(inputStream)
                            inputStream.close()
                            if (parsed.isNotEmpty()) {
                                dao.replaceAll(parsed)
                            }
                        }
                    }
                }) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { importUri = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bottom sheet for card actions
    if (selectedRecord != null) {
        val record = selectedRecord!!
        ModalBottomSheet(
            onDismissRequest = { selectedRecord = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = "${record.systolic} / ${record.diastolic} mmHg",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Explain this reading") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val r = selectedRecord
                        selectedRecord = null
                        explanationRecord = r
                    }
                )
                ListItem(
                    headlineContent = { Text("Delete") },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val r = selectedRecord
                        selectedRecord = null
                        recordToDelete = r
                    }
                )
            }
        }
    }

    // Explanation API call
    LaunchedEffect(explanationRecord) {
        val record = explanationRecord ?: return@LaunchedEffect
        val apiKey = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("anthropic_api_key", null)
        if (apiKey.isNullOrBlank()) {
            explanationState = ExplanationState.Error("No API key set.\nTap the gear icon on the Record screen to add your Anthropic API key.")
            return@LaunchedEffect
        }
        explanationState = ExplanationState.Loading
        val result = getBloodPressureExplanation(apiKey, record.systolic, record.diastolic, record.pulse)
        explanationState = result.fold(
            onSuccess = { ExplanationState.Success(it) },
            onFailure = { ExplanationState.Error(it.message ?: "Unknown error") }
        )
    }

    // Explanation dialog
    if (explanationState !is ExplanationState.Idle) {
        AlertDialog(
            onDismissRequest = {
                explanationState = ExplanationState.Idle
                explanationRecord = null
            },
            title = {
                Text(
                    when (explanationState) {
                        is ExplanationState.Loading -> "Analysing..."
                        is ExplanationState.Success -> "Reading Explanation"
                        is ExplanationState.Error -> "Error"
                        else -> ""
                    }
                )
            },
            text = {
                when (val state = explanationState) {
                    is ExplanationState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is ExplanationState.Success -> {
                        Text(
                            text = state.text,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                    is ExplanationState.Error -> {
                        Text(text = state.message)
                    }
                    else -> {}
                }
            },
            confirmButton = {
                if (explanationState !is ExplanationState.Loading) {
                    TextButton(onClick = {
                        explanationState = ExplanationState.Idle
                        explanationRecord = null
                    }) { Text("OK") }
                }
            },
            dismissButton = {
                if (explanationState is ExplanationState.Error) {
                    TextButton(onClick = {
                        // Re-trigger by setting a new instance
                        val record = explanationRecord
                        explanationRecord = null
                        explanationState = ExplanationState.Idle
                        explanationRecord = record
                    }) { Text("Retry") }
                }
            }
        )
    }

    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Delete Record") },
            text = { Text("Are you sure you want to delete this reading?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        dao.delete(recordToDelete!!)
                        recordToDelete = null
                    }
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (records.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No records yet",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = { filePickerLauncher.launch("text/*") }) {
                    Text("Import", color = Color.Red)
                }
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(records) { record ->
                    Card(
                        onClick = { selectedRecord = record },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = Instant.ofEpochMilli(record.timestamp)
                                    .atZone(ZoneId.systemDefault())
                                    .format(dateFormatter),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "${record.systolic} / ${record.diastolic} mmHg",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = if (record.pulse > 0) "Pulse: ${record.pulse} bpm" else "Pulse: NA",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { exportCsv(context, records) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export")
                }
                Button(
                    onClick = { exportGroupedCsv(context, records) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Report")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { exportAveragesCsv(context, records) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Daily Averages")
                }
                Button(
                    onClick = { filePickerLauncher.launch("text/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import", color = Color.Red)
                }
            }
        }
    }
}
