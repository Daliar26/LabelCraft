package dev.openlabel.core.printer.protocol

import dev.openlabel.core.label.render.MonochromeRaster
import java.io.ByteArrayOutputStream

/**
 * Bluetooth LE protocol of the X6H family of inexpensive pocket thermal printers (GT01 and MX06 use the same framing).
 *
 * Frame layout: 0x51 0x78 CMD 0x00 LEN 0x00 DATA CRC8(DATA) 0xFF, written to the GATT characteristic AE01.
 * A raster row is 48 bytes (384 dots, 48 mm). Each row is one DRAW_BITMAP frame, leftmost dot in the least significant bit,
 * where a set bit prints black.
 *
 * Framing and the CRC-8 table follow the MIT-licensed inkjet project (https://github.com/AaronChartier/inkjet).
 * They are checked by unit tests against that project's reference output; each printer model still needs a physical test print.
 */
object X6hProtocol {
    const val PRINT_WIDTH_DOTS = 384
    const val PRINT_WIDTH_BYTES = 48

    /** Blank rows fed after each copy so the last printed row is not left under the print head. */
    const val FEED_LINES_AFTER_COPY = 6

    private const val CMD_DRAW_BITMAP = 0xA2
    private const val CMD_SET_QUALITY = 0xA4
    private const val CMD_SET_ENERGY = 0xAF
    private const val CMD_SET_SPEED = 0xBD
    private const val CMD_SET_DRAWING_MODE = 0xBE

    private const val DEFAULT_QUALITY = 3
    private const val DEFAULT_ENERGY = 9500
    private const val DEFAULT_SPEED = 10

    private val CRC8_TABLE = intArrayOf(
        0x00, 0x07, 0x0e, 0x09, 0x1c, 0x1b, 0x12, 0x15, 0x38, 0x3f, 0x36, 0x31, 0x24, 0x23, 0x2a, 0x2d,
        0x70, 0x77, 0x7e, 0x79, 0x6c, 0x6b, 0x62, 0x65, 0x48, 0x4f, 0x46, 0x41, 0x54, 0x53, 0x5a, 0x5d,
        0xe0, 0xe7, 0xee, 0xe9, 0xfc, 0xfb, 0xf2, 0xf5, 0xd8, 0xdf, 0xd6, 0xd1, 0xc4, 0xc3, 0xca, 0xcd,
        0x90, 0x97, 0x9e, 0x99, 0x8c, 0x8b, 0x82, 0x85, 0xa8, 0xaf, 0xa6, 0xa1, 0xb4, 0xb3, 0xba, 0xbd,
        0xc7, 0xc0, 0xc9, 0xce, 0xdb, 0xdc, 0xd5, 0xd2, 0xff, 0xf8, 0xf1, 0xf6, 0xe3, 0xe4, 0xed, 0xea,
        0xb7, 0xb0, 0xb9, 0xbe, 0xab, 0xac, 0xa5, 0xa2, 0x8f, 0x88, 0x81, 0x86, 0x93, 0x94, 0x9d, 0x9a,
        0x27, 0x20, 0x29, 0x2e, 0x3b, 0x3c, 0x35, 0x32, 0x1f, 0x18, 0x11, 0x16, 0x03, 0x04, 0x0d, 0x0a,
        0x57, 0x50, 0x59, 0x5e, 0x4b, 0x4c, 0x45, 0x42, 0x6f, 0x68, 0x61, 0x66, 0x73, 0x74, 0x7d, 0x7a,
        0x89, 0x8e, 0x87, 0x80, 0x95, 0x92, 0x9b, 0x9c, 0xb1, 0xb6, 0xbf, 0xb8, 0xad, 0xaa, 0xa3, 0xa4,
        0xf9, 0xfe, 0xf7, 0xf0, 0xe5, 0xe2, 0xeb, 0xec, 0xc1, 0xc6, 0xcf, 0xc8, 0xdd, 0xda, 0xd3, 0xd4,
        0x69, 0x6e, 0x67, 0x60, 0x75, 0x72, 0x7b, 0x7c, 0x51, 0x56, 0x5f, 0x58, 0x4d, 0x4a, 0x43, 0x44,
        0x19, 0x1e, 0x17, 0x10, 0x05, 0x02, 0x0b, 0x0c, 0x21, 0x26, 0x2f, 0x28, 0x3d, 0x3a, 0x33, 0x34,
        0x4e, 0x49, 0x40, 0x47, 0x52, 0x55, 0x5c, 0x5b, 0x76, 0x71, 0x78, 0x7f, 0x6a, 0x6d, 0x64, 0x63,
        0x3e, 0x39, 0x30, 0x37, 0x22, 0x25, 0x2c, 0x2b, 0x06, 0x01, 0x08, 0x0f, 0x1a, 0x1d, 0x14, 0x13,
        0xae, 0xa9, 0xa0, 0xa7, 0xb2, 0xb5, 0xbc, 0xbb, 0x96, 0x91, 0x98, 0x9f, 0x8a, 0x8d, 0x84, 0x83,
        0xde, 0xd9, 0xd0, 0xd7, 0xc2, 0xc5, 0xcc, 0xcb, 0xe6, 0xe1, 0xe8, 0xef, 0xfa, 0xfd, 0xf4, 0xf3,
    )

