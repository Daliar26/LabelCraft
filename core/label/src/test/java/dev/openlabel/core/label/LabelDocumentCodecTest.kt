package dev.openlabel.core.label

import dev.openlabel.core.label.codec.LabelDocumentCodec
import dev.openlabel.core.label.model.LabelDocument
import dev.openlabel.core.label.model.MediaType
import dev.openlabel.core.label.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelDocumentCodecTest {
    @Test fun jsonRoundTripRetainsElements() {
        val original = LabelDocument(
            mediaType = MediaType.BLACK_MARK,
            gapMm = 4.5f,
            elements = listOf(TextElement("label", 2f, 3f, 30f, 8f, "Test")),
        )
        val decoded = LabelDocumentCodec.decode(LabelDocumentCodec.encode(original))
        assertEquals(original, decoded)
    }
}
