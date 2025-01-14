package eu.kanade.tachiyomi.extension.uk.zenko

import android.util.Log

object StringProcessor {
    private const val separator = "@#%&;№%#&**#!@"

    data class ParsedResult(
        val part: String = "",
        val chapter: String = "",
        val name: String = "",
    )

    fun parse(input: String?): ParsedResult {
        if (input.isNullOrEmpty()) {
            return ParsedResult()
        }

        val parts = input.split(separator)
        return when (parts.size) {
            3 -> {
                val (part, chapter, name) = parts
                ParsedResult(part, chapter, name)
            }

            2 -> {
                val (part, chapter) = parts
                ParsedResult(part, chapter)
            }

            1 -> {
                val (name) = parts
                ParsedResult(name = name)
            }

            else -> ParsedResult()
        }
    }

    fun assemble(parsed: ParsedResult): String {
        return listOf(parsed.part, parsed.chapter, parsed.name)
            .filter { it.isNotEmpty() }
            .joinToString(separator)
    }

    // gen ID by rule: part + chapter
    // example
    // 1 + 0 = 100
    // 1 + 1 = 101
    // 0 + 10 = 010
    // 1 + 99 = 199
    // 1 + 100.5 = 1100.5
    fun generateId(input: String?): Double {
        if (input.isNullOrEmpty()) {
            return -1.0
        }
        val (part, chapter) = parse(input)

        val partNumber = part.toIntOrNull() ?: 0
        val chapterNumber = chapter.toDoubleOrNull() ?: 0

        val idString = if (partNumber > 0) {
            "$partNumber${ if (chapter.length == 1) chapter.padStart(2, '0') else chapter}"
        } else {
            "$chapterNumber"
        }

        return try {
            idString.toDouble()
        } catch (e: NumberFormatException) {
            Log.d("ZENKO", "Invalid ID format: $idString")
            -1.0
        }
    }

    fun format(input: String?): String {
        val (part, chapter, name) = parse(input)
        val chapterLabel = if (chapter.isNotEmpty()) {
            "Розділ $chapter${if (name.isNotEmpty()) ":" else ""}"
        } else {
            ""
        }
        return listOf(
            if (part.isNotEmpty()) "Том $part" else "",
            chapterLabel,
            name,
        ).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
