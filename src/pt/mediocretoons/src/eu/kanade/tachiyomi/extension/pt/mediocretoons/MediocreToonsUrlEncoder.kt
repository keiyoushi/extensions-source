package eu.kanade.tachiyomi.extension.pt.mediocretoons

import java.util.Calendar
import java.util.TimeZone

object MediocreToonsUrlEncoder {

    private const val WORK_SALT = "mediocre-work-salt-2024"
    private const val SECRET = "mediocre-secret-2024"
    private const val TOKEN_SALT = "mediocre-salt-2024"

    fun encodeMangaUrl(id: Int): String {
        val encodedId = encodeId(id)
        val token = generateToken(id, "work")
        return "/obra/$encodedId/$token"
    }

    fun encodeChapterUrl(id: Int): String {
        val encodedId = encodeId(id)
        val token = generateToken(id, "chapter")
        return "/chapter/$encodedId/$token"
    }

    private fun encodeId(id: Int): String {
        val idStr = id.toString()
        val base36Id = id.toString(36)

        var hash1 = 0
        var hash2 = 0
        val combined = "$idStr-$WORK_SALT"

        for (i in combined.indices) {
            val charCode = combined[i].code
            hash1 = ((hash1 shl 5) - hash1 + charCode) and Int.MAX_VALUE.inv().inv()
            hash2 = ((hash2 shl 3) - hash2 + charCode + i) and Int.MAX_VALUE.inv().inv()
        }

        val hash1Str = kotlin.math.abs(hash1).toString(36)
        val hash2Str = kotlin.math.abs(hash2).toString(36)

        val letters = StringBuilder()
        for (i in 0 until 8) {
            val charIndex = (id * (i + 1) * 7) % 26
            letters.append(('a'.code + charIndex).toChar())
        }

        val parts = buildString {
            append(hash1Str.take(6))
            append(letters)
            append(base36Id)
            append(hash2Str.take(6))
        }

        val chars = parts.toCharArray()
        for (i in chars.size - 1 downTo 1) {
            val swapIndex = (id * 7 + i * 11) % (i + 1)
            val temp = chars[i]
            chars[i] = chars[swapIndex]
            chars[swapIndex] = temp
        }

        return String(chars).take(20)
    }

    private fun generateToken(id: Int, type: String): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val year = calendar.get(Calendar.YEAR)
        val week = calendar.get(Calendar.WEEK_OF_YEAR)

        val baseString = "$year-W$week-$type-$id-$SECRET"
        val hash1 = computeHash(baseString)
        val hash2 = computeHash("$baseString-$hash1-extra")

        val combined = hash1 + hash2
        val chars = combined.toCharArray()

        for (i in chars.size - 1 downTo 1) {
            val swapIndex = (id * 7 + i * 11 + week * 13) % (i + 1)
            val temp = chars[i]
            chars[i] = chars[swapIndex]
            chars[swapIndex] = temp
        }

        return String(chars).take(24)
    }

    private fun computeHash(input: String): String {
        val salted = input + TOKEN_SALT

        var hash1 = 0
        for (char in salted) {
            val charCode = char.code
            hash1 = ((hash1 shl 5) - hash1 + charCode) and Int.MAX_VALUE.inv().inv()
        }

        var hash2 = 0
        val reversed = salted.reversed()
        for (char in reversed) {
            val charCode = char.code
            hash2 = ((hash2 shl 5) - hash2 + charCode) and Int.MAX_VALUE.inv().inv()
        }

        return kotlin.math.abs(hash1 xor hash2).toString(36)
    }
}
