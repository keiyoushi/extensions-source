package eu.kanade.tachiyomi.extension.th.onemanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class MangaBlackCat : HttpSource() {

    override val supportsLatest = true

    // migration from OneManga
    override val id: Long = 2248402620929558947L

    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")).apply {
        timeZone = TimeZone.getTimeZone("Asia/Bangkok")
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("latest")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

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

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
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
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservableSuccess()
        .flatMap { response ->
            chapterListParse(response, mutableSetOf(getMangaUrl(manga)))
        }
        .map { chapters ->
            chapters
                .distinctBy { it.url }
                .sortedByDescending { it.chapter_number }
        }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    private fun chapterListParse(response: Response, requestedPages: MutableSet<String>): Observable<List<SChapter>> {
        val document = response.asJsoup()
        val chapters = parseChapters(document)
        val nextPageUrl = document.nextChapterPageUrl()

        if (nextPageUrl == null || !requestedPages.add(nextPageUrl)) {
            return Observable.just(chapters)
        }

        val nextPageRequest = GET(
            nextPageUrl,
            headersBuilder()
                .set("Referer", response.request.url.toString())
                .build(),
        )

        return client.newCall(nextPageRequest)
            .asObservableSuccess()
            .flatMap { nextPageResponse ->
                chapterListParse(nextPageResponse, requestedPages)
                    .map { nextPageChapters -> chapters + nextPageChapters }
            }
    }

    private fun parseChapters(document: Document): List<SChapter> {
        val slugPath = document.location()
            .substringBefore("?")
            .substringAfter("$baseUrl/manga/", "")
            .removeSuffix("/")

        return document.select("a.chapter-card-link[data-chapter-number]")
            .map { it.toChapter() } + parseFallbackChapters(document, slugPath)
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

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    private fun Document.nextChapterPageUrl(): String? = selectFirst("nav[aria-label='Pagination Navigation'] a[rel=next]")
        ?.attr("abs:href")
        ?.takeUnless { it.isBlank() }

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
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

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

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
            date.contains("ago", ignoreCase = true) -> 0L
            else -> dateFormat.tryParse(date)
        }
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
    }
}
