package dev.openlabel.feature.templates

import android.content.Context
import dev.openlabel.core.label.codec.LabelDocumentCodec
import dev.openlabel.core.label.model.LabelDocument
import java.io.File

class TemplateStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "templates").apply { mkdirs() }

    fun save(document: LabelDocument): File {
        val safeName = document.name.replace(Regex("[^a-zA-Z0-9А-Яа-я_-]"), "_").take(48).ifBlank { "label" }
        return File(directory, "$safeName.json").apply { writeText(LabelDocumentCodec.encode(document), Charsets.UTF_8) }
    }

    fun list(): List<File> = directory.listFiles { file -> file.isFile && file.extension == "json" }?.sortedBy { it.name } ?: emptyList()
    fun read(file: File): LabelDocument = LabelDocumentCodec.decode(file.readText(Charsets.UTF_8))
}
