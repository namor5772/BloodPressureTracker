package au.roman.bloodpressuretracker

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import au.roman.bloodpressuretracker.data.BloodPressureDao
import au.roman.bloodpressuretracker.data.DailyAverage
import au.roman.bloodpressuretracker.data.computeDailyAverages
import java.time.format.DateTimeFormatter

private val averagesDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyAveragesScreen(
    dao: BloodPressureDao,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val records by dao.getAll().collectAsState(initial = emptyList())
    val dailyAverages = remember(records) { computeDailyAverages(records) }
    val context = LocalContext.current

    var selectedAverage by remember { mutableStateOf<DailyAverage?>(null) }
    var explanationAverage by remember { mutableStateOf<DailyAverage?>(null) }
    var explanationState by remember { mutableStateOf<ExplanationState>(ExplanationState.Idle) }

    // Bottom sheet for actions
    if (selectedAverage != null) {
        val avg = selectedAverage!!
        ModalBottomSheet(
            onDismissRequest = { selectedAverage = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = "${"%.1f".format(avg.avgSystolic)} / ${"%.1f".format(avg.avgDiastolic)} mmHg" +
                        if (avg.avgPulse != null) ", ${"%.1f".format(avg.avgPulse)} bpm" else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Explain this daily average") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val a = selectedAverage
                        selectedAverage = null
                        explanationAverage = a
                    }
                )
            }
        }
    }

    // Explanation API call
    LaunchedEffect(explanationAverage) {
        val avg = explanationAverage ?: return@LaunchedEffect
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("anthropic_api_key", null)
        if (apiKey.isNullOrBlank()) {
            explanationState = ExplanationState.Error("No API key set.\nTap the gear icon on the Record screen to add your Anthropic API key.")
            return@LaunchedEffect
        }
        val customInstructions = prefs.getString("custom_instructions", "") ?: ""
        explanationState = ExplanationState.Loading
        val result = getDailyAverageExplanation(apiKey, avg.avgSystolic, avg.avgDiastolic, avg.avgPulse, customInstructions)
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
                explanationAverage = null
            },
            title = {
                Text(
                    when (explanationState) {
                        is ExplanationState.Loading -> "Analysing..."
                        is ExplanationState.Success -> "Daily Average Explanation"
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
                        explanationAverage = null
                    }) { Text("OK") }
                }
            },
            dismissButton = {
                if (explanationState is ExplanationState.Error) {
                    TextButton(onClick = {
                        val avg = explanationAverage
                        explanationAverage = null
                        explanationState = ExplanationState.Idle
                        explanationAverage = avg
                    }) { Text("Retry") }
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Daily Averages") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        if (dailyAverages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No records yet",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dailyAverages) { avg ->
                    Card(
                        onClick = { selectedAverage = avg },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = avg.date.format(averagesDateFormatter),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "(${avg.recordCount} reading${if (avg.recordCount != 1) "s" else ""})",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            Text(
                                text = "${"%.1f".format(avg.avgSystolic)} / ${"%.1f".format(avg.avgDiastolic)} mmHg" +
                                    if (avg.avgPulse != null) ", ${"%.1f".format(avg.avgPulse)} bpm" else "",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
