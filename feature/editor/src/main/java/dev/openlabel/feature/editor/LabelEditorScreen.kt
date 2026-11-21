package dev.openlabel.feature.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.openlabel.core.label.model.BarcodeElement
import dev.openlabel.core.label.model.BarcodeSymbology
import dev.openlabel.core.label.model.ImageElement
import dev.openlabel.core.label.model.LabelDocument
import dev.openlabel.core.label.model.LineElement
import dev.openlabel.core.label.model.PrinterDpi
import dev.openlabel.core.label.model.QrElement
import dev.openlabel.core.label.model.RectangleElement
import dev.openlabel.core.label.model.TextElement
import dev.openlabel.core.printer.hardware.HardwareAction
import dev.openlabel.core.printer.hardware.HardwareKeyMapper
import dev.openlabel.core.printer.hardware.HardwareScrollProfile
import dev.openlabel.core.printer.hardware.HardwareScrollProfileStore
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun LabelEditorScreen(
    document: LabelDocument,
    deviceAddress: String?,
    onDocumentChanged: (LabelDocument) -> Unit,
    onPrint: (LabelDocument, Int) -> Unit,
    onDevices: () -> Unit,
    onTemplates: () -> Unit,
    onQueue: () -> Unit,
    onSettings: () -> Unit,
) {
    var current by remember { mutableStateOf(document) }
    var textValue by remember { mutableStateOf("Товар") }
    var widthText by remember(document.widthMm) { mutableStateOf(document.widthMm.toString()) }
    var heightText by remember(document.heightMm) { mutableStateOf(document.heightMm.toString()) }
    var fontSize by remember { mutableFloatStateOf(12f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var copiesText by remember { mutableStateOf("1") }
    var profileError by remember { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val profileStore = remember { HardwareScrollProfileStore(context) }
    var scrollProfile by remember(deviceAddress) { mutableStateOf(profileStore.load(deviceAddress ?: "default")) }
    var stepMm by remember(deviceAddress) { mutableFloatStateOf(scrollProfile.stepMm) }
    var speed by remember(deviceAddress) { mutableFloatStateOf(scrollProfile.speedMultiplier) }
    var acceleration by remember(deviceAddress) { mutableFloatStateOf(scrollProfile.acceleration) }
    var inverted by remember(deviceAddress) { mutableStateOf(scrollProfile.inverted) }
    var mappingText by remember(deviceAddress) {
        mutableStateOf(scrollProfile.keyMap.entries.joinToString(",") { "${it.key}:${it.value.name}" })
    }

    fun updateDocument(updated: LabelDocument) {
        current = updated
        onDocumentChanged(updated)
    }

    fun applySizePreset(widthMm: Float, heightMm: Float) {
        val fits = current.elements.all { it.xMm + it.widthMm <= widthMm && it.yMm + it.heightMm <= heightMm }
        if (!fits) {
            profileError = "Текущие элементы не помещаются в выбранный формат. Удалите или переместите элементы."
            return
        }
        updateDocument(current.copy(widthMm = widthMm, heightMm = heightMm))
        widthText = widthMm.toString()
        heightText = heightMm.toString()
        profileError = null
    }

    fun addImage(bitmap: Bitmap) {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
        val data = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        bitmap.recycle()
        val size = min(18f, min(current.widthMm - 4f, current.heightMm - 4f)).coerceAtLeast(5f)
        val index = current.elements.size + 1
        updateDocument(current.copy(elements = current.elements + ImageElement("image-$index", 2f, 2f, size, size, data)))
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching { context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input) } }
            .getOrNull()?.let(::addImage)
    }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let(::addImage)
    }

    LaunchedEffect(document) {
        current = document
        widthText = document.widthMm.toString()
        heightText = document.heightMm.toString()
    }
    LaunchedEffect(stepMm, speed, acceleration, inverted, mappingText, deviceAddress) {
        val parsedMap = mappingText.split(',').mapNotNull { item ->
            val parts = item.trim().split(':', limit = 2)
            if (parts.size != 2) null else runCatching { parts[0].toInt() to HardwareAction.valueOf(parts[1].uppercase()) }.getOrNull()
        }.toMap().ifEmpty { HardwareScrollProfile.defaultKeyMap() }
        scrollProfile = scrollProfile.copy(
            deviceAddress = deviceAddress ?: "default",
            stepMm = stepMm,
            speedMultiplier = speed,
            acceleration = acceleration,
            inverted = inverted,
            keyMap = parsedMap,
        )
        runCatching { profileStore.save(scrollProfile) }.onFailure { profileError = it.message }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val keyCode = event.nativeKeyEvent.keyCode
                val action = HardwareKeyMapper.map(keyCode, scrollProfile)
                if (action == HardwareAction.NONE) return@onPreviewKeyEvent false
                val delta = HardwareKeyMapper.deltaPx(
                    action = action,
                    profile = scrollProfile,
                    density = density,
                    dpi = current.dpi.dotsPerInch,
                    repeated = false,
                )
                scope.launch { scroll.scrollBy(delta) }
                scrollProfile.consumeVolumeKeys || keyCode !in setOf(
                    android.view.KeyEvent.KEYCODE_VOLUME_UP,
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN,
                )
            }
            .pointerInput(scrollProfile) {
                awaitPointerEventScope {
                    while (true) {
                        val pointerEvent = awaitPointerEvent()
                        if (pointerEvent.type == PointerEventType.Scroll) {
                            val wheelDelta = pointerEvent.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (wheelDelta != 0f) {
                                val action = if (wheelDelta > 0f) HardwareAction.SCROLL_DOWN else HardwareAction.SCROLL_UP
                                val delta = HardwareKeyMapper.deltaPx(
                                    action = action,
                                    profile = scrollProfile,
                                    density = density,
                                    dpi = current.dpi.dotsPerInch,
                                )
                                scroll.scrollBy(delta * kotlin.math.abs(wheelDelta).coerceAtLeast(1f))
                            }
                            pointerEvent.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("LabelCraft", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WYSIWYG • ${current.widthMm.toInt()} × ${current.heightMm.toInt()} мм • ${current.dpi.dotsPerInch} DPI")
                Canvas(Modifier.fillMaxWidth().height(210.dp)) {
                    val scale = min(size.width / current.widthMm, size.height / current.heightMm)
                    val labelWidth = current.widthMm * scale
                    val labelHeight = current.heightMm * scale
                    val left = (size.width - labelWidth) / 2f
                    val top = (size.height - labelHeight) / 2f
                    drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(labelWidth, labelHeight))
                    drawRect(Color.DarkGray, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(labelWidth, labelHeight), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    val native = drawContext.canvas.nativeCanvas
                    current.elements.forEach { element ->
                        val x = left + element.xMm * scale
                        val y = top + element.yMm * scale
                        val w = element.widthMm * scale
                        val h = element.heightMm * scale
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.BLACK
                            style = if (element is RectangleElement && element.filled) Paint.Style.FILL else Paint.Style.STROKE
                            strokeWidth = 1f
                            textSize = (element as? TextElement)?.fontSizePt?.let { it * current.dpi.dotsPerInch / 72f * scale / 2.8f } ?: 10f
                        }
                        when (element) {
                            is TextElement -> native.drawText(element.text, x, y + h.coerceAtLeast(paint.textSize), paint)
                            is BarcodeElement, is QrElement, is ImageElement -> native.drawRect(x, y, x + w, y + h, paint)
                            is RectangleElement -> native.drawRect(x, y, x + w, y + h, paint)
                            else -> native.drawLine(x, y, x + w, y + h, paint)
                        }
                    }
                }
            }
        }
        Text("Размер этикетки (мм)", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(widthText, { widthText = it }, Modifier.weight(1f), label = { Text("Ширина 20–110") })
            OutlinedTextField(heightText, { heightText = it }, Modifier.weight(1f), label = { Text("Длина 10–300") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { applySizePreset(40f, 30f) }) { Text("40 × 30") }
            Button(onClick = { applySizePreset(58f, 40f) }) { Text("58 × 40") }
            Button(onClick = { applySizePreset(100f, 150f) }) { Text("100 × 150") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                runCatching {
                    val width = widthText.toFloat()
                    val height = heightText.toFloat()
                    require(width in 20f..110f && height in 10f..300f)
                    require(current.elements.all { it.xMm + it.widthMm <= width && it.yMm + it.heightMm <= height }) {
                        "Новый размер меньше размещённых элементов. Переместите элементы или увеличьте размер."
                    }
                    updateDocument(current.copy(widthMm = width, heightMm = height))
                    profileError = null
                }.onFailure { profileError = "Допустимо: ширина 20–110 мм, длина 10–300 мм" }
            }) { Text("Применить размер") }
            Button(onClick = { updateDocument(current.copy(dpi = if (current.dpi == PrinterDpi.DPI_203) PrinterDpi.DPI_300 else PrinterDpi.DPI_203)) }) {
                Text("DPI ${current.dpi.dotsPerInch}")
            }
        }
        if (profileError != null) Text(profileError!!, color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDevices) { Text("Принтеры") }
            Button(onClick = onTemplates) { Text("Шаблоны") }
            Button(onClick = onQueue) { Text("Очередь") }
            Button(onClick = onSettings) { Text("Протокол") }
        }
        Text("Элементы", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(textValue, { textValue = it }, Modifier.fillMaxWidth(), label = { Text("Текст") })
        Text("Размер шрифта: ${fontSize.toInt()} pt")
        Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 6f..36f)
        Text("Поворот: ${rotation.toInt()}°")
        Slider(value = rotation, onValueChange = { rotation = it }, valueRange = 0f..360f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val index = current.elements.size + 1
                val y = (2f + index * 5f).coerceAtMost((current.heightMm - 7f).coerceAtLeast(0f))
                updateDocument(current.copy(elements = current.elements + TextElement("text-$index", 2f, y, current.widthMm - 4f, 5f, textValue, fontSizePt = fontSize, rotationDegrees = rotation)))
            }) { Text("+ Текст") }
            Button(onClick = {
                val index = current.elements.size + 1
                val barcodeHeight = min(12f, current.heightMm)
                val y = (current.heightMm - barcodeHeight).coerceAtLeast(0f)
                updateDocument(current.copy(elements = current.elements + BarcodeElement("code128-$index", 2f, y, current.widthMm - 4f, barcodeHeight, "123456789012", BarcodeSymbology.CODE_128)))
            }) { Text("+ Code128") }
            Button(onClick = {
                val index = current.elements.size + 1
                val y = (current.heightMm - 18f).coerceAtLeast(0f)
                updateDocument(current.copy(elements = current.elements + BarcodeElement("ean13-$index", 2f, y, current.widthMm - 4f, 12f, "5901234123457", BarcodeSymbology.EAN_13)))
            }) { Text("+ EAN-13") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val index = current.elements.size + 1
                val side = min(16f, min(current.widthMm - 4f, current.heightMm - 4f)).coerceAtLeast(4f)
                updateDocument(current.copy(elements = current.elements + QrElement("qr-$index", 2f, 2f, side, side, "LabelCraft")))
            }) { Text("+ QR") }
            Button(onClick = {
                val index = current.elements.size + 1
                updateDocument(current.copy(elements = current.elements + RectangleElement("rect-$index", 2f, 2f, current.widthMm - 4f, current.heightMm - 4f)))
            }) { Text("+ Рамка") }
            Button(onClick = {
                val index = current.elements.size + 1
                updateDocument(current.copy(elements = current.elements + LineElement("line-$index", 2f, 2f, current.widthMm - 4f, current.heightMm - 4f)))
            }) { Text("+ Линия") }
            Button(onClick = { galleryPicker.launch("image/*") }) { Text("Галерея") }
            Button(onClick = { cameraPicker.launch(null) }) { Text("Камера") }
        }
        Text("Аппаратная прокрутка · профиль ${deviceAddress ?: "по умолчанию"}", style = MaterialTheme.typography.titleMedium)
        Text("Шаг: ${"%.1f".format(stepMm)} мм")
        Slider(value = stepMm, onValueChange = { stepMm = it }, valueRange = 0.5f..30f)
        Text("Скорость: ${"%.2f".format(speed)}×")
        Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.25f..5f)
        Text("Ускорение: ${"%.2f".format(acceleration)}×")
        Slider(value = acceleration, onValueChange = { acceleration = it }, valueRange = 0.5f..4f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Инверсия")
            Switch(checked = inverted, onCheckedChange = { inverted = it })
        }
        OutlinedTextField(
            value = mappingText,
            onValueChange = { mappingText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Карта keyCode:ACTION") },
            supportingText = { Text("Например: 24:SCROLL_UP,25:SCROLL_DOWN,92:PAGE_UP,93:PAGE_DOWN") },
        )
        Text("Громкость / D-pad / Bluetooth-клавиатура обрабатываются в окне редактора. Для HID-колёс прошивка может отправлять системные события прокрутки вместо клавиш.", style = MaterialTheme.typography.bodySmall)
        Text("Копии")
        OutlinedTextField(copiesText, { copiesText = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Количество 1–9999") })
        Button(onClick = { onDocumentChanged(current); onPrint(current, copiesText.toIntOrNull()?.coerceIn(1, 9_999) ?: 1) }, modifier = Modifier.fillMaxWidth()) { Text("Добавить в очередь печати") }
        Spacer(Modifier.height(20.dp))
    }
}
