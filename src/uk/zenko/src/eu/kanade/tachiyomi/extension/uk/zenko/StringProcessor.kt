package eu.kanade.tachiyomi.extension.uk.zenko

import android.util.Log

object StringProcessor {
    private const val SEPARATOR = "@#%&;№%#&**#!@"

    data class ParsedResult(
        val part: String = "",
        val chapter: String = "",
        val name: String = "",
    )

    private fun parse(input: String?): ParsedResult {
        if (input.isNullOrEmpty()) {
            return ParsedResult()
        }

        val parts = input.split(SEPARATOR)
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

        val formattedChapter = if (chapter.contains('.')) {
            chapter.split('.').joinToString(".") { part ->
                if (part == chapter.split('.').first() && part.length == 1) {
                    part.padStart(2, '0')
                } else {
                    part
                }
            }
        } else {
            if (chapter.length == 1) chapter.padStart(2, '0') else chapter
        }

        val idString = if (partNumber > 0) {
            "$partNumber$formattedChapter"
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
