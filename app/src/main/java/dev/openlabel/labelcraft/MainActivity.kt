package dev.openlabel.labelcraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.openlabel.core.printer.queue.PrintJobStatus
import dev.openlabel.core.printer.protocol.PrinterProtocol
import dev.openlabel.feature.devices.DevicesScreen
import dev.openlabel.feature.devices.CalibrationScreen
import dev.openlabel.feature.editor.LabelEditorScreen
import dev.openlabel.feature.templates.TemplatesScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { LabelCraftNavigation() }
            }
        }
    }
}

@Composable
private fun LabelCraftNavigation(viewModel: MainViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val document by viewModel.document.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val protocol by viewModel.protocol.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val connecting by viewModel.connecting.collectAsState()
    val connectedAddress by viewModel.connectedAddress.collectAsState()
    val error by viewModel.error.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val printerProfile by viewModel.printerProfile.collectAsState()

    NavHost(navController = nav, startDestination = "editor") {
        composable("editor") {
            LabelEditorScreen(
                document = document,
                deviceAddress = connectedAddress,
                onDocumentChanged = viewModel::updateDocument,
                onPrint = { item, count -> viewModel.print(item, count) },
                onDevices = { nav.navigate("devices") },
                onTemplates = { nav.navigate("templates") },
                onQueue = { nav.navigate("queue") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("devices") {
            DevicesScreen(
                devices = devices,
                connectedAddress = connectedAddress,
                scanning = scanning || connecting,
                error = error,
                onScan = viewModel::scanDevices,
                onConnect = viewModel::connect,
                onCalibration = { nav.navigate("calibration") },
                onBack = { nav.popBackStack() },
            )
        }
        composable("templates") {
            TemplatesScreen(
                document = document,
                onLoad = viewModel::updateDocument,
                onBatchPrint = viewModel::printBatch,
                onBack = { nav.popBackStack() },
            )
        }
        composable("calibration") {
            CalibrationScreen(
                initialProfile = printerProfile,
                connected = connectedAddress != null,
                message = error,
                onAutomatic = viewModel::runAutomaticCalibration,
                onSaveManual = viewModel::saveManualProfile,
                onBack = { nav.popBackStack() },
            )
        }
        composable("queue") {
            PrintQueueScreen(jobs = jobs, onBack = { nav.popBackStack() }, onRetry = viewModel::retry)
        }
        composable("settings") {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Протокол печати", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = viewModel::detectProtocol, modifier = Modifier.fillMaxWidth()) { Text("Определить по ответу принтера") }
                PrinterProtocol.values().filter { it != PrinterProtocol.AUTO }.forEach { item ->
                    Button(onClick = { viewModel.chooseProtocol(item) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (protocol == item) "✓ ${item.name}" else item.name)
                    }
                }
                Button(onClick = { nav.popBackStack() }) { Text("Назад") }
            }
        }
    }
}

@Composable
private fun PrintQueueScreen(
    jobs: List<dev.openlabel.core.printer.queue.PrintJob>,
    onBack: () -> Unit,
    onRetry: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Назад") }
            Text("Очередь печати", style = MaterialTheme.typography.headlineSmall)
        }
        if (jobs.isEmpty()) Text("Очередь пуста")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(jobs, key = { it.id }) { job ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(job.document.name, style = MaterialTheme.typography.titleMedium)
                        Text("${job.document.widthMm} × ${job.document.heightMm} мм • ${job.copies} коп. • ${job.protocol.name}")
                        Text("Статус: ${when (job.status) {
                            PrintJobStatus.QUEUED -> "В очереди"
                            PrintJobStatus.PRINTING -> "Печать"
                            PrintJobStatus.COMPLETED -> "Готово"
                            PrintJobStatus.FAILED -> "Ошибка"
                        }}")
                        job.error?.let { Text(it) }
                        if (job.status == PrintJobStatus.FAILED) Button(onClick = { onRetry(job.id) }) { Text("Повторить") }
                    }
                }
            }
        }
    }
}
