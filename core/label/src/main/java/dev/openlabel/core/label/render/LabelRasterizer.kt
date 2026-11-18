package dev.openlabel.core.label.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.openlabel.core.label.model.BarcodeElement
import dev.openlabel.core.label.model.BarcodeSymbology
import dev.openlabel.core.label.model.ImageElement
import dev.openlabel.core.label.model.LabelDocument
import dev.openlabel.core.label.model.LabelElement
import dev.openlabel.core.label.model.LineElement
import dev.openlabel.core.label.model.PrinterDpi
import dev.openlabel.core.label.model.QrElement
import dev.openlabel.core.label.model.RectangleElement
import dev.openlabel.core.label.model.TextElement
import dev.openlabel.core.label.model.mmToDots
import dev.openlabel.core.label.model.validateDocument
import kotlin.math.max

class LabelRasterizer {
    fun render(document: LabelDocument): Bitmap {
        validateDocument(document)
        val width = mmToDots(document.widthMm, document.dpi).coerceAtLeast(1)
        val height = mmToDots(document.heightMm, document.dpi).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        document.elements.forEach { element -> drawElement(canvas, element, document.dpi) }
        return bitmap
    }

    private fun drawElement(canvas: Canvas, element: LabelElement, dpi: PrinterDpi) {
        val left = mmToDots(element.xMm, dpi).toFloat()
        val top = mmToDots(element.yMm, dpi).toFloat()
        val width = mmToDots(element.widthMm, dpi).toFloat()
        val height = mmToDots(element.heightMm, dpi).toFloat()
        val bounds = RectF(left, top, left + width, top + height)
        val save = canvas.save()
        if (element.rotationDegrees != 0f) canvas.rotate(element.rotationDegrees, bounds.centerX(), bounds.centerY())

        when (element) {
            is TextElement -> drawText(canvas, element, bounds, dpi)
            is BarcodeElement -> drawBarcode(canvas, element, bounds)
            is QrElement -> drawQr(canvas, element, bounds)
            is ImageElement -> drawImage(canvas, element, bounds)
            is LineElement -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = max(1f, mmToDots(element.strokeMm, dpi).toFloat())
                }
                canvas.drawLine(bounds.left, bounds.top, bounds.right, bounds.bottom, paint)
            }
            is RectangleElement -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    style = if (element.filled) Paint.Style.FILL else Paint.Style.STROKE
                    strokeWidth = max(1f, mmToDots(element.strokeMm, dpi).toFloat())
                }
                canvas.drawRect(bounds, paint)
            }
        }
        canvas.restoreToCount(save)
    }

    private fun drawText(canvas: Canvas, element: TextElement, bounds: RectF, dpi: PrinterDpi) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = element.fontSizePt * dpi.dotsPerInch / 72f
            typeface = Typeface.create(
                Typeface.DEFAULT,
                when {
                    element.bold && element.italic -> Typeface.BOLD_ITALIC
                    element.bold -> Typeface.BOLD
                    element.italic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                },
            )
        }
        val lineHeight = paint.fontSpacing
        element.text.split('\n').forEachIndexed { line, value ->
            canvas.drawText(value, bounds.left, bounds.top + lineHeight * (line + 1), paint)
        }
    }

    private fun drawBarcode(canvas: Canvas, element: BarcodeElement, bounds: RectF) {
        val format = when (element.symbology) {
            BarcodeSymbology.CODE_128 -> BarcodeFormat.CODE_128
            BarcodeSymbology.EAN_13 -> BarcodeFormat.EAN_13
        }
        val humanTextHeight = if (element.showText) (bounds.height() * 0.18f).coerceAtMost(12f) else 0f
        val barcodeBounds = RectF(bounds.left, bounds.top, bounds.right, (bounds.bottom - humanTextHeight).coerceAtLeast(bounds.top + 1f))
        val bitMatrix = MultiFormatWriter().encode(
            element.value,
            format,
            barcodeBounds.width().toInt().coerceAtLeast(1),
            barcodeBounds.height().toInt().coerceAtLeast(1),
        )
        canvas.drawBitmap(bitMatrix.toBitmap(), null, barcodeBounds, Paint(Paint.FILTER_BITMAP_FLAG))
        if (element.showText && humanTextHeight > 0f) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textAlign = Paint.Align.CENTER
                textSize = (humanTextHeight * 0.72f).coerceAtLeast(5f)
            }
            canvas.drawText(element.value, bounds.centerX(), bounds.bottom - textPaint.descent(), textPaint)
        }
    }

    private fun drawQr(canvas: Canvas, element: QrElement, bounds: RectF) {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
        val matrix = MultiFormatWriter().encode(element.value, BarcodeFormat.QR_CODE, bounds.width().toInt().coerceAtLeast(1), bounds.height().toInt().coerceAtLeast(1), hints)
        canvas.drawBitmap(matrix.toBitmap(), null, bounds, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawImage(canvas: Canvas, element: ImageElement, bounds: RectF) {
        runCatching { Base64.decode(element.encodedImage, Base64.DEFAULT) }
            .mapCatching { BitmapFactoryCompat.decode(it) }
            .getOrNull()?.let { image ->
                canvas.drawBitmap(image, null, bounds, Paint(Paint.FILTER_BITMAP_FLAG))
                image.recycle()
            }
    }

    private fun com.google.zxing.common.BitMatrix.toBitmap(): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) for (y in 0 until height) {
            result.setPixel(x, y, if (get(x, y)) Color.BLACK else Color.WHITE)
        }
        return result
    }
}

private object BitmapFactoryCompat {
    fun decode(bytes: ByteArray): Bitmap? = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
