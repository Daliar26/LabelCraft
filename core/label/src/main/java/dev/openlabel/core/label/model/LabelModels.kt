package dev.openlabel.core.label.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PrinterDpi(val dotsPerInch: Int) { DPI_203(203), DPI_300(300) }

@Serializable
enum class BarcodeSymbology { CODE_128, EAN_13 }

@Serializable
sealed interface LabelElement {
    val id: String
    val xMm: Float
    val yMm: Float
    val widthMm: Float
    val heightMm: Float
    val rotationDegrees: Float
}

@Serializable
@SerialName("text")
data class TextElement(
    override val id: String,
    override val xMm: Float,
    override val yMm: Float,
    override val widthMm: Float,
    override val heightMm: Float,
    val text: String,
    val fontSizePt: Float = 12f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    override val rotationDegrees: Float = 0f,
) : LabelElement

@Serializable
@SerialName("barcode")
data class BarcodeElement(
    override val id: String,
    override val xMm: Float,
    override val yMm: Float,
    override val widthMm: Float,
    override val heightMm: Float,
    val value: String,
    val symbology: BarcodeSymbology = BarcodeSymbology.CODE_128,
    val showText: Boolean = true,
    override val rotationDegrees: Float = 0f,
) : LabelElement

@Serializable
@SerialName("qr")
data class QrElement(
    override val id: String,
    override val xMm: Float,
    override val yMm: Float,
    override val widthMm: Float,
    override val heightMm: Float,
    val value: String,
    override val rotationDegrees: Float = 0f,
) : LabelElement

@Serializable
@SerialName("image")
data class ImageElement(
    override val id: String,
    override val xMm: Float,
    override val yMm: Float,
    override val widthMm: Float,
    override val heightMm: Float,
    val encodedImage: String,
    override val rotationDegrees: Float = 0f,
) : LabelElement

@Serializable
@SerialName("line")
data class LineElement(
    override val id: String,
    override val xMm: Float,
    override val yMm: Float,
    override val widthMm: Float,
    override val heightMm: Float,
    val strokeMm: Float = 0.2f,
    override val rotationDegrees: Float = 0f,
) : LabelElement

@Serializable
@SerialName("rectangle")
data class RectangleElement(
    override val id: String,
    override val xMm: Float,
    override val yMm: Float,
    override val widthMm: Float,
    override val heightMm: Float,
    val strokeMm: Float = 0.2f,
    val filled: Boolean = false,
    override val rotationDegrees: Float = 0f,
) : LabelElement

@Serializable
data class LabelDocument(
    val name: String = "Новая этикетка",
    val widthMm: Float = 58f,
    val heightMm: Float = 40f,
    val dpi: PrinterDpi = PrinterDpi.DPI_203,
    val mediaType: MediaType = MediaType.GAP,
    val gapMm: Float = 2f,
    val elements: List<LabelElement> = emptyList(),
)

@Serializable
data class LabelProfile(
    val name: String,
    val widthMm: Float,
    val heightMm: Float,
    val dpi: PrinterDpi,
    val mediaType: MediaType = MediaType.GAP,
    val gapMm: Float = 2f,
) {
    init {
        require(widthMm in 20f..110f) { "Ширина этикетки должна быть 20–110 мм" }
        require(heightMm in 10f..300f) { "Длина этикетки должна быть 10–300 мм" }
        require(gapMm in 0f..20f) { "Зазор должен быть 0–20 мм" }
    }
}

@Serializable
enum class MediaType { GAP, BLACK_MARK, CONTINUOUS }
