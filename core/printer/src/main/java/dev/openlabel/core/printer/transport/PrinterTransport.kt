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
private val X6H_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000AE01-0000-1000-8000-00805F9B34FB")
private const val BLE_DEFAULT_CHUNK = 20

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
    /** Sends independent frames. Transports that can keep frames intact should override this. */
    suspend fun sendFrames(frames: List<ByteArray>) {
        send(frames.fold(ByteArray(0)) { acc, frame -> acc + frame })
    }
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

/** BLE write transport. Prefers the AE01 characteristic of the X6H family, otherwise the first writable characteristic. */
class BleGattTransport(context: Context, private val device: BluetoothDevice) : PrinterTransport {
    private val appContext = context.applicationContext
    @Volatile private var gatt: android.bluetooth.BluetoothGatt? = null
    @Volatile private var writeCharacteristic: android.bluetooth.BluetoothGattCharacteristic? = null
    private var connection = CompletableDeferred<Unit>()
    private var servicesReady = CompletableDeferred<Unit>()
    @Volatile private var lastStatus: Int = android.bluetooth.BluetoothGatt.GATT_SUCCESS
    @Volatile private var writeFinished = CompletableDeferred<Int>()
    @Volatile private var maxPayload = BLE_DEFAULT_CHUNK

    private val callback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(g: android.bluetooth.BluetoothGatt, status: Int, newState: Int) {
            lastStatus = status
            if (status != android.bluetooth.BluetoothGatt.GATT_SUCCESS) {
                if (!connection.isCompleted) connection.completeExceptionally(IllegalStateException("BLE connection error: $status"))
                if (!writeFinished.isCompleted) writeFinished.complete(android.bluetooth.BluetoothGatt.GATT_FAILURE)
                return
            }
            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                if (!connection.isCompleted) connection.complete(Unit)
                g.discoverServices()
            } else if (newState == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
                writeCharacteristic = null
                lastStatus = android.bluetooth.BluetoothGatt.GATT_FAILURE
                if (!writeFinished.isCompleted) writeFinished.complete(android.bluetooth.BluetoothGatt.GATT_FAILURE)
            }
        }

        override fun onMtuChanged(g: android.bluetooth.BluetoothGatt, mtu: Int, status: Int) {
            if (status == android.bluetooth.BluetoothGatt.GATT_SUCCESS) maxPayload = (mtu - 3).coerceAtLeast(BLE_DEFAULT_CHUNK)
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
            val allCharacteristics = g.services.asSequence().flatMap { it.characteristics.asSequence() }.toList()
            fun isWritable(c: android.bluetooth.BluetoothGattCharacteristic) =
                c.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                    c.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
            writeCharacteristic = allCharacteristics.firstOrNull { it.uuid == X6H_CHARACTERISTIC_UUID && isWritable(it) }
                ?: allCharacteristics.firstOrNull { isWritable(it) }
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

    override suspend fun send(data: ByteArray) = writeChunks(data, BLE_DEFAULT_CHUNK)

    /** Each frame goes out in as few ATT writes as the negotiated MTU allows, so frames are not split across writes. */
    override suspend fun sendFrames(frames: List<ByteArray>) {
        frames.forEach { frame -> writeChunks(frame, maxPayload) }
    }

    private suspend fun writeChunks(data: ByteArray, chunkSize: Int) {
        val activeGatt = checkNotNull(gatt) { "BLE-принтер не подключён" }
        val characteristic = checkNotNull(writeCharacteristic) { "BLE write characteristic не найден" }
        val writeType = if (characteristic.properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
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
