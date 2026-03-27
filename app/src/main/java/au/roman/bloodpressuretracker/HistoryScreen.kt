package au.roman.bloodpressuretracker

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import au.roman.bloodpressuretracker.data.BloodPressureDao
import au.roman.bloodpressuretracker.data.BloodPressureRecord
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
private val csvDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val reportDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val reportTimeFormatter = DateTimeFormatter.ofPattern("h:mma")

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

@Composable
fun HistoryScreen(dao: BloodPressureDao, modifier: Modifier = Modifier) {
    val records by dao.getAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var recordToDelete by remember { mutableStateOf<BloodPressureRecord?>(null) }

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
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No records yet",
                style = MaterialTheme.typography.bodyLarge
            )
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
                        onClick = { recordToDelete = record },
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
        }
    }
}
