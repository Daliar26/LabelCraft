package dev.openlabel.feature.devices

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.openlabel.core.printer.transport.PrinterDevice
import dev.openlabel.core.printer.transport.TransportKind

@Composable
fun DevicesScreen(
    devices: List<PrinterDevice>,
    connectedAddress: String?,
    scanning: Boolean,
    error: String?,
    onScan: () -> Unit,
    onConnect: (PrinterDevice) -> Unit,
    onCalibration: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) onScan()
    }
    fun requestScan() {
        val permissions = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter { context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) onScan() else permissionLauncher.launch(missing.toTypedArray())
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Назад") }
            Button(onClick = ::requestScan, enabled = !scanning) { Text(if (scanning) "Поиск…" else "Найти принтеры") }
            Button(onClick = onCalibration) { Text("Калибровка") }
        }
        Text("Bluetooth-принтеры", style = MaterialTheme.typography.headlineSmall)
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        if (devices.isEmpty()) Text("Устройства появятся здесь после сканирования.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.address + it.kind.name }) { device ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text("${device.address} • ${if (device.kind == TransportKind.BLE) "BLE" else "Bluetooth Classic SPP"}")
                        Text(if (device.bonded) "Сопряжено" else "Не сопряжено")
                        Button(onClick = { onConnect(device) }, enabled = connectedAddress != device.address) {
                            Text(if (connectedAddress == device.address) "Подключено" else "Подключить")
                        }
                    }
                }
            }
        }
    }
}
