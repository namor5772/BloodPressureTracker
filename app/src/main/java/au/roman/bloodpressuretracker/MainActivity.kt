package au.roman.bloodpressuretracker

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import au.roman.bloodpressuretracker.data.AppDatabase
import au.roman.bloodpressuretracker.data.BloodPressureDao
import au.roman.bloodpressuretracker.data.BloodPressureRecord
import au.roman.bloodpressuretracker.ui.theme.BloodPressureTrackerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BloodPressureTrackerTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.bloodPressureDao() }
    val snackbarHostState = remember { SnackbarHostState() }
    var currentScreen by remember { mutableStateOf(Screen.Record) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == Screen.Record,
                    onClick = { currentScreen = Screen.Record },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Record") },
                    label = { Text("Record") }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.History,
                    onClick = { currentScreen = Screen.History },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "History") },
                    label = { Text("History") }
                )
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            Screen.Record -> BloodPressureScreen(
                dao = dao,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.padding(innerPadding)
            )
            Screen.History -> HistoryScreen(
                dao = dao,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@Composable
fun BloodPressureScreen(
    dao: BloodPressureDao,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var systolicText by remember { mutableStateOf("") }
    var diastolicText by remember { mutableStateOf("") }
    var pulseText by remember { mutableStateOf("") }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    val systolic = systolicText.toIntOrNull()
    val diastolic = diastolicText.toIntOrNull()
    val pulse = pulseText.toIntOrNull()
    val isValid = systolic != null && diastolic != null && (pulseText.isEmpty() || pulse != null)

    if (showApiKeyDialog) {
        var keyText by remember {
            mutableStateOf(
                context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getString("anthropic_api_key", "") ?: ""
            )
        }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Anthropic API Key") },
            text = {
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit().putString("anthropic_api_key", keyText.trim()).apply()
                    showApiKeyDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Record Blood Pressure",
                fontSize = 28.sp
            )
            IconButton(onClick = { showApiKeyDialog = true }) {
                Icon(Icons.Default.Settings, contentDescription = "API Key Settings")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = systolicText,
            onValueChange = { systolicText = it.filter { c -> c.isDigit() } },
            label = { Text("Systolic (mmHg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = diastolicText,
            onValueChange = { diastolicText = it.filter { c -> c.isDigit() } },
            label = { Text("Diastolic (mmHg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = pulseText,
            onValueChange = { pulseText = it.filter { c -> c.isDigit() } },
            label = { Text("Pulse (bpm)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isValid) {
                    val record = BloodPressureRecord(
                        systolic = systolic!!,
                        diastolic = diastolic!!,
                        pulse = pulse ?: 0,
                        timestamp = System.currentTimeMillis()
                    )
                    scope.launch {
                        dao.insert(record)
                        systolicText = ""
                        diastolicText = ""
                        pulseText = ""
                        focusManager.clearFocus()
                        snackbarHostState.showSnackbar("Reading saved")
                    }
                }
            },
            enabled = isValid
        ) {
            Text("Save")
        }

        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(0.15f)
        ) {
            Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.height(96.dp).fillMaxWidth()
            )
            Text(
                text = "Stay Healthy",
                fontSize = 14.sp,
                color = Color.Red
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
