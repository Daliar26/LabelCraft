package dev.openlabel.core.label.model

import kotlin.math.roundToInt

private const val MILLIMETERS_PER_INCH = 25.4f

fun mmToDots(mm: Float, dpi: PrinterDpi): Int {
    require(mm.isFinite() && mm >= 0f) { "Размер в миллиметрах должен быть конечным и неотрицательным" }
    return (mm * dpi.dotsPerInch / MILLIMETERS_PER_INCH).roundToInt()
}

fun dotsToMm(dots: Int, dpi: PrinterDpi): Float {
    require(dots >= 0) { "Количество точек не может быть отрицательным" }
    return dots * MILLIMETERS_PER_INCH / dpi.dotsPerInch
}

fun validateDocument(document: LabelDocument) {
    require(document.widthMm in 20f..110f) { "Ширина документа должна быть 20–110 мм" }
    require(document.heightMm in 10f..300f) { "Длина документа должна быть 10–300 мм" }
    require(document.gapMm in 0f..20f) { "Зазор должен быть 0–20 мм" }
    document.elements.forEach { element ->
        require(element.xMm.isFinite() && element.yMm.isFinite())
        require(element.widthMm > 0f && element.heightMm > 0f)
        require(element.xMm >= 0f && element.yMm >= 0f)
        require(element.xMm + element.widthMm <= document.widthMm + 0.01f) { "Элемент ${element.id} выходит за ширину этикетки" }
        require(element.yMm + element.heightMm <= document.heightMm + 0.01f) { "Элемент ${element.id} выходит за длину этикетки" }
    }
}