    fun crc8(data: ByteArray): Int {
        var crc = 0
        for (b in data) crc = CRC8_TABLE[(crc xor (b.toInt() and 0xFF)) and 0xFF]
        return crc and 0xFF
    }

    fun frame(command: Int, data: ByteArray): ByteArray {
        require(data.size <= 0xFF) { "Длина кадра X6H не должна превышать 255 байт" }
        val out = ByteArray(6 + data.size + 2)
        out[0] = 0x51.toByte()
        out[1] = 0x78.toByte()
        out[2] = command.toByte()
        out[3] = 0x00.toByte()
        out[4] = data.size.toByte()
        out[5] = 0x00.toByte()
        data.copyInto(out, 6)
        out[out.size - 2] = crc8(data).toByte()
        out[out.size - 1] = 0xFF.toByte()
        return out
    }

    /** Complete job: configuration frames, then each copy as full raster rows followed by a short feed. */
    fun frames(raster: MonochromeRaster, copies: Int): List<ByteArray> {
        require(copies in 1..9_999) { "Количество копий вне диапазона" }
        require(raster.width <= PRINT_WIDTH_DOTS) {
            "Ширина этикетки ${raster.width} точек больше ширины печати X6H ($PRINT_WIDTH_DOTS точек, 48 мм)"
        }
        require(raster.bytesPerRow <= PRINT_WIDTH_BYTES) { "Строка растра больше ${PRINT_WIDTH_BYTES} байт" }
        val result = ArrayList<ByteArray>()
        result += frame(CMD_SET_QUALITY, byteArrayOf(DEFAULT_QUALITY.toByte()))
        result += frame(CMD_SET_ENERGY, byteArrayOf((DEFAULT_ENERGY and 0xFF).toByte(), ((DEFAULT_ENERGY shr 8) and 0xFF).toByte()))
        result += frame(CMD_SET_SPEED, byteArrayOf(DEFAULT_SPEED.toByte()))
        result += frame(CMD_SET_DRAWING_MODE, byteArrayOf(0x00))
        val blank = ByteArray(PRINT_WIDTH_BYTES)
        repeat(copies) {
            for (y in 0 until raster.height) result += frame(CMD_DRAW_BITMAP, row(raster, y))
            repeat(FEED_LINES_AFTER_COPY) { result += frame(CMD_DRAW_BITMAP, blank) }
        }
        return result
    }

    /** Converts one MSB-first raster row into the 48-byte LSB-first row used by X6H; unused bytes stay white. */
    private fun row(raster: MonochromeRaster, y: Int): ByteArray {
        val line = ByteArray(PRINT_WIDTH_BYTES)
        val base = y * raster.bytesPerRow
        for (i in 0 until raster.bytesPerRow) line[i] = reverseBits(raster.data[base + i])
        return line
    }

    private fun reverseBits(value: Byte): Byte {
        var v = value.toInt() and 0xFF
        var reversed = 0
        repeat(8) {
            reversed = (reversed shl 1) or (v and 1)
            v = v shr 1
        }
        return reversed.toByte()
    }
}
