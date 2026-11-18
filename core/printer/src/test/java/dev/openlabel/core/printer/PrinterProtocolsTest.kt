package dev.openlabel.core.printer

import dev.openlabel.core.label.model.LabelProfile
import dev.openlabel.core.label.model.MediaType
import dev.openlabel.core.label.model.PrinterDpi
import dev.openlabel.core.label.render.MonochromeRaster
import dev.openlabel.core.printer.protocol.PrinterCommandEncoder
import dev.openlabel.core.printer.protocol.ProtocolDetector
import dev.openlabel.core.printer.protocol.PrinterProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterProtocolsTest {
    private val profile = LabelProfile("sample", 58f, 40f, PrinterDpi.DPI_203)
    private val raster = MonochromeRaster(8, 2, 1, byteArrayOf(0xff.toByte(), 0x00))

    @Test fun detectsTsplSignature() = assertEquals(PrinterProtocol.TSPL2, (ProtocolDetector.detect("TSC TSPL".toByteArray()) as dev.openlabel.core.printer.protocol.ProtocolDetection.Detected).protocol)
    @Test fun detectsCpclSignature() = assertEquals(PrinterProtocol.CPCL, (ProtocolDetector.detect("CPCL".toByteArray()) as dev.openlabel.core.printer.protocol.ProtocolDetection.Detected).protocol)
    @Test fun unknownSignatureIsUnknown() = assertTrue(ProtocolDetector.detect(byteArrayOf()) is dev.openlabel.core.printer.protocol.ProtocolDetection.Unknown)
    @Test fun tsplPayloadContainsRasterAndPrintCommand() {
        val result = PrinterCommandEncoder.printRaster(PrinterProtocol.TSPL2, raster, profile)
        assertTrue(result.toString(Charsets.ISO_8859_1).contains("BITMAP 0,0,1,2,0,"))
        assertTrue(result.toString(Charsets.ISO_8859_1).contains("PRINT 1,1"))
    }
    @Test fun escPosRasterHeaderIsPresent() {
        val result = PrinterCommandEncoder.printRaster(PrinterProtocol.ESC_POS, raster, profile)
        assertEquals(0x1d.toByte(), result[2])
        assertEquals(0x76.toByte(), result[3])
    }
    @Test fun zplCalibrationCommandMatchesFirmwareCommand() {
        assertEquals("~JC", PrinterCommandEncoder.calibrationCommand(PrinterProtocol.ZPL, profile)!!.toString(Charsets.US_ASCII))
    }

    @Test fun tsplBlackMarkUsesBlineCommand() {
        val blackMark = profile.copy(mediaType = MediaType.BLACK_MARK, gapMm = 3.5f)
        val payload = PrinterCommandEncoder.printRaster(PrinterProtocol.TSPL2, raster, blackMark)
        assertTrue(payload.toString(Charsets.ISO_8859_1).contains("BLINE 3.5 mm,0 mm"))
    }
}
