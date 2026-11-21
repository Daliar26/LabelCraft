package dev.openlabel.core.printer.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

@Singleton
class PrinterRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val discovery = BluetoothDiscovery(context)
    @Volatile private var activeTransport: PrinterTransport? = null
    @Volatile var connectedDevice: PrinterDevice? = null
        private set

    suspend fun scan(): List<PrinterDevice> = discovery.scan()

    suspend fun connect(device: PrinterDevice) {
        disconnect()
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: error("На устройстве нет Bluetooth")
        val bluetoothDevice = adapter.getRemoteDevice(device.address)
        if (device.kind == TransportKind.CLASSIC_SPP) ensurePaired(bluetoothDevice)
        val transport = when (device.kind) {
            TransportKind.CLASSIC_SPP -> ClassicSppTransport(bluetoothDevice)
            TransportKind.BLE -> BleGattTransport(context, bluetoothDevice)
        }
        var lastFailure: Throwable? = null
        repeat(3) { attempt ->
            try {
                transport.connect()
                activeTransport = transport
                connectedDevice = device
                return
            } catch (error: Throwable) {
                lastFailure = error
                transport.close()
                if (attempt < 2) delay(500L * (1L shl attempt))
            }
        }
        throw IllegalStateException("Не удалось подключиться к ${device.name}", lastFailure)
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private suspend fun ensurePaired(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        val completion = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val changed = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
                }
                if (changed?.address != device.address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDED -> if (!completion.isCompleted) completion.complete(Unit)
                    BluetoothDevice.BOND_NONE -> if (!completion.isCompleted) completion.completeExceptionally(IllegalStateException("Сопряжение Bluetooth не выполнено"))
                }
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        try {
            if (!device.createBond() && device.bondState != BluetoothDevice.BOND_BONDED) {
                error("Не удалось начать Bluetooth-сопряжение")
            }
            withTimeout(45_000) { completion.await() }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    suspend fun send(data: ByteArray) = deliver { it.send(data) }

    suspend fun sendFrames(frames: List<ByteArray>) = deliver { it.sendFrames(frames) }

    /** Runs one transmission on the active transport; on a dropped link it reconnects and retries once. */
    private suspend fun deliver(block: suspend (PrinterTransport) -> Unit) {
        checkNotNull(connectedDevice) { "Сначала подключите принтер" }
        if (activeTransport?.isConnected() != true) reconnect()
        try {
            block(checkNotNull(activeTransport))
        } catch (error: Exception) {
            if (error is CancellationException || (error !is IOException && error !is IllegalStateException)) throw error
            // BluetoothSocket.isConnected may remain true after the remote peer has vanished.
            // Retry once after rebuilding the transport; a partial first write can be device-dependent.
            reconnect()
            try {
                block(checkNotNull(activeTransport))
            } catch (retryError: Throwable) {
                retryError.addSuppressed(error)
                throw retryError
            }
        }
    }

    suspend fun readAvailable(): ByteArray = activeTransport?.readAvailable() ?: byteArrayOf()

    suspend fun reconnect() {
        val device = checkNotNull(connectedDevice) { "Нет выбранного принтера для реконнекта" }
        connect(device)
    }

    fun isConnected(): Boolean = activeTransport?.isConnected() == true

    fun disconnect() {
        runCatching { activeTransport?.close() }
        activeTransport = null
        connectedDevice = null
    }
}
