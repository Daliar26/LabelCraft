package dev.openlabel.core.label.codec

import dev.openlabel.core.label.model.LabelDocument
import dev.openlabel.core.label.model.validateDocument
import kotlinx.serialization.json.Json

object LabelDocumentCodec {
    private val json = Json {
        classDiscriminator = "elementType"
        prettyPrint = true
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun encode(document: LabelDocument): String {
        validateDocument(document)
        return json.encodeToString(LabelDocument.serializer(), document)
    }

    fun decode(source: String): LabelDocument = json.decodeFromString(LabelDocument.serializer(), source).also(::validateDocument)
}
