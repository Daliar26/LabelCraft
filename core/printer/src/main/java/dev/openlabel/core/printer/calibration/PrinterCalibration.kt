package dev.openlabel.core.printer.calibration

import dev.openlabel.core.label.calibration.CalibrationEstimator
import dev.openlabel.core.label.calibration.CalibrationResult
import dev.openlabel.core.label.calibration.CalibrationSample
import dev.openlabel.core.label.model.LabelProfile
import dev.openlabel.core.printer.protocol.PrinterCommandEncoder
import dev.openlabel.core.printer.protocol.PrinterProtocol
import dev.openlabel.core.printer.transport.PrinterRepository
import kotlinx.coroutines.delay

/** Sensor telemetry format used by supported firmware adapters. */
object SensorTelemetryParser {
    private val expression = Regex("LABEL_PULSES=(\\d+);GAP_PULSES=(\\d+);PULSES_PER_MM=([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)

    fun parse(data: ByteArray): CalibrationSample? {
        val match = expression.find(data.toString(Charsets.US_ASCII)) ?: return null
        return runCatching {
            CalibrationSample(
                labelPulses = match.groupValues[1].toInt(),
                gapPulses = match.groupValues[2].toInt(),
                pulsesPerMm = match.groupValues[3].toFloat(),
            )
        }.getOrNull()
    }
}

class PrinterCalibration(private val printers: PrinterRepository) {
    suspend fun tryAutomatic(protocol: PrinterProtocol, profile: LabelProfile): CalibrationResult? {
        val command = PrinterCommandEncoder.calibrationCommand(protocol, profile) ?: return null
        printers.send(command)
        // A device/firmware adapter must expose three independent encoder/sensor readings.
        val samples = mutableListOf<dev.openlabel.core.label.calibration.CalibrationSample>()
        repeat(12) {
            delay(250)
            SensorTelemetryParser.parse(printers.readAvailable())?.let(samples::add)
            if (samples.size >= 3) return CalibrationEstimator.estimate(samples.take(5))
        }
        return null
    }

    fun estimateFromMeasurements(samples: List<CalibrationSample>) = CalibrationEstimator.estimate(samples)
}
