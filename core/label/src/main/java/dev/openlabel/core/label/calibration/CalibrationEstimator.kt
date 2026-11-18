package dev.openlabel.core.label.calibration

import dev.openlabel.core.label.model.MediaType
import kotlin.math.abs

/** A vendor/firmware adapter supplies encoder pulse counts; Android cannot read a generic printer sensor directly. */
data class CalibrationSample(
    val labelPulses: Int,
    val gapPulses: Int,
    val pulsesPerMm: Float,
) {
    init {
        require(labelPulses > 0 && gapPulses >= 0)
        require(pulsesPerMm.isFinite() && pulsesPerMm > 0f)
    }
}

data class CalibrationResult(
    val labelLengthMm: Float,
    val gapLengthMm: Float,
    val mediaType: MediaType,
    val confidence: Float,
)

object CalibrationEstimator {
    fun estimate(samples: List<CalibrationSample>, mediaType: MediaType = MediaType.GAP): CalibrationResult {
        require(samples.size >= 3) { "Для калибровки нужно не менее трёх измерений" }
        val labelValues = samples.map { it.labelPulses / it.pulsesPerMm }.sorted()
        val gapValues = samples.map { it.gapPulses / it.pulsesPerMm }.sorted()
        val labelMedian = median(labelValues)
        val gapMedian = median(gapValues)
        val deviations = labelValues.map { abs(it - labelMedian) }
        val mad = median(deviations.sorted())
        val confidence = (1f - mad / labelMedian.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return CalibrationResult(labelMedian, if (mediaType == MediaType.CONTINUOUS) 0f else gapMedian, mediaType, confidence)
    }

    private fun median(values: List<Float>): Float = if (values.size % 2 == 1) {
        values[values.size / 2]
    } else {
        (values[values.size / 2 - 1] + values[values.size / 2]) / 2f
    }
}

fun calibrationErrorMm(actualMm: Float, calculatedMm: Float): Float = abs(actualMm - calculatedMm)
