package dev.openlabel.core.label

import dev.openlabel.core.label.calibration.CalibrationEstimator
import dev.openlabel.core.label.calibration.CalibrationSample
import dev.openlabel.core.label.calibration.calibrationErrorMm
import dev.openlabel.core.label.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class CalibrationEstimatorTest {
    @Test fun knownSensorSetMeasuresLengthWithinOneMillimeter() {
        val samples = listOf(400, 401, 399, 400, 402).map { CalibrationSample(it, 24, 8f) }
        val result = CalibrationEstimator.estimate(samples)
        assertTrue(calibrationErrorMm(50f, result.labelLengthMm) <= 1f)
        assertEquals(3f, result.gapLengthMm, 0.01f)
    }
    @Test fun evenNumberOfSamplesUsesMedianAverage() {
        val result = CalibrationEstimator.estimate(listOf(
            CalibrationSample(400, 16, 8f), CalibrationSample(408, 16, 8f),
            CalibrationSample(392, 16, 8f), CalibrationSample(400, 16, 8f),
        ))
        assertEquals(50f, result.labelLengthMm, 0.01f)
    }
    @Test fun continuousMediaAlwaysHasZeroGap() {
        val result = CalibrationEstimator.estimate(List(3) { CalibrationSample(400, 40, 8f) }, MediaType.CONTINUOUS)
        assertEquals(0f, result.gapLengthMm, 0f)
    }
    @Test fun calibrationNeedsAtLeastThreeMeasurements() {
        assertThrows(IllegalArgumentException::class.java) { CalibrationEstimator.estimate(listOf(CalibrationSample(400, 24, 8f))) }
    }
    @Test fun sampleRejectsZeroPulsesPerMm() {
        assertThrows(IllegalArgumentException::class.java) { CalibrationSample(400, 20, 0f) }
    }
    @Test fun confidenceStaysInUnitInterval() {
        val result = CalibrationEstimator.estimate(listOf(390, 400, 410).map { CalibrationSample(it, 20, 8f) })
        assertTrue(result.confidence in 0f..1f)
    }
}
