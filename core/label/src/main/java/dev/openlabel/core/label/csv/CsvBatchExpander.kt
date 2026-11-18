package dev.openlabel.core.label.csv

import dev.openlabel.core.label.model.LabelDocument
import dev.openlabel.core.label.model.TextElement

object CsvBatchExpander {
    /** Replaces {{column}} placeholders inside text elements with a CSV row. */
    fun expand(template: LabelDocument, rows: List<Map<String, String>>): List<LabelDocument> = rows.mapIndexed { index, row ->
        val normalized = row.mapKeys { it.key.trim() }
        template.copy(
            name = "${template.name} #${index + 1}",
            elements = template.elements.map { element ->
                if (element is TextElement) {
                    element.copy(text = replaceTokens(element.text, normalized))
                } else element
            },
        )
    }

    private fun replaceTokens(source: String, values: Map<String, String>): String =
        Regex("\\{\\{([^{}]+)}}").replace(source) { match -> values[match.groupValues[1].trim()] ?: match.value }
}
