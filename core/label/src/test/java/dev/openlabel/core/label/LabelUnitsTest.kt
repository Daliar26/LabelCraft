package dev.openlabel.core.label

import dev.openlabel.core.label.model.PrinterDpi
import dev.openlabel.core.label.model.dotsToMm
import dev.openlabel.core.label.model.mmToDots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LabelUnitsTest {
    @Test fun tenMmAt203DpiIsEightyDots() = assertEquals(80, mmToDots(10f, PrinterDpi.DPI_203))
    @Test fun tenMmAt300DpiIs118Dots() = assertEquals(118, mmToDots(10f, PrinterDpi.DPI_300))
    @Test fun oneInchAt203DpiIs203Dots() = assertEquals(203, mmToDots(25.4f, PrinterDpi.DPI_203))
    @Test fun oneInchAt300DpiIs300Dots() = assertEquals(300, mmToDots(25.4f, PrinterDpi.DPI_300))
    @Test fun zeroMmIsZeroDots() = assertEquals(0, mmToDots(0f, PrinterDpi.DPI_203))
    @Test fun dotsConvertBackToMm() = assertEquals(25.4f, dotsToMm(300, PrinterDpi.DPI_300), 0.0001f)
    @Test fun negativeMmIsRejected() = assertThrows(IllegalArgumentException::class.java) { mmToDots(-1f, PrinterDpi.DPI_203) }
    @Test fun infiniteMmIsRejected() = assertThrows(IllegalArgumentException::class.java) { mmToDots(Float.POSITIVE_INFINITY, PrinterDpi.DPI_203) }
    @Test fun negativeDotsAreRejected() = assertThrows(IllegalArgumentException::class.java) { dotsToMm(-1, PrinterDpi.DPI_203) }
}
