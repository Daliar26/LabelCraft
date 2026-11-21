package dev.openlabel.core.printer

import dev.openlabel.core.label.render.MonochromeRaster
import dev.openlabel.core.printer.protocol.X6hProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X6hProtocolTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    @Test
    fun crc8MatchesReferenceImplementation() {
        assertEquals(0, X6hProtocol.crc8(byteArrayOf()))
        assertEquals(72, X6hProtocol.crc8(byteArrayOf(1, 2, 3)))
        assertEquals(232, X6hProtocol.crc8(ByteArray(48) { 0xFF.toByte() }))
    }

    @Test
    fun configurationFramesMatchReferenceBytes() {
        assertEquals("5178a40001000309ff", hex(X6hProtocol.frame(0xA4, byteArrayOf(3))))
        assertEquals("5178af0002001c2550ff", hex(X6hProtocol.frame(0xAF, byteArrayOf(0x1C, 0x25))))
        assertEquals("5178bd0001000a36ff", hex(X6hProtocol.frame(0xBD, byteArrayOf(10))))
        assertEquals("5178be0001000000ff", hex(X6hProtocol.frame(0xBE, byteArrayOf(0))))
    }

    @Test
    fun rasterRowIsLsbFirstAndMarkedBlackOnSetBit() {
        // Leftmost dot black: MSB-first input 0x80 must become LSB-first 0x01 in the first data byte.
        val raster = MonochromeRaster(width = 8, height = 1, bytesPerRow = 1, data = byteArrayOf(0x80.toByte()))
        val rowFrame = X6hProtocol.frames(raster, 1)[4]
        assertEquals(56, rowFrame.size)
        assertEquals(0xA2, rowFrame[2].toInt() and 0xFF)
        assertEquals(0x01, rowFrame[6].toInt() and 0xFF)
        assertEquals(8, rowFrame[54].toInt() and 0xFF)
        assertEquals(0xFF, rowFrame[55].toInt() and 0xFF)
    }

    @Test
    fun frameCountIncludesConfigurationRowsAndFeed() {
        val raster = MonochromeRaster(width = 8, height = 3, bytesPerRow = 1, data = ByteArray(3))
        assertEquals(4 + 3 + 6, X6hProtocol.frames(raster, 1).size)
        assertEquals(4 + 2 * (3 + 6), X6hProtocol.frames(raster, 2).size)
    }

    @Test
    fun rejectsLabelWiderThanPrintHead() {
        val raster = MonochromeRaster(width = 500, height = 1, bytesPerRow = 63, data = ByteArray(63))
        val error = runCatching { X6hProtocol.frames(raster, 1) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
