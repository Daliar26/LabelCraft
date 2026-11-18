package dev.openlabel.labelcraft

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.openlabel.core.label.model.LabelDocument
import dev.openlabel.core.label.model.LabelProfile
import dev.openlabel.core.label.model.PrinterDpi
import dev.openlabel.core.printer.calibration.PrinterCalibration
import dev.openlabel.core.printer.calibration.PrinterProfileStore
import dev.openlabel.core.printer.protocol.PrinterProtocol
import dev.openlabel.core.printer.protocol.ProtocolDetection
import dev.openlabel.core.printer.protocol.ProtocolDetector
import dev.openlabel.core.printer.queue.PrintJob
import dev.openlabel.core.printer.queue.PrintQueueManager
import dev.openlabel.core.printer.transport.PrinterDevice
import dev.openlabel.core.printer.transport.PrinterRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    private val printers: PrinterRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val queue = PrintQueueManager(printers = printers, context = context)
    private val profileStore = PrinterProfileStore(context)
    private val calibration = PrinterCalibration(printers)
    private val mutableDocument = MutableStateFlow(LabelDocument())
    val document: StateFlow<LabelDocument> = mutableDocument.asStateFlow()
    private val mutableDevices = MutableStateFlow<List<PrinterDevice>>(emptyList())
    val devices: StateFlow<List<PrinterDevice>> = mutableDevices.asStateFlow()
    private val mutableProtocol = MutableStateFlow(PrinterProtocol.TSPL2)
    private val mutableProfile = MutableStateFlow(LabelProfile("Default", 58f, 40f, PrinterDpi.DPI_203))
    val printerProfile: StateFlow<LabelProfile> = mutableProfile.asStateFlow()
    val protocol: StateFlow<PrinterProtocol> = mutableProtocol.asStateFlow()
    private val mutableScanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = mutableScanning.asStateFlow()
    private val mutableConnecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = mutableConnecting.asStateFlow()
    private val mutableConnectedAddress = MutableStateFlow<String?>(null)
    val connectedAddress: StateFlow<String?> = mutableConnectedAddress.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()
    val jobs: StateFlow<List<PrintJob>> = queue.jobs

    fun updateDocument(document: LabelDocument) { mutableDocument.value = document }
    fun chooseProtocol(protocol: PrinterProtocol) { mutableProtocol.value = protocol }

    fun scanDevices() = viewModelScope.launch {
        mutableScanning.value = true
        mutableError.value = null
        runCatching { printers.scan() }
            .onSuccess { mutableDevices.value = it }
            .onFailure { mutableError.value = it.message ?: "Ошибка поиска Bluetooth" }
        mutableScanning.value = false
    }

    fun connect(device: PrinterDevice) = viewModelScope.launch {
        mutableConnecting.value = true
        mutableError.value = null
        runCatching { printers.connect(device) }
            .onSuccess {
                mutableConnectedAddress.value = device.address
                val saved = profileStore.load(device.address)
                if (saved != null) {
                    mutableProfile.value = saved
                    mutableDocument.value = mutableDocument.value.copy(
                        widthMm = saved.widthMm,
                        heightMm = saved.heightMm,
                        dpi = saved.dpi,
                        mediaType = saved.mediaType,
                        gapMm = saved.gapMm,
                    )
                } else {
                    profileStore.save(device.address, mutableProfile.value)
                }
            }
            .onFailure { mutableError.value = it.message ?: "Ошибка подключения" }
        mutableConnecting.value = false
    }

    fun print(document: LabelDocument = mutableDocument.value, copies: Int = 1) {
        if (mutableProtocol.value == PrinterProtocol.AUTO) {
            mutableError.value = "Не удалось определить протокол автоматически. Выберите протокол вручную."
            return
        }
        queue.enqueue(document, mutableProtocol.value, copies)
    }

    fun detectProtocol() = viewModelScope.launch {
        mutableError.value = null
        runCatching { printers.readAvailable() }
            .onSuccess { bytes ->
                when (val detected = ProtocolDetector.detect(bytes)) {
                    is ProtocolDetection.Detected -> {
                        mutableProtocol.value = detected.protocol
                        mutableError.value = "Обнаружен протокол ${detected.protocol.name}"
                    }
                    ProtocolDetection.Unknown -> mutableError.value = "Принтер не сообщил сигнатуру протокола. Выберите TSPL2 / ESC-POS / CPCL / ZPL вручную."
                }
            }
            .onFailure { mutableError.value = it.message ?: "Не удалось прочитать ответ принтера" }
    }

    fun saveManualProfile(profile: LabelProfile) {
        mutableProfile.value = profile
        mutableDocument.value = mutableDocument.value.copy(
            widthMm = profile.widthMm,
            heightMm = profile.heightMm,
            dpi = profile.dpi,
            mediaType = profile.mediaType,
            gapMm = profile.gapMm,
        )
        profileStore.save(mutableConnectedAddress.value ?: "default", profile)
        mutableError.value = "Профиль сохранён локально"
    }

    fun runAutomaticCalibration() = viewModelScope.launch {
        mutableError.value = null
        val profile = mutableProfile.value
        runCatching { calibration.tryAutomatic(mutableProtocol.value, profile) }
            .onSuccess { result ->
                if (result == null) {
                    mutableError.value = "Принтер не вернул телеметрию датчика. Используйте ручной ввод длины и зазора."
                } else {
                    val updated = profile.copy(heightMm = result.labelLengthMm, gapMm = result.gapLengthMm)
                    saveManualProfile(updated)
                    mutableError.value = "Измерено ${"%.1f".format(result.labelLengthMm)} мм; уверенность ${"%.0f".format(result.confidence * 100)}%"
                }
            }
            .onFailure { mutableError.value = it.message ?: "Ошибка автокалибровки" }
    }

    fun printBatch(documents: List<LabelDocument>) {
        if (mutableProtocol.value == PrinterProtocol.AUTO) {
            mutableError.value = "Для пакетной печати выберите протокол принтера вручную."
            return
        }
        documents.forEach { queue.enqueue(it, mutableProtocol.value) }
    }

    fun retry(jobId: String) = queue.retry(jobId)

    override fun onCleared() {
        super.onCleared()
        // The Bluetooth socket is released with its connection owner; queued jobs remain persisted.
    }
}
