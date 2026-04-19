package eu.kanade.tachiyomi.extension.en.arvenscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat

fun PostSummaryDto.toSMangaSummary(): SManga {
    val cleanTitle = postTitle.trim()
    if (cleanTitle.isEmpty()) throw Exception(MISSING_TITLE_MESSAGE)

    return SManga.create().apply {
        url = "$slug#$id"
        title = cleanTitle
        thumbnail_url = featuredImage
        genre = genres.joinToString { it.name }
        status = mapStatus(seriesStatus)
    }
}

fun Document.toSMangaDetailsModel(): SManga {
    val props = findSeriesProps() ?: throw Exception("Could not find series details")

    val title = extractJsonStringField(props, "postTitle")?.trim()
        ?: selectFirst("meta[property=og:title]")?.attr("content")?.trim()
        ?: throw Exception(MISSING_TITLE_MESSAGE)

    val slug = extractJsonStringField(props, "slug")?.trim().orEmpty()
    val id = extractJsonIntField(props, "id")

    val postContent = extractJsonStringField(props, "postContent")
    val alternativeTitles = extractJsonStringField(props, "alternativeTitles")
    val author = extractJsonStringField(props, "author")?.trim()?.takeUnless { it.isEmpty() }
    val studio = extractJsonStringField(props, "studio")?.trim()?.takeUnless { it.isEmpty() }
    val artist = extractJsonStringField(props, "artist")?.trim()?.takeUnless { it.isEmpty() }
    val featuredImage = extractJsonStringField(props, "featuredImage")?.trim()?.takeUnless { it.isEmpty() }
    val seriesType = extractJsonStringField(props, "seriesType")
    val seriesStatus = extractJsonStringField(props, "seriesStatus")
    val genreNames = extractGenreNames(props)

    return SManga.create().apply {
        url = if (slug.isNotEmpty() && id != null) "$slug#$id" else slug
        this.title = title
        description = buildDescription(postContent, alternativeTitles)
        this.author = author ?: studio
        this.artist = artist
        genre = buildGenre(seriesType, genreNames)
        status = mapStatus(seriesStatus)
        thumbnail_url = featuredImage
            ?: selectFirst("meta[property=og:image]")?.attr("content")?.takeUnless { it.isBlank() }
        initialized = true
    }
}

fun Document.parseChapterList(
    mangaSlug: String,
    dateFormat: SimpleDateFormat,
    showLocked: Boolean,
): List<SChapter> {
    val props = findSeriesProps() ?: return emptyList()
    val chaptersBlock = extractChaptersBlock(props) ?: return emptyList()

    return parseChapterEntries(chaptersBlock)
        .filter { showLocked || (it.isAccessible != false && it.isLocked != true) }
        .map { it.toSChapterModel(mangaSlug, dateFormat) }
}

fun Document.parsePageImages(): List<String> = select("img[src]")
    .asSequence()
    .map { it.absUrl("src").ifEmpty { it.attr("src") } }
    .filter { url ->
        url.contains("/upload/series/", ignoreCase = true) &&
            url.contains("/page-", ignoreCase = true)
    }
    .distinct()
    .toList()

fun Document.extractSeriesSlug(): String? {
    val props = findSeriesProps() ?: return null
    return extractJsonStringField(props, "slug")?.trim()?.takeUnless { it.isEmpty() }
}

private fun Document.findSeriesProps(): String? = select("astro-island[props]")
    .map { it.attr("props") }
    .firstOrNull { it.contains("\"postContent\"") && it.contains("\"chapters\":[1,") }

private fun extractJsonStringField(json: String, field: String): String? {
    val regex = Regex("\"${Regex.escape(field)}\":\\[0,(null|\"((?:[^\"\\\\]|\\\\.)*)\")\\]")
    val match = regex.find(json) ?: return null
    if (match.groupValues[1] == "null") return null
    return decodeJsonString(match.groupValues[2])
}

private fun extractJsonIntField(json: String, field: String): Int? {
    val regex = Regex("\"${Regex.escape(field)}\":\\[0,(-?\\d+)\\]")
    return regex.find(json)?.groupValues?.get(1)?.toIntOrNull()
}

private fun extractChaptersBlock(json: String): String? {
    val marker = "\"chapters\":[1,"
    val markerIdx = json.indexOf(marker)
    if (markerIdx < 0) return null

    val innerArrayStart = json.indexOf('[', markerIdx + marker.length)
    if (innerArrayStart < 0) return null

    val innerArrayEnd = findMatchingBracket(json, innerArrayStart) ?: return null
    return json.substring(innerArrayStart + 1, innerArrayEnd)
}

private fun findMatchingBracket(text: String, openIdx: Int): Int? {
    val openChar = text[openIdx]
    val closeChar = when (openChar) {
        '[' -> ']'
        '{' -> '}'
        else -> return null
    }

    var depth = 0
    var inString = false
    var escape = false
    var i = openIdx

    while (i < text.length) {
        val c = text[i]
        when {
            escape -> escape = false
            c == '\\' -> escape = true
            c == '"' -> inString = !inString
            !inString && c == openChar -> depth++
            !inString && c == closeChar -> {
                depth--
                if (depth == 0) return i
            }
        }
        i++
    }
    return null
}

private data class ChapterEntry(
    val id: Int,
    val slug: String,
    val number: String,
    val title: String?,
    val createdAt: String?,
    val isLocked: Boolean?,
    val isAccessible: Boolean?,
)

