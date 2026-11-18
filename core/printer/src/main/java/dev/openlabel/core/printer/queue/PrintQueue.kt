package dev.openlabel.core.printer.queue

import dev.openlabel.core.label.model.LabelDocument
import dev.openlabel.core.label.render.LabelRasterizer
import dev.openlabel.core.label.render.MonochromeDither
import dev.openlabel.core.printer.protocol.PrinterCommandEncoder
import dev.openlabel.core.printer.protocol.PrinterProtocol
import dev.openlabel.core.printer.transport.PrinterRepository
import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID


@Serializable
enum class PrintJobStatus { QUEUED, PRINTING, COMPLETED, FAILED }

@Serializable
data class PrintJob(
    val id: String = UUID.randomUUID().toString(),
    val document: LabelDocument,
    val protocol: PrinterProtocol,
    val copies: Int = 1,
    val attempts: Int = 0,
    val status: PrintJobStatus = PrintJobStatus.QUEUED,
    val error: String? = null,
) {
    init { require(copies in 1..9_999) }
}

class PrintQueueManager(
    private val printers: PrinterRepository,
    private val rasterizer: LabelRasterizer = LabelRasterizer(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    context: Context? = null,
) {
    private val preferences = context?.applicationContext?.getSharedPreferences("print-queue", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "elementType" }
    private val channel = Channel<String>(Channel.UNLIMITED)
    private val mutableJobs = MutableStateFlow(restoreJobs())
    val jobs: StateFlow<List<PrintJob>> = mutableJobs.asStateFlow()

    init {
        scope.launch {
            for (id in channel) process(id)
        }
        mutableJobs.value.filter { it.status == PrintJobStatus.QUEUED }.forEach { channel.trySend(it.id) }
    }

    fun enqueue(document: LabelDocument, protocol: PrinterProtocol, copies: Int = 1): String {
        val job = PrintJob(document = document, protocol = protocol, copies = copies)
        mutableJobs.value = mutableJobs.value + job
        persist()
        channel.trySend(job.id)
        return job.id
    }

    fun retry(jobId: String) {
        val job = mutableJobs.value.firstOrNull { it.id == jobId } ?: return
        if (job.status != PrintJobStatus.FAILED) return
        mutableJobs.value = mutableJobs.value.map { if (it.id == jobId) it.copy(status = PrintJobStatus.QUEUED, error = null) else it }
        persist()
        channel.trySend(jobId)
    }

    private suspend fun process(id: String) {
        val job = mutableJobs.value.firstOrNull { it.id == id } ?: return
        mutableJobs.value = mutableJobs.value.map { if (it.id == id) it.copy(status = PrintJobStatus.PRINTING) else it }
        persist()
        try {
            val bitmap = rasterizer.render(job.document)
            val raster = try { MonochromeDither.convert(bitmap) } finally { bitmap.recycle() }
            val profile = dev.openlabel.core.label.model.LabelProfile(
                name = job.document.name,
                widthMm = job.document.widthMm,
                heightMm = job.document.heightMm,
                dpi = job.document.dpi,
                mediaType = job.document.mediaType,
                gapMm = job.document.gapMm,
            )
            printers.send(PrinterCommandEncoder.printRaster(job.protocol, raster, profile, job.copies))
            mutableJobs.value = mutableJobs.value.map { if (it.id == id) it.copy(status = PrintJobStatus.COMPLETED, attempts = it.attempts + 1) else it }
            persist()
        } catch (error: Throwable) {
            mutableJobs.value = mutableJobs.value.map {
                if (it.id == id) it.copy(status = PrintJobStatus.FAILED, attempts = it.attempts + 1, error = error.message ?: "Ошибка печати") else it
            }
            persist()
        }
    }

    private fun restoreJobs(): List<PrintJob> {
        val raw = preferences?.getString("jobs", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<PrintJob>>(raw).map {
                if (it.status == PrintJobStatus.PRINTING) it.copy(status = PrintJobStatus.QUEUED) else it
            }
        }.getOrDefault(emptyList())
    }

    private fun persist() {
        val prefs = preferences ?: return
        runCatching { prefs.edit().putString("jobs", json.encodeToString(mutableJobs.value)).apply() }
    }
}
