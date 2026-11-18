package dev.openlabel.core.printer.protocol

import kotlinx.serialization.Serializable

import dev.openlabel.core.label.model.LabelProfile
import dev.openlabel.core.label.render.MonochromeRaster

sealed interface ProtocolDetection {
    data class Detected(val protocol: PrinterProtocol) : ProtocolDetection
    data object Unknown : ProtocolDetection
}

@Serializable
enum class PrinterProtocol { AUTO, TSPL2, ESC_POS, CPCL, ZPL }

object ProtocolDetector {
    /** Detection is based on a response/capability signature. A silent printer cannot be identified safely. */
    fun detect(response: ByteArray): ProtocolDetection {
        val text = response.toString(Charsets.ISO_8859_1).uppercase()
        val protocol = when {
            "TSPL" in text || "TSC" in text -> PrinterProtocol.TSPL2
            "ZPL" in text || "ZEBRA" in text -> PrinterProtocol.ZPL
            "CPCL" in text -> PrinterProtocol.CPCL
            "ESC/POS" in text || "EPSON" in text -> PrinterProtocol.ESC_POS
            else -> null
        }
        return protocol?.let(ProtocolDetection::Detected) ?: ProtocolDetection.Unknown
    }
}

object PrinterCommandEncoder {
    fun calibrationCommand(protocol: PrinterProtocol, profile: LabelProfile): ByteArray? = when (protocol) {
        PrinterProtocol.TSPL2 -> when (profile.mediaType) {
            dev.openlabel.core.label.model.MediaType.GAP -> "GAPDETECT 0,0\r\n".toByteArray(Charsets.US_ASCII)
            dev.openlabel.core.label.model.MediaType.BLACK_MARK -> "BLINEDETECT 0,0\r\n".toByteArray(Charsets.US_ASCII)
            dev.openlabel.core.label.model.MediaType.CONTINUOUS -> null
        }
        PrinterProtocol.ZPL -> if (profile.mediaType == dev.openlabel.core.label.model.MediaType.CONTINUOUS) null
            else "~JC".toByteArray(Charsets.US_ASCII)
        PrinterProtocol.CPCL, PrinterProtocol.ESC_POS, PrinterProtocol.AUTO -> null
    }

    fun printRaster(protocol: PrinterProtocol, raster: MonochromeRaster, profile: LabelProfile, copies: Int = 1): ByteArray {
        require(copies in 1..9_999)
        return when (protocol) {
            PrinterProtocol.TSPL2 -> tspl(raster, profile, copies)
            PrinterProtocol.ESC_POS -> escPos(raster, copies)
            PrinterProtocol.CPCL -> cpcl(raster, profile, copies)
            PrinterProtocol.ZPL -> zpl(raster, copies)
            PrinterProtocol.AUTO -> error("Выберите протокол: автоматическое определение не удалось")
        }
    }

    private fun tspl(raster: MonochromeRaster, profile: LabelProfile, copies: Int): ByteArray = buildByteArray {
        appendAscii("SIZE ${profile.widthMm} mm,${profile.heightMm} mm\r\n")
        when (profile.mediaType) {
            dev.openlabel.core.label.model.MediaType.GAP -> appendAscii("GAP ${profile.gapMm} mm,0 mm\r\n")
            dev.openlabel.core.label.model.MediaType.BLACK_MARK -> appendAscii("BLINE ${profile.gapMm} mm,0 mm\r\n")
            dev.openlabel.core.label.model.MediaType.CONTINUOUS -> appendAscii("GAP 0 mm,0 mm\r\n")
        }
        appendAscii("CLS\r\n")
        appendAscii("BITMAP 0,0,${raster.bytesPerRow},${raster.height},0,")
        append(raster.data)
        appendAscii("\r\nPRINT 1,$copies\r\n")
    }

    private fun escPos(raster: MonochromeRaster, copies: Int): ByteArray = buildByteArray {
        repeat(copies) {
            append(byteArrayOf(0x1b, 0x40))
            append(byteArrayOf(0x1d, 0x76, 0x30, 0x00))
            appendLittleEndian(raster.bytesPerRow)
            appendLittleEndian(raster.height)
            append(raster.data)
            append(byteArrayOf(0x0a))
        }
    }

    private fun cpcl(raster: MonochromeRaster, profile: LabelProfile, copies: Int): ByteArray = buildByteArray {
        appendAscii("! 0 200 200 ${profile.heightMm.toInt()} $copies\r\n")
        appendAscii("EG ${raster.bytesPerRow} ${raster.height} 0 0 ")
        append(raster.data)
        appendAscii("\r\nFORM\r\nPRINT\r\n")
    }


    private fun zpl(raster: MonochromeRaster, copies: Int): ByteArray = buildByteArray {
        val hex = raster.data.joinToString("") { byte -> "%02X".format(byte.toInt() and 0xff) }
        val totalBytes = raster.data.size
        appendAscii("^XA^FO0,0^GFA,$totalBytes,$totalBytes,${raster.bytesPerRow},$hex^PQ$copies^XZ")
    }

    private class ByteArrayBuilder {
        private val output = java.io.ByteArrayOutputStream()
        fun append(bytes: ByteArray) { output.write(bytes) }
        fun appendAscii(value: String) { append(value.toByteArray(Charsets.US_ASCII)) }
        fun appendLittleEndian(value: Int) { append(byteArrayOf((value and 0xff).toByte(), ((value shr 8) and 0xff).toByte())) }
        fun result(): ByteArray = output.toByteArray()
    }

    private fun buildByteArray(block: ByteArrayBuilder.() -> Unit): ByteArray = ByteArrayBuilder().apply(block).result()
}
