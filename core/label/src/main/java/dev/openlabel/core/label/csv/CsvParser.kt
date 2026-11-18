package dev.openlabel.core.label.csv

/** Small RFC-4180-style parser; handles quoted commas, escaped quotes and CRLF records. */
object CsvParser {
    fun parse(input: String): List<List<String>> {
        val text = input.removePrefix("\uFEFF")
        if (text.isEmpty()) return emptyList()
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var afterQuote = false
        var index = 0

        fun finishCell() {
            row += cell.toString()
            cell.setLength(0)
            afterQuote = false
        }
        fun finishRow() {
            finishCell()
            rows += row.toList()
            row = mutableListOf()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                quoted && char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                char == '"' && !quoted && cell.isEmpty() && !afterQuote -> quoted = true
                char == '"' && quoted -> {
                    quoted = false
                    afterQuote = true
                }
                char == ',' && !quoted -> finishCell()
                (char == '\n' || char == '\r') && !quoted -> {
                    finishRow()
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                afterQuote && !quoted && char.isWhitespace() -> Unit
                afterQuote && !quoted -> throw IllegalArgumentException("Недопустимый символ после кавычки в позиции $index")
                else -> cell.append(char)
            }
            index++
        }
        require(!quoted) { "Незакрытая кавычка в CSV" }
        if (cell.isNotEmpty() || row.isNotEmpty() || text.lastOrNull() == ',') finishRow()
        return rows
    }

    fun parseWithHeader(input: String): List<Map<String, String>> {
        val rows = parse(input)
        if (rows.isEmpty()) return emptyList()
        val headers = rows.first().map { it.trim() }
        require(headers.isNotEmpty() && headers.all { it.isNotBlank() }) { "Пустое имя столбца CSV" }
        require(headers.distinct().size == headers.size) { "Заголовки CSV должны быть уникальными" }
        return rows.drop(1).filter { it.any(String::isNotBlank) }.map { values ->
            headers.mapIndexed { column, header -> header to values.getOrElse(column) { "" } }.toMap()
        }
    }
}
