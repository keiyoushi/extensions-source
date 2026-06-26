package eu.kanade.tachiyomi.extension.id.cosmicscansid

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val DATE_FORMATS = listOf(
    SimpleDateFormat("MMMM d, yyyy", Locale("id")),
    SimpleDateFormat("MMMM d, yyyy", Locale.US),
    SimpleDateFormat("dd/MM/yy", Locale.US),
    SimpleDateFormat("yyyy-MM-dd", Locale.US),
).onEach { it.isLenient = false }

private val DATE_NUMBER_REGEX = """(\d+)""".toRegex()

private val DATE_REGEX = (
    """(?:Januari|Februari|Maret|April|Mei|Juni|Juli|Agustus|September|Oktober|November|Desember|""" +
        """January|February|March|May|June|July|August|October|December)\s+\d{1,2},\s+\d{4}"""
    ).toRegex(RegexOption.IGNORE_CASE)

private val CHAPTER_NUMBER_REGEX = """(?:chapter|ch\.?|bab)-?\s*(\d+(?:\.\d+)?)""".toRegex(RegexOption.IGNORE_CASE)

private val LEADING_ZERO_CHAPTER_REGEX = """(?:chapter|ch\.?|bab)-?0\d+(?:\.\d+)?""".toRegex(RegexOption.IGNORE_CASE)

internal val AJAX_URL_REGEX = """["']ajaxurl["']\s*:\s*["']([^"']+admin-ajax\.php)["']""".toRegex()

internal val MANGA_ID_REGEXES = listOf(
    """["']mid["']\s*:\s*(\d+)""".toRegex(),
    """post_id\s*=\s*["']?(\d+)""".toRegex(),
    """postid-(\d+)""".toRegex(),
)

private val READER_IMAGES_REGEX = """"images"\s*:\s*(\[[^\]]+])""".toRegex()

private val IMAGE_URL_REGEX = """"([^"]+)"""".toRegex()

internal fun mergeChapters(
    normalChapters: List<SChapter>,
    ajaxChapters: List<SChapter>,
): List<SChapter> {
    val dateByChapter = normalChapters
        .filter { it.chapter_number >= 0F && it.date_upload > 0L }
        .groupBy { it.chapter_number }
        .mapValues { (_, chapters) -> chapters.maxOf { it.date_upload } }

    val chapters = ajaxChapters.ifEmpty { normalChapters }

    return chapters
        .onEach { chapter ->
            if (chapter.date_upload == 0L) {
                chapter.date_upload = dateByChapter[chapter.chapter_number] ?: 0L
            }
        }
        .groupBy { it.chapterKey() }
        .map { (_, chapters) -> chapters.pickBestChapter() }
        .sortedWith(
            compareByDescending<SChapter> { it.chapter_number >= 0F }
                .thenByDescending { it.chapter_number }
                .thenByDescending { it.date_upload },
        )
}

private fun List<SChapter>.pickBestChapter(): SChapter = sortedWith(
    compareByDescending<SChapter> { it.url.hasLeadingZeroChapter() }
        .thenByDescending { it.date_upload },
).first()

private fun SChapter.chapterKey(): String {
    val suffix = if (name.contains("[Novel]", ignoreCase = true)) "-novel" else ""

    return if (chapter_number >= 0F) {
        "chapter-$chapter_number$suffix"
    } else {
        url.normalizeChapterUrl()
    }
}

internal fun chapterNameFrom(url: String, text: String): String {
    val chapter = chapterStringFrom(url, text)
    if (chapter != null) {
        val suffix = if (isNovelChapter(url, text)) " [Novel]" else ""
        return "Chapter $chapter$suffix"
    }

    return url.normalizeChapterUrl()
        .removeSuffix("/")
        .substringAfterLast("/")
        .replace('-', ' ')
        .replaceFirstChar { it.titlecase(Locale.ROOT) }
}

internal fun chapterNumberFrom(url: String, text: String = ""): Float = chapterStringFrom(url, text)?.toFloatOrNull() ?: -1F

private fun chapterStringFrom(url: String, text: String): String? {
    val slug = url.normalizeChapterUrl().removeSuffix("/").substringAfterLast("/")

    return CHAPTER_NUMBER_REGEX.find(slug)?.groupValues?.get(1)
        ?: CHAPTER_NUMBER_REGEX.find(text)?.groupValues?.get(1)
}

