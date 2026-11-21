package dev.openlabel.core.label

import dev.openlabel.core.label.csv.CsvBatchExpander
import dev.openlabel.core.label.csv.CsvParser
import dev.openlabel.core.label.model.LabelDocument
import dev.openlabel.core.label.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CsvParserTest {
    @Test fun parsesSimpleRows() = assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), CsvParser.parse("a,b\n1,2"))
    @Test fun parsesQuotedComma() = assertEquals("A, B", CsvParser.parse("\"A, B\",x").first().first())
    @Test fun parsesEscapedQuote() = assertEquals("say \"hi\"", CsvParser.parse("\"say \"\"hi\"\"\"").first().first())
    @Test fun parsesCrlf() = assertEquals(2, CsvParser.parse("a,b\r\n1,2\r\n").size)
    @Test fun parsesEmbeddedNewline() = assertEquals("first\nsecond", CsvParser.parse("\"first\nsecond\",x").first().first())
    @Test fun stripsUtf8Bom() = assertEquals("name", CsvParser.parse("\uFEFFname").first().first())
    @Test fun rejectsUnclosedQuote() { assertThrows(IllegalArgumentException::class.java) { CsvParser.parse("\"abc") } }
    @Test fun headerRowsBecomeMaps() = assertEquals("Ada", CsvParser.parseWithHeader("name,qty\nAda,3").single()["name"])
    @Test fun rejectsDuplicateHeaders() { assertThrows(IllegalArgumentException::class.java) { CsvParser.parseWithHeader("name,name\na,b") } }
    @Test fun expandsCsvTokensInTemplateText() {
        val template = LabelDocument(elements = listOf(TextElement("t", 1f, 1f, 20f, 6f, "{{name}} / {{sku}}")))
        val result = CsvBatchExpander.expand(template, listOf(mapOf("name" to "Pen", "sku" to "P01")))
        assertEquals("Pen / P01", (result.single().elements.single() as TextElement).text)
    }
}
