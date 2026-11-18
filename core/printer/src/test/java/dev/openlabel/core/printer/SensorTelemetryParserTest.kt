package dev.openlabel.core.printer

import dev.openlabel.core.printer.calibration.SensorTelemetryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorTelemetryParserTest {
    @Test fun parsesPulseCountsAndScale() {
        val sample = SensorTelemetryParser.parse("LABEL_PULSES=400;GAP_PULSES=24;PULSES_PER_MM=8".toByteArray())
        assertEquals(400, sample?.labelPulses)
        assertEquals(24, sample?.gapPulses)
        assertEquals(8f, sample?.pulsesPerMm)
    }

    @Test fun rejectsUnrecognizedPrinterStatus() {
        assertNull(SensorTelemetryParser.parse("READY;PAPER OK".toByteArray()))
    }
}
