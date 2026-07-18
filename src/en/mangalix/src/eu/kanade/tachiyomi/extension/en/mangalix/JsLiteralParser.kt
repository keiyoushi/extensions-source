package eu.kanade.tachiyomi.extension.en.mangalix

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

internal class JsLiteralParser(
    private val source: String,
    startIndex: Int,
) {
    private var position = startIndex

    fun parse(): JsonElement {
        skipWhitespace()
        return parseValue()
    }

    private fun parseValue(): JsonElement {
        skipWhitespace()
        return when (peek()) {
            '{' -> parseObject()
            '[' -> parseArray()
            '\'', '"' -> JsonPrimitive(parseString())
            '-', '+', in '0'..'9' -> parseNumber()
            else -> parseKeyword()
        }
    }

    private fun parseObject(): JsonObject {
        expect('{')
        skipWhitespace()

        val values = linkedMapOf<String, JsonElement>()
        if (consume('}')) return JsonObject(values)

        while (true) {
            skipWhitespace()
            val key = when (peek()) {
                '\'', '"' -> parseString()
                else -> parseIdentifier()
            }

            skipWhitespace()
            expect(':')
            values[key] = parseValue()

            skipWhitespace()
            if (consume('}')) break
            expect(',')
            skipWhitespace()
            if (consume('}')) break
        }

        return JsonObject(values)
    }

    private fun parseArray(): JsonArray {
        expect('[')
        skipWhitespace()

        val values = mutableListOf<JsonElement>()
        if (consume(']')) return JsonArray(values)

        while (true) {
            values += parseValue()

            skipWhitespace()
            if (consume(']')) break
            expect(',')
            skipWhitespace()
            if (consume(']')) break
        }

        return JsonArray(values)
    }

    private fun parseString(): String {
        val quote = next()
        val result = StringBuilder()

        while (position < source.length) {
            val char = next()
            if (char == quote) return result.toString()
            if (char != '\\') {
                result.append(char)
                continue
            }

            if (position >= source.length) fail("Unterminated escape sequence")
            when (val escaped = next()) {
                '\'', '"', '\\', '/' -> result.append(escaped)
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'v' -> result.append('\u000B')
                '0' -> result.append('\u0000')
                'x' -> result.append(readHex(2).toChar())
                'u' -> {
                    if (consume('{')) {
                        val end = source.indexOf('}', position)
                        if (end == -1) fail("Unterminated Unicode escape")
                        val codePoint = source.substring(position, end).toIntOrNull(16)
                            ?: fail("Invalid Unicode escape")
                        result.appendCodePoint(codePoint)
                        position = end + 1
                    } else {
                        result.append(readHex(4).toChar())
                    }
                }
                '\n' -> Unit
                '\r' -> consume('\n')
                else -> result.append(escaped)
            }
        }

        fail("Unterminated string")
    }

    private fun parseNumber(): JsonPrimitive {
        val start = position
        if (peek() == '+' || peek() == '-') position++
        while (position < source.length && source[position].isDigit()) position++
        if (position < source.length && source[position] == '.') {
            position++
            while (position < source.length && source[position].isDigit()) position++
        }
        if (position < source.length && source[position] in "eE") {
            position++
            if (position < source.length && source[position] in "+-") position++
            while (position < source.length && source[position].isDigit()) position++
        }

        val number = source.substring(start, position)
        return number.toLongOrNull()?.let(::JsonPrimitive)
            ?: number.toDoubleOrNull()?.let(::JsonPrimitive)
            ?: fail("Invalid number")
    }

    private fun parseKeyword(): JsonElement = when (val value = parseIdentifier()) {
        "true" -> JsonPrimitive(true)
        "false" -> JsonPrimitive(false)
        "null", "undefined" -> JsonNull
        else -> fail("Unsupported value '$value'")
    }

    private fun parseIdentifier(): String {
        val start = position
        while (position < source.length) {
            val char = source[position]
            if (!char.isLetterOrDigit() && char != '_' && char != '$') break
            position++
        }
        if (position == start) fail("Expected identifier")
        return source.substring(start, position)
    }

    private fun readHex(length: Int): Int {
        if (position + length > source.length) fail("Incomplete hexadecimal escape")
        val value = source.substring(position, position + length).toIntOrNull(16)
            ?: fail("Invalid hexadecimal escape")
        position += length
        return value
    }

    private fun skipWhitespace() {
        while (position < source.length && source[position].isWhitespace()) position++
    }

    private fun peek(): Char = source.getOrNull(position) ?: fail("Unexpected end of input")

    private fun next(): Char = peek().also { position++ }

    private fun expect(expected: Char) {
        if (!consume(expected)) fail("Expected '$expected'")
    }

    private fun consume(expected: Char): Boolean {
        if (source.getOrNull(position) != expected) return false
        position++
        return true
    }

    private fun fail(message: String): Nothing = throw IOException("$message at position $position")
}