private fun parseChapterEntries(chaptersBlock: String): List<ChapterEntry> {
    val entries = mutableListOf<ChapterEntry>()
    var i = 0

    while (i < chaptersBlock.length) {
        val startToken = "[0,{"
        val tokenIdx = chaptersBlock.indexOf(startToken, i)
        if (tokenIdx < 0) break

        val objectStart = tokenIdx + startToken.length - 1
        val objectEnd = findMatchingBracket(chaptersBlock, objectStart) ?: break
        val chapterJson = chaptersBlock.substring(objectStart, objectEnd + 1)

        val id = extractJsonIntField(chapterJson, "id")
        val slug = extractJsonStringField(chapterJson, "slug")

        if (id != null && !slug.isNullOrBlank()) {
            val rawNumber = Regex("\"number\":\\[0,([^\\]]+)\\]").find(chapterJson)?.groupValues?.get(1)
                ?.trim()
                ?.trim('"')
                .orEmpty()

            entries += ChapterEntry(
                id = id,
                slug = slug,
                number = rawNumber,
                title = extractJsonStringField(chapterJson, "title"),
                createdAt = extractJsonStringField(chapterJson, "createdAt"),
                isLocked = extractJsonBoolField(chapterJson, "isLocked"),
                isAccessible = extractJsonBoolField(chapterJson, "isAccessible"),
            )
        }

        i = objectEnd + 1
    }

    return entries.distinctBy { it.id }
}

private fun extractJsonBoolField(json: String, field: String): Boolean? {
    val regex = Regex("\"${Regex.escape(field)}\":\\[0,(true|false|null)\\]")
    val match = regex.find(json) ?: return null
    return when (match.groupValues[1]) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

private fun ChapterEntry.toSChapterModel(mangaSlug: String, dateFormat: SimpleDateFormat): SChapter {
    val fallbackNumber = slug.substringAfter("chapter-", "")
    val chapterNumberText = number.ifEmpty { fallbackNumber }
    val chapterTitle = title?.trim().orEmpty()

    return SChapter.create().apply {
        url = "$mangaSlug/$slug#$id"
        name = buildString {
            if (isAccessible == false || isLocked == true) {
                append("🔒 ")
            }

            append("Chapter")
            if (chapterNumberText.isNotEmpty()) {
                append(' ')
                append(chapterNumberText)
            }

            if (chapterTitle.isNotEmpty()) {
                append(" - ")
                append(chapterTitle)
            }
        }
        chapterNumberText.toFloatOrNull()?.let { chapter_number = it }
        createdAt?.let { date_upload = dateFormat.tryParse(it) }
    }
}

private fun decodeJsonString(escaped: String): String {
    val sb = StringBuilder(escaped.length)
    var i = 0
    while (i < escaped.length) {
        val c = escaped[i]
        if (c == '\\' && i + 1 < escaped.length) {
            when (val next = escaped[i + 1]) {
                '"', '\\', '/' -> sb.append(next)
                'b' -> sb.append('\b')
                'f' -> sb.append('\u000C')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                'u' -> {
                    if (i + 5 < escaped.length) {
                        val code = escaped.substring(i + 2, i + 6).toIntOrNull(16)
                        if (code != null) {
                            sb.append(code.toChar())
                            i += 6
                            continue
                        }
                    }
                    sb.append(c)
                }
                else -> sb.append(next)
            }
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private fun extractGenreNames(props: String): List<String> {
    val marker = "\"genres\":[1,"
    val markerIdx = props.indexOf(marker)
    if (markerIdx < 0) return emptyList()

    val innerStart = props.indexOf('[', markerIdx + marker.length)
    if (innerStart < 0) return emptyList()

    val innerEnd = findMatchingBracket(props, innerStart) ?: return emptyList()
    val block = props.substring(innerStart, innerEnd + 1)

    return Regex("\"name\":\\[0,\"((?:[^\"\\\\]|\\\\.)*)\"\\]")
        .findAll(block)
        .map { decodeJsonString(it.groupValues[1]) }
        .toList()
}

private fun buildDescription(postContent: String?, alternativeTitles: String?): String? {
    val synopsis = postContent
        ?.takeIf { it.isNotBlank() }
        ?.replace("<br>", "\n")
        ?.replace("<br/>", "\n")
        ?.replace("<br />", "\n")
        ?.let { Jsoup.parse(it).text() }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val altTitles = alternativeTitles
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toList()
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString("\n")

    return buildString {
        synopsis?.let { append(it) }
        altTitles?.let {
            if (isNotEmpty()) append("\n\n")
            append("Alternative titles:\n")
            append(it)
        }
    }.takeIf { it.isNotBlank() }
}

private fun buildGenre(seriesType: String?, genres: List<String>): String? {
    val values = mutableListOf<String>()

    when (seriesType?.uppercase()) {
        "MANGA" -> values += "Manga"
        "MANHUA" -> values += "Manhua"
        "MANHWA" -> values += "Manhwa"
    }

    values += genres

    val out = values.distinct().joinToString()
    return out.takeIf { it.isNotBlank() }
}

private fun mapStatus(status: String?): Int = when (status) {
    "ONGOING", "COMING_SOON", "MASS_RELEASED" -> SManga.ONGOING
    "COMPLETED" -> SManga.COMPLETED
    "CANCELLED", "DROPPED" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
