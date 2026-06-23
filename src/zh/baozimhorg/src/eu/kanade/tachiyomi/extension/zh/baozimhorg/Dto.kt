package eu.kanade.tachiyomi.extension.zh.baozimhorg

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class ResponseDto<T>(val data: T)

@Serializable
class ChapterListDto(
    private val id: Int,
    private val slug: String,
    private val chapters: List<ChapterDto>,
) {
    fun toChapterList(): List<SChapter> {
        val mangaId = id.toString()
        val mangaSlug = slug
        return chapters.asReversed().map { it.toSChapter(mangaSlug, mangaId) }
    }
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val attributes: AttributesDto,
) {
    fun toSChapter(mangaSlug: String, mangaId: String) = attributes.toSChapter(mangaSlug, mangaId, id.toString())
}

@Serializable
class AttributesDto(
    private val title: String,
    private val slug: String,
    private val updatedAt: String,
) {
    fun toSChapter(mangaSlug: String, mangaId: String, chapterId: String) = SChapter.create().apply {
        url = "$mangaSlug/$slug#$mangaId/$chapterId"
        name = title
        date_upload = parseDate(updatedAt)
    }
}

private val formats = listOf(
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    },
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    },
)

fun parseDate(dateString: String): Long {
    for (format in formats) {
        val date = format.parse(dateString, ParsePosition(0)) ?: continue
        return date.time
    }
    throw IllegalArgumentException("Unable to parse date: $dateString")
}

@Serializable
class PageListDto(val info: PageListInfoDto)

@Serializable
class PageListInfoDto(val images: PageListInfoImagesDto)

@Serializable
class PageListInfoImagesDto(val images: String)

@Serializable
class ImageDto(private val url: String, private val order: Int) {
    fun toPage() = Page(order, imageUrl = "https://f40-1-4.g-mh.online$url")
}

/**
 * The /api/v2/chapter/getinfo endpoint returns the image list as an obfuscated
 * string instead of a plain array. This reverses the site's client-side decoder
 * (assets/runtime/chapter-decoder.js) into the original JSON array of images.
 *
 * Pipeline: strip "J7r" prefix / "nQ" suffix -> split into 3 parts around the
 * "kD" and "W4s" markers -> reorder to part3+part1+part2 -> reverse every 2nd
 * 7-char block -> map the custom alphabet back to standard base64url -> base64
 * decode -> UTF-8 JSON.
 */
object ChapterImageDecoder {
    private const val STD = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val CUSTOM = "_-9876543210abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val PREFIX = "J7r"
    private const val MARKER1 = "kD"
    private const val MARKER2 = "W4s"
    private const val SUFFIX = "nQ"
    private const val GROUP = 7

    // Precomputed lookup: custom-alphabet char code -> standard base64url char (-1 = invalid)
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        for (i in CUSTOM.indices) this[CUSTOM[i].code] = STD[i].code
    }

    fun decode(input: String): String {
        require(input.startsWith(PREFIX) && input.endsWith(SUFFIX)) { "未知的章节数据格式" }
        val body = input.substring(PREFIX.length, input.length - SUFFIX.length)
        val payloadLen = body.length - MARKER1.length - MARKER2.length
        require(payloadLen > 0) { "未知的章节数据格式" }

        val aLen = payloadLen / 3
        val bLen = (payloadLen - aLen) / 2
        val cLen = payloadLen - aLen - bLen

        val part1 = body.substring(0, bLen)
        val marker1 = body.substring(bLen, bLen + MARKER1.length)
        val part2 = body.substring(bLen + MARKER1.length, bLen + MARKER1.length + cLen)
        val marker2 = body.substring(bLen + MARKER1.length + cLen, bLen + MARKER1.length + cLen + MARKER2.length)
        val part3 = body.substring(bLen + MARKER1.length + cLen + MARKER2.length)
        require(marker1 == MARKER1 && marker2 == MARKER2 && part3.length == aLen) { "未知的章节数据格式" }

        val reordered = part3 + part1 + part2
        val standard = mapAlphabet(unzigzag(reordered))
        return String(base64UrlDecode(standard), Charsets.UTF_8)
    }

    private fun unzigzag(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        var block = 0
        while (i < s.length) {
            val chunk = s.substring(i, minOf(i + GROUP, s.length))
            sb.append(if (block % 2 == 1) chunk.reversed() else chunk)
            i += GROUP
            block++
        }
        return sb.toString()
    }

    private fun mapAlphabet(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val mapped = if (ch.code < DECODE_TABLE.size) DECODE_TABLE[ch.code] else -1
            require(mapped >= 0) { "无效的章节数据字符" }
            sb.append(mapped.toChar())
        }
        return sb.toString()
    }

    private fun base64UrlDecode(s: String): ByteArray {
        val pad = (4 - s.length % 4) % 4
        val padded = s + "=".repeat(pad)
        return android.util.Base64.decode(padded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
    }
}
