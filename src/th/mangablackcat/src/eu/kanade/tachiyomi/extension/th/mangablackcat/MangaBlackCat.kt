package eu.kanade.tachiyomi.extension.th.mangablackcat

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Source
abstract class MangaBlackCat : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
            .build()

        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("latest")
            .addQueryParameter("page", page.toString())
            .build()

        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()

        return parseMangaList(client.get(url).asJsoup())
    }

    private fun parseMangaList(document: Document): MangasPage {
        val manga = document.select("article.manga-card").mapNotNull { it.toSManga() }
        val hasNextPage = document.selectFirst("a[rel=next], a[aria-label*=Next]") != null

        return MangasPage(manga, hasNextPage)
    }

    private fun Element.toSManga(): SManga? {
        val link = selectFirst("a[href*=/manga/]") ?: return null
        val image = selectFirst("img")
        val rawTitle = image?.attr("alt")?.ifBlank { null }
            ?: selectFirst("h3")?.text()
            ?: link.attr("title")

        return SManga.create().apply {
            title = rawTitle.trim()
            thumbnail_url = image?.imgAttr()
            setUrlWithoutDomain(link.attr("abs:href"))
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga)
        val document = client.get(mangaUrl).asJsoup()

        return SMangaUpdate(
            mangaDetailsParse(document, manga),
            if (fetchChapters) chapterListParse(document, mangaUrl) else chapters,
        )
    }

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = manga.apply {
        title = document.selectFirst("article h1, main h1")?.text().orEmpty()
        thumbnail_url = document.selectFirst("article figure img, main figure img")?.imgAttr()
        author = document.selectFirst("article span span.text-base-content, main span span.text-base-content")
            ?.text()
            ?.takeUnless { it.isBlank() }
        status = parseStatus(
            document.select("article span, main span")
                .firstOrNull { element ->
                    val text = element.text()
                    text.contains("กำลังอัพเดท") || text.contains("จบแล้ว")
                }
                ?.text(),
        )
        description = document.select("article [class*=leading-relaxed], main [class*=leading-relaxed]")
            .map { it.text() }
            .firstOrNull { it.length > 80 }
            .orEmpty()
    }

    private suspend fun chapterListParse(firstPage: Document, mangaUrl: String): List<SChapter> {
        val requestedPages = mutableSetOf(mangaUrl)
        val chapters = mutableListOf<SChapter>()
        var document = firstPage

        while (true) {
            chapters += parseChapters(document)
            val nextPageUrl = document.nextChapterPageUrl()
            if (nextPageUrl == null || !requestedPages.add(nextPageUrl)) break

            val nextPageHeaders = headersBuilder()
                .set("Referer", document.location())
                .build()
            document = client.get(nextPageUrl, nextPageHeaders).asJsoup()
        }

        return chapters
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
    }

    private fun parseChapters(document: Document): List<SChapter> {
        val slugPath = document.location()
            .substringBefore("?")
            .substringAfter("$baseUrl/manga/", "")
            .removeSuffix("/")

        // The fallback also matches the "first/latest chapter" buttons, so only use it when there are no chapter cards
        return document.select("a.chapter-card-link[data-chapter-number]")
            .map { it.toChapter() }
            .ifEmpty { parseFallbackChapters(document, slugPath) }
    }

    private fun Element.toChapter(): SChapter = SChapter.create().apply {
        val chapterNumber = attr("data-chapter-number").toFloatOrNull()
        setUrlWithoutDomain(attr("abs:href"))
        name = selectFirst("h4")?.text().orEmpty().ifBlank {
            "ตอนที่ ${chapterNumber?.toString()?.removeSuffix(".0").orEmpty()}"
        }
        chapter_number = chapterNumber ?: parseChapterNumber(url)
        date_upload = selectFirst("p")?.text().parseChapterDate()
    }

    private fun parseFallbackChapters(document: Document, slugPath: String): List<SChapter> {
        if (slugPath.isBlank()) return emptyList()

        val chapterUrlRegex = Regex("""/manga/${Regex.escape(slugPath)}/(\d+(?:\.\d+)?)/*$""")

        return document.select("a[href*=/manga/]")
            .mapNotNull { link ->
                val href = link.attr("abs:href")
                val chapterNumber = chapterUrlRegex.find(href.substringBefore("?"))
                    ?.groupValues
                    ?.get(1)
                    ?.toFloatOrNull()
                    ?: return@mapNotNull null

                val text = link.text()
                if (!text.contains("ตอน") && !text.contains(chapterNumber.toString().removeSuffix(".0"))) {
                    return@mapNotNull null
                }

                SChapter.create().apply {
                    setUrlWithoutDomain(href)
                    name = text.ifBlank { "ตอนที่ ${chapterNumber.toString().removeSuffix(".0")}" }
                    chapter_number = chapterNumber
                }
            }
    }

    private fun Document.nextChapterPageUrl(): String? = selectFirst("nav[aria-label='Pagination Navigation'] a[rel=next]")
        ?.attr("abs:href")
        ?.takeUnless { it.isBlank() }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val chapterUrl = document.location()

        val bootPages = BOOT_JSON_REGEX.findAll(document.html())
            .mapNotNull { match -> decodeBootImage(match.groupValues[1]) }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, chapterUrl, imageUrl) }
            .toList()

        if (bootPages.isNotEmpty()) return bootPages

        return document.select("main img[src], .reader-protected img[src]")
            .mapNotNull { img -> img.imgAttr().takeUnless { it.isBlank() } }
            .filterNot { "/storage/chapter-thumbnails/" in it }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, chapterUrl, imageUrl) }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
        .header("Referer", page.url)
        .build()

    private fun parseStatus(status: String?): Int = when (val normalizedStatus = status?.lowercase(Locale.ROOT)) {
        null -> SManga.UNKNOWN
        else -> when {
            normalizedStatus.contains("กำลังอัพเดท") || normalizedStatus.contains("ongoing") -> SManga.ONGOING
            normalizedStatus.contains("จบแล้ว") || normalizedStatus.contains("completed") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun String?.parseChapterDate(): Long {
        val date = this?.trim()
        return when {
            date.isNullOrBlank() -> 0L
            date.contains("ago", ignoreCase = true) -> parseRelativeDate(date)
            else -> dateFormat.tryParseDate(date, ZoneId.of("Asia/Bangkok"))
        }
    }

    // e.g. "45m ago", "13h ago", "2d ago", "3w ago", "6mos ago", "1y ago"
    private fun parseRelativeDate(date: String): Long {
        val match = RELATIVE_DATE_REGEX.find(date.lowercase(Locale.ROOT)) ?: return 0L
        val amount = match.groupValues[1].toLong()
        val unit = when (match.groupValues[2]) {
            "s", "sec", "secs", "second", "seconds" -> ChronoUnit.SECONDS
            "m", "min", "mins", "minute", "minutes" -> ChronoUnit.MINUTES
            "h", "hr", "hrs", "hour", "hours" -> ChronoUnit.HOURS
            "d", "day", "days" -> ChronoUnit.DAYS
            "w", "wk", "wks", "week", "weeks" -> ChronoUnit.WEEKS
            "mo", "mos", "month", "months" -> ChronoUnit.MONTHS
            "y", "yr", "yrs", "year", "years" -> ChronoUnit.YEARS
            else -> return 0L
        }
        return ZonedDateTime.now().minus(amount, unit).toInstant().toEpochMilli()
    }

    private fun parseChapterNumber(url: String): Float = url.removeSuffix("/")
        .substringAfterLast("/")
        .toFloatOrNull()
        ?: -1f

    private fun decodeBootImage(rawJsonString: String): String? {
        val decoded = Parser.unescapeEntities(rawJsonString, false).decodeJavaScriptString()
        return try {
            decoded.parseAs<BootImageDto>().toImageUrl()
        } catch (_: Exception) {
            null
        }
    }

    @Serializable
    private class BootImageDto(
        private val image: String? = null,
    ) {
        fun toImageUrl(): String? = image?.takeUnless { it.isBlank() }
    }

    private fun String.decodeJavaScriptString(): String = replace(UNICODE_ESCAPE_REGEX) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }
        .replace("\\/", "/")
        .replace("\\'", "'")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    private companion object {
        val BOOT_JSON_REGEX = """boot:\s*JSON\.parse\('((?:\\'|[^'])*)'\)""".toRegex()
        val UNICODE_ESCAPE_REGEX = """\\u([0-9a-fA-F]{4})""".toRegex()
        val RELATIVE_DATE_REGEX = """(\d+)\s*([a-z]+)\s+ago""".toRegex()
        val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("th"))
    }
}
