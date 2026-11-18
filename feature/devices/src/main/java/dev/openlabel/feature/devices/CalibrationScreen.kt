package dev.openlabel.feature.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.openlabel.core.label.model.LabelProfile
import dev.openlabel.core.label.model.MediaType
import dev.openlabel.core.label.model.PrinterDpi

@Composable
fun CalibrationScreen(
    initialProfile: LabelProfile,
    connected: Boolean,
    message: String?,
    onAutomatic: () -> Unit,
    onSaveManual: (LabelProfile) -> Unit,
    onBack: () -> Unit,
) {
    var width by remember(initialProfile) { mutableStateOf(initialProfile.widthMm.toString()) }
    var height by remember(initialProfile) { mutableStateOf(initialProfile.heightMm.toString()) }
    var gap by remember(initialProfile) { mutableFloatStateOf(initialProfile.gapMm) }
    var dpi by remember(initialProfile) { mutableStateOf(initialProfile.dpi) }
    var media by remember(initialProfile) { mutableStateOf(initialProfile.mediaType) }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("Назад") }
            Text("Калибровка этикетки", style = MaterialTheme.typography.headlineSmall)
        }
        Text(if (connected) "Принтер подключён" else "Подключите принтер для аппаратной калибровки")
        Button(onClick = onAutomatic, enabled = connected, modifier = Modifier.fillMaxWidth()) {
            Text("Автокалибровка (gap / black mark)")
        }
        Text("Автоматический замер доступен только для моделей, которые возвращают данные датчика/энкодера. Команда калибровки без телеметрии не считается измерением.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(width, { width = it }, Modifier.fillMaxWidth(), label = { Text("Ширина, мм (20–110)") })
        OutlinedTextField(height, { height = it }, Modifier.fillMaxWidth(), label = { Text("Длина этикетки, мм (10–300)") })
        Text("Зазор: ${"%.1f".format(gap)} мм")
        Slider(value = gap, onValueChange = { gap = it }, valueRange = 0f..20f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dpi = PrinterDpi.DPI_203 }) { Text(if (dpi == PrinterDpi.DPI_203) "✓ 203 DPI" else "203 DPI") }
            Button(onClick = { dpi = PrinterDpi.DPI_300 }) { Text(if (dpi == PrinterDpi.DPI_300) "✓ 300 DPI" else "300 DPI") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { media = MediaType.GAP }) { Text(if (media == MediaType.GAP) "✓ Gap" else "Gap") }
            Button(onClick = { media = MediaType.BLACK_MARK }) { Text(if (media == MediaType.BLACK_MARK) "✓ Black mark" else "Black mark") }
            Button(onClick = { media = MediaType.CONTINUOUS }) { Text(if (media == MediaType.CONTINUOUS) "✓ Continuous" else "Continuous") }
        }
        if (message != null) Text(message, color = MaterialTheme.colorScheme.primary)
        if (localError != null) Text(localError!!, color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            runCatching {
                val profile = LabelProfile(
                    name = "Профиль принтера",
                    widthMm = width.toFloat(),
                    heightMm = height.toFloat(),
                    dpi = dpi,
                    mediaType = media,
                    gapMm = if (media == MediaType.CONTINUOUS) 0f else gap,
                )
                onSaveManual(profile)
                localError = null
            }.onFailure { localError = it.message ?: "Некорректные параметры этикетки" }
        }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить ручной профиль") }
    }
}
