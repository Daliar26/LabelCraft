package dev.openlabel.core.printer.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.util.UUID

private val SERIAL_PORT_PROFILE: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

enum class TransportKind { CLASSIC_SPP, BLE }

data class PrinterDevice(
    val name: String,
    val address: String,
    val kind: TransportKind,
    val bonded: Boolean = false,
    val rssi: Int? = null,
)

interface PrinterTransport : Closeable {
    suspend fun connect()
    suspend fun send(data: ByteArray)
    suspend fun readAvailable(): ByteArray
    fun isConnected(): Boolean
}

class ClassicSppTransport(private val device: BluetoothDevice) : PrinterTransport {
    @Volatile private var socket: BluetoothSocket? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (isConnected()) return@withContext
        val candidate = device.createRfcommSocketToServiceRecord(SERIAL_PORT_PROFILE)
        try {
            candidate.connect()
            socket = candidate
        } catch (error: Throwable) {
            runCatching { candidate.close() }
            throw error
        }
    }

    override suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        val active = checkNotNull(socket) { "SPP-принтер не подключён" }
        check(active.isConnected) { "SPP-соединение закрыто" }
        active.outputStream.write(data)
        active.outputStream.flush()
    }

    override suspend fun readAvailable(): ByteArray = withContext(Dispatchers.IO) {
        val input = socket?.inputStream ?: return@withContext byteArrayOf()
        val available = input.available().coerceAtMost(4096)
        if (available == 0) return@withContext byteArrayOf()
        ByteArray(available).also { input.read(it) }
    }

    override fun isConnected(): Boolean = socket?.isConnected == true

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }
}

/** BLE write transport. The first writable GATT characteristic is used; vendor-specific services may need a profile adapter. */
class BleGattTransport(context: Context, private val device: BluetoothDevice) : PrinterTransport {
    private val appContext = context.applicationContext
    @Volatile private var gatt: android.bluetooth.BluetoothGatt? = null
    @Volatile private var writeCharacteristic: android.bluetooth.BluetoothGattCharacteristic? = null
    private var connection = CompletableDeferred<Unit>()
    private var servicesReady = CompletableDeferred<Unit>()
    @Volatile private var lastStatus: Int = android.bluetooth.BluetoothGatt.GATT_SUCCESS
    @Volatile private var writeFinished = CompletableDeferred<Int>()

    private val callback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(g: android.bluetooth.BluetoothGatt, status: Int, newState: Int) {
            lastStatus = status
            if (status != android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                if (!connection.isCompleted) connection.completeExceptionally(IllegalStateException("BLE connection error: $status"))
                return
            }
            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                if (!connection.isCompleted) connection.complete(Unit)
                g.discoverServices()
            } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                writeCharacteristic = null
            }
        }

        override fun onCharacteristicWrite(
            g: android.bluetooth.BluetoothGatt,
            characteristic: android.bluetooth.BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (!writeFinished.isCompleted) writeFinished.complete(status)
        }

        override fun onServicesDiscovered(g: android.bluetooth.BluetoothGatt, status: Int) {
            lastStatus = status
            if (status != android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                if (!servicesReady.isCompleted) servicesReady.completeExceptionally(IllegalStateException("BLE service discovery error: $status"))
                return
            }
            writeCharacteristic = g.services.asSequence().flatMap { it.characteristics.asSequence() }
                .firstOrNull { characteristic ->
                    characteristic.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                        characteristic.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                }
            val found = writeCharacteristic
            if (found == null) {
                if (!servicesReady.isCompleted) servicesReady.completeExceptionally(IllegalStateException("BLE printer has no writable characteristic"))
            } else if (!servicesReady.isCompleted) servicesReady.complete(Unit)
        }
    }

    override suspend fun connect() {
        if (isConnected()) return
        connection = CompletableDeferred()
        servicesReady = CompletableDeferred()
        gatt = withContext(Dispatchers.Main) { device.connectGatt(appContext, false, callback) }
        withTimeout(18_000) { connection.await() }
        withTimeout(18_000) { servicesReady.await() }
        gatt?.requestMtu(247)
    }

    override suspend fun send(data: ByteArray) {
        val activeGatt = checkNotNull(gatt) { "BLE-принтер не подключён" }
        val characteristic = checkNotNull(writeCharacteristic) { "BLE write characteristic не найден" }
        val writeType = if (characteristic.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        val chunkSize = 20
        data.asList().chunked(chunkSize).forEach { chunk ->
            val bytes = chunk.toByteArray()
            if (writeType == android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                writeFinished = CompletableDeferred()
            }
            @Suppress("DEPRECATION")
            val accepted = if (android.os.Build.VERSION.SDK_INT >= 33) {
                activeGatt.writeCharacteristic(characteristic, bytes, writeType) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                characteristic.writeType = writeType
                characteristic.value = bytes
                activeGatt.writeCharacteristic(characteristic)
            }
            check(accepted) { "BLE GATT отказал в отправке блока" }
            if (writeType == android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) {
                val status = withTimeout(5_000) { writeFinished.await() }
                check(status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) { "BLE GATT write error: $status" }
            } else {
                delay(30)
            }
        }
    }

    override suspend fun readAvailable(): ByteArray = byteArrayOf() // Status notifications are vendor-service specific.
    override fun isConnected(): Boolean = gatt != null && writeCharacteristic != null && lastStatus == android.bluetooth.BluetoothGatt.GATT_SUCCESS
    override fun close() {
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
        writeCharacteristic = null
        lastStatus = android.bluetooth.BluetoothGatt.GATT_FAILURE
    }
}