private fun String.hasLeadingZeroChapter(): Boolean {
    val slug = normalizeChapterUrl().removeSuffix("/").substringAfterLast("/")
    return LEADING_ZERO_CHAPTER_REGEX.containsMatchIn(slug)
}

private fun isNovelChapter(url: String, text: String): Boolean = url.contains("novel", ignoreCase = true) || text.contains("novel", ignoreCase = true)

internal fun String.normalizeChapterUrl(): String {
    val path = substringBefore("?")
        .substringBefore("#")
        .removeSuffix("/")
        .let { url ->
            if (url.startsWith("http")) {
                "/" + url.substringAfter("://").substringAfter("/")
            } else {
                url
            }
        }

    return if (path.startsWith("/")) "$path/" else "/$path/"
}

internal fun String?.parseCosmicChapterDate(): Long {
    if (isNullOrBlank()) return 0L

    val date = trim()
    val lowerDate = date.lowercase(Locale.ROOT)
    val amount = DATE_NUMBER_REGEX.find(lowerDate)?.groupValues?.get(1)?.toIntOrNull()

    if (lowerDate.contains("hari ini") || lowerDate.contains("today")) {
        return System.currentTimeMillis()
    }

    if (amount != null) {
        val calendar = Calendar.getInstance()
        when {
            lowerDate.contains("menit") || lowerDate.contains("minute") -> calendar.add(Calendar.MINUTE, -amount)
            lowerDate.contains("jam") || lowerDate.contains("hour") -> calendar.add(Calendar.HOUR_OF_DAY, -amount)
            lowerDate.contains("hari") || lowerDate.contains("day") -> calendar.add(Calendar.DATE, -amount)
            lowerDate.contains("minggu") || lowerDate.contains("week") -> calendar.add(Calendar.WEEK_OF_YEAR, -amount)
            lowerDate.contains("bulan") || lowerDate.contains("month") -> calendar.add(Calendar.MONTH, -amount)
            lowerDate.contains("tahun") || lowerDate.contains("year") -> calendar.add(Calendar.YEAR, -amount)
            else -> return date.extractDate().tryParseDate()
        }
        return calendar.timeInMillis
    }

    return date.extractDate().tryParseDate()
}

private fun String.extractDate(): String = DATE_REGEX.find(this)?.value ?: this

private fun String.tryParseDate(): Long {
    DATE_FORMATS.forEach { format ->
        format.tryParse(this).takeIf { it != 0L }?.let { return it }
    }

    return 0L
}

internal fun Document.findCover(): String? = selectFirst(
    listOf(
        ".thumb img",
        ".infomanga > div[itemprop=image] img",
        ".infomanga img",
        ".bigcover img",
        ".poster img",
        ".manga-thumb img",
        ".manga-poster img",
        ".summary_image img",
        ".imgholder img",
        ".cover img",
    ).joinToString(),
)?.imageUrl()
    ?: selectFirst("meta[property='og:image'], meta[name='twitter:image']")
        ?.attr("abs:content")
        ?.takeIf { it.isValidThumbnail() }

private fun Element.imageUrl(): String? = sequenceOf("data-lazy-src", "data-src", "data-cfsrc", "src", "content")
    .map { attr("abs:$it").ifBlank { attr(it) } }
    .firstOrNull { it.isValidThumbnail() }

internal fun String?.isValidThumbnail(): Boolean = !isNullOrBlank() &&
    !startsWith("data:") &&
    !contains("blank", ignoreCase = true) &&
    !contains("placeholder", ignoreCase = true)

internal fun parseReaderScriptImages(document: Document, chapterUrl: String): List<Page> = READER_IMAGES_REGEX.findAll(document.toString())
    .flatMap { match ->
        IMAGE_URL_REGEX.findAll(match.groupValues[1])
    }
    .map { match ->
        match.groupValues[1].replace("\\/", "/")
    }
    .filter { it.isValidPageImage() }
    .distinct()
    .mapIndexed { index, imageUrl ->
        Page(index, chapterUrl, imageUrl)
    }
    .toList()

internal fun String.isValidPageImage(): Boolean = startsWith("http") &&
    !startsWith("data:") &&
    !contains("/wp-content/uploads/2026/02/", ignoreCase = true) &&
    !contains("BRIO4D", ignoreCase = true)
