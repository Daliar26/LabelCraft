package dev.openlabel.core.printer.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class BluetoothDiscovery(private val context: Context) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    @SuppressLint("MissingPermission")
    suspend fun scan(durationMs: Long = 9_000): List<PrinterDevice> = withContext(Dispatchers.Main) {
        val bluetooth = adapter ?: return@withContext emptyList()
        val found = linkedMapOf<String, PrinterDevice>()
        runCatching {
            bluetooth.bondedDevices.orEmpty().forEach { device ->
                found[device.address] = device.toPrinter(TransportKind.CLASSIC_SPP, bonded = true)
            }
        }
        val classicReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_FOUND) return
                val device = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (device != null) found[device.address] = device.toPrinter(TransportKind.CLASSIC_SPP)
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        runCatching {
            registerReceiverCompat(classicReceiver, filter)
            bluetooth.startDiscovery()
        }

        val scanner = bluetooth.bluetoothLeScanner
        val bleCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                found[device.address] = device.toPrinter(TransportKind.BLE, rssi = result.rssi)
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(0, it) }
            }
        }
        runCatching { scanner?.startScan(bleCallback) }
        delay(durationMs)
        runCatching { scanner?.stopScan(bleCallback) }
        runCatching { bluetooth.cancelDiscovery() }
        runCatching { context.unregisterReceiver(classicReceiver) }
        found.values.sortedByDescending { it.bonded }
    }

    @Suppress("DEPRECATION")
    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toPrinter(kind: TransportKind, bonded: Boolean = false, rssi: Int? = null) =
        PrinterDevice(name = name ?: "Bluetooth printer", address = address, kind = kind, bonded = bonded, rssi = rssi)
}
