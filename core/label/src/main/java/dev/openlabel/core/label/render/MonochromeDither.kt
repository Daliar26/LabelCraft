package dev.openlabel.core.label.render

import android.graphics.Bitmap
import kotlin.math.roundToInt

/** MSB-first packed 1-bit raster with white=0 and black=1. */
data class MonochromeRaster(val width: Int, val height: Int, val bytesPerRow: Int, val data: ByteArray)

object MonochromeDither {
    fun convert(bitmap: Bitmap, threshold: Int = 128): MonochromeRaster {
        require(threshold in 0..255)
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val luminance = FloatArray(pixels.size) { index ->
            val color = pixels[index]
            (0.299f * ((color shr 16) and 0xff) + 0.587f * ((color shr 8) and 0xff) + 0.114f * (color and 0xff))
        }
        val packedWidth = (width + 7) / 8
        val output = ByteArray(packedWidth * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val old = luminance[index].coerceIn(0f, 255f)
                val new = if (old < threshold) 0f else 255f
                val error = old - new
                if (new == 0f) {
                    val byteIndex = y * packedWidth + x / 8
                    output[byteIndex] = (output[byteIndex].toInt() or (0x80 shr (x % 8))).toByte()
                }
                if (x + 1 < width) luminance[index + 1] += error * 7f / 16f
                if (y + 1 < height) {
                    if (x > 0) luminance[index + width - 1] += error * 3f / 16f
                    luminance[index + width] += error * 5f / 16f
                    if (x + 1 < width) luminance[index + width + 1] += error / 16f
                }
            }
        }
        return MonochromeRaster(width, height, packedWidth, output)
    }
}
