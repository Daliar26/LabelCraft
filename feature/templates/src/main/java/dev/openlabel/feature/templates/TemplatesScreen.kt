package dev.openlabel.feature.templates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.openlabel.core.label.codec.LabelDocumentCodec
import dev.openlabel.core.label.csv.CsvBatchExpander
import dev.openlabel.core.label.csv.CsvParser
import dev.openlabel.core.label.model.LabelDocument

@Composable
fun TemplatesScreen(
    document: LabelDocument,
    onLoad: (LabelDocument) -> Unit,
    onBatchPrint: (List<LabelDocument>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { TemplateStore(context) }
    var files by remember { mutableStateOf(store.list()) }
    var message by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(LabelDocumentCodec.encode(document).toByteArray(Charsets.UTF_8))
                } ?: error("Не удалось создать файл")
                message = "JSON экспортирован"
            }.onFailure {
                message = it.message ?: "Ошибка экспорта"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Не удалось открыть файл")
                onLoad(LabelDocumentCodec.decode(json))
                message = "Шаблон импортирован"
            }.onFailure {
                message = it.message ?: "Ошибка импорта"
            }
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val csv = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Не удалось открыть CSV")
                val rows = CsvParser.parseWithHeader(csv)
                val batch = CsvBatchExpander.expand(document, rows)
                onBatchPrint(batch)
                message = "Подготовлено заданий: ${batch.size}"
            }.onFailure {
                message = it.message ?: "Ошибка CSV"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) {
                Text("Назад")
            }
            Button(
                onClick = {
                    store.save(document)
                    files = store.list()
                    message = "Шаблон сохранён"
                }
            ) {
                Text("Сохранить")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                exportLauncher.launch("${document.name}.json")
            }) {
                Text("Экспорт JSON")
            }
            Button(onClick = {
                importLauncher.launch(arrayOf("application/json", "text/plain"))
            }) {
                Text("Импорт JSON")
            }
        }

        Button(
            onClick = {
                csvLauncher.launch(arrayOf("text/*", "application/vnd.ms-excel"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Пакетная печать из CSV")
        }

        Text(
            "Шаблоны",
            style = MaterialTheme.typography.headlineSmall
        )

        if (message.isNotBlank()) {
            Text(message, color = MaterialTheme.colorScheme.primary)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files, key = { it.absolutePath }) { file ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            file.nameWithoutExtension,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                runCatching {
                                    onLoad(store.read(file))
                                    message = "Загружен ${file.name}"
                                }.onFailure {
                                    message = it.message ?: "Ошибка"
                                }
                            }
                        ) {
                            Text("Открыть")
                        }
                    }
                }
            }
        }
    }
}
