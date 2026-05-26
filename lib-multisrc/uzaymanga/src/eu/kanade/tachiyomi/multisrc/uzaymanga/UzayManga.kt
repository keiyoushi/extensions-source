package eu.kanade.tachiyomi.multisrc.uzaymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

abstract class UzayManga(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    override val versionId: Int,
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1, TimeUnit.SECONDS)
        .rateLimit(4, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaParse(response: Response): MangasPage = response.asJsoup().parseBrowseMangaPage()

    override fun popularMangaSelector() = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesParse(response: Response): MangasPage = response.asJsoup().parseBrowseMangaPage()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val slug = query.substringAfter(URL_SEARCH_PREFIX)
            return fetchMangaBySlug(slug)
        }

        val directUrl = query.toHttpUrlOrNull()
        if (directUrl != null && directUrl.host == baseUrl.toHttpUrl().host) {
            val segments = directUrl.pathSegments
            val slug = segments.getOrNull(1)
            if (segments.firstOrNull() == "manga" && !slug.isNullOrBlank()) {
                return fetchMangaBySlug(slug)
            }
        }

        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return super.fetchSearchManga(page, query, filters)
        }

        return client.newCall(buildSearchRequest(trimmedQuery, "contains"))
            .asObservableSuccess()
            .flatMap { response ->
                val mangas = searchMangaParse(response).mangas
                if (mangas.isNotEmpty()) {
                    Observable.just(MangasPage(mangas, false))
                } else {
                    client.newCall(buildSearchRequest(trimmedQuery, "start"))
                        .asObservableSuccess()
                        .map { fallbackResponse -> searchMangaParse(fallbackResponse) }
                }
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = buildSearchRequest(query, "contains")

    override fun searchMangaParse(response: Response): MangasPage = MangasPage(response.asJsoup().parseMangaItemList(), false)

    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun searchMangaSelector() = throw UnsupportedOperationException()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val series = document.parseBookSeriesLd()
        title = series?.mangaName ?: document.selectFirst("h1")!!.text()
        thumbnail_url = (series?.mangaImage ?: document.selectFirst("meta[property=og:image]")?.attr("content"))
            ?.toThumbnailUrl()
        genre = document.parseGenres()
        description = document.parseDescription()
        status = parseStatus(document)
        setUrlWithoutDomain(document.location())
    }

    override fun chapterListSelector() = CHAPTER_LIST_SELECTOR

    override fun chapterFromElement(element: Element) = element.toSChapter("")

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaTitle = document.parseBookSeriesLd()?.mangaName
            ?: document.selectFirst("h1")?.text()?.trim()
                .orEmpty()
        return document.select(chapterListSelector())
            .map { it.toSChapter(mangaTitle) }
            .sortedByDescending { it.chapter_number }
    }

    private fun Element.toSChapter(mangaTitle: String) = SChapter.create().apply {
        name = attr("title").cleanChapterName(mangaTitle)
        chapter_number = parseChapterNumber()
        date_upload = parseChapterDate()
        setUrlWithoutDomain(absUrl("href"))
    }

    private fun String.cleanChapterName(mangaTitle: String): String {
        var name = trim()
        if (mangaTitle.isNotEmpty() && name.startsWith(mangaTitle, ignoreCase = true)) {
            name = name.substring(mangaTitle.length).trim()
        }
        if (name.endsWith(CHAPTER_NAME_SUFFIX, ignoreCase = true)) {
            name = name.removeSuffix(CHAPTER_NAME_SUFFIX).trim()
        }
        return name
    }

    override fun pageListParse(document: Document): List<Page> = document.select(".manga-reader-container .ep-item img[src]").mapIndexed { index, img ->
        Page(index, url = document.location(), imageUrl = img.absUrl("src"))
    }

    override fun imageRequest(page: Page): Request = GET(
        page.imageUrl!!,
        headers.newBuilder().set("Referer", page.url).build(),
    )

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    private fun fetchMangaBySlug(slug: String): Observable<MangasPage> {
        val url = "$baseUrl/manga/$slug"
        return client.newCall(GET(url, headers)).asObservableSuccess().map { response ->
            val document = response.asJsoup()
            when {
                isMangaPage(document) -> MangasPage(listOf(mangaDetailsParse(document)), false)
                else -> MangasPage(emptyList(), false)
            }
        }
    }

    private fun isMangaPage(document: Document): Boolean = document.parseBookSeriesLd() != null || document.selectFirst("h1") != null

    private fun buildSearchRequest(query: String, searchMode: String): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("searchMode", searchMode)
            .build()
        return GET(url, headers)
    }

    private fun Document.parseBrowseMangaPage(): MangasPage {
        val mangas = parseMangaItemList()
        return MangasPage(mangas, mangas.size >= PAGE_SIZE)
    }

    private fun Document.parseMangaItemList(): List<SManga> {
        val htmlThumbnails = parseHtmlThumbnailMap()
        return select("script[type=application/ld+json]")
            .mapNotNull { script ->
                runCatching { script.data().parseAs<ItemListLd>() }.getOrNull()
            }
            .filter { it.listName != PAGINATION_LIST_NAME }
            .flatMap { it.itemListElement.orEmpty() }
            .mapNotNull { it.item }
            .filter { it.hasRequiredFields }
            .map { it.toSMangaWithThumbnails(htmlThumbnails) }
    }

    private fun BookSeriesLd.toSMangaWithThumbnails(htmlThumbnails: Map<String, String>): SManga {
        val slug = mangaUrl!!.extractMangaSlug()
        val thumbnail = htmlThumbnails[slug]?.toThumbnailUrl()
            ?: mangaImage?.toThumbnailUrl()
        return toSManga(thumbnail).apply {
            setUrlWithoutDomain(mangaUrl!!)
        }
    }

    private fun Document.parseHtmlThumbnailMap(): Map<String, String> = buildMap {
        select(BROWSE_MANGA_CARD_SELECTOR).forEach { anchor ->
            val slug = anchor.attr("href").extractMangaSlug() ?: return@forEach
            val thumbnail = anchor.selectFirst("img")?.absUrl("src") ?: return@forEach
            put(slug, thumbnail)
        }
    }

    private fun Document.parseBookSeriesLd(): BookSeriesLd? {
        val candidates = select("script[type=application/ld+json]")
            .mapNotNull { script ->
                runCatching { script.data().parseAs<BookSeriesLd>() }.getOrNull()
            }
            .filter { it.hasRequiredFields }
        return candidates.firstOrNull { it.isBookSeries } ?: candidates.firstOrNull()
    }

    private fun String.extractMangaSlug(): String? {
        val path = toHttpUrlOrNull()?.encodedPath ?: this
        val slug = path.trim('/').substringAfter("manga/").substringBefore('/')
        return slug.takeIf { it.isNotBlank() }
    }

    private fun String.toThumbnailUrl(): String {
        val path = trim()
        if (path.isEmpty()) return path
        val absolute = when {
            path.startsWith("http", ignoreCase = true) -> path
            path.startsWith("/thumbnails/") ||
                path.startsWith("/images/thumbnail/") ||
                path.startsWith("/thumbnail/") -> "$baseUrl/upload$path"
            path.startsWith("/") -> baseUrl + path
            else -> "$baseUrl/$path"
        }
        return applyUploadPrefix(absolute)
    }

    protected open fun applyUploadPrefix(url: String): String {
        if (url.contains("/upload/")) return url
        return when {
            url.contains("/thumbnails/") -> url.replaceFirst("/thumbnails/", "/upload/thumbnails/")
            url.contains("/images/thumbnail/") -> url.replaceFirst("/images/thumbnail/", "/upload/images/thumbnail/")
            url.contains("/thumbnail/") -> url.replaceFirst("/thumbnail/", "/upload/thumbnail/")
            else -> url
        }
    }

    private fun Document.parseGenres(): String = select("a[href^=/manga?category]").joinToString { it.text().trim() }

    private fun Document.parseDescription(): String? {
        val parts = buildList {
            selectFirst("h2:contains(Seri Özeti)")?.parent()?.selectFirst("p")?.text()?.trim()?.let(::add)
            parseInfoRow("Orijinal İsim")?.let { add("Orijinal İsim: $it") }
            parseInfoRow("Diğer İsimler")?.let { add("Diğer İsimler: $it") }
        }
        return parts.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun Document.parseInfoRow(label: String): String? {
        val row = select("div.flex.justify-between").firstOrNull { element ->
            element.selectFirst("span")?.text()?.contains(label, ignoreCase = true) == true
        } ?: return null
        return row.select("span").last()?.let { span ->
            span.attr("title").takeIf { it.isNotBlank() } ?: span.text().trim()
        }?.takeIf { it.isNotBlank() }
    }

    private fun parseStatus(document: Document): Int {
        val statusText = (
            document.selectFirst("span:contains(Durum)")?.parent()?.text()
                ?: document.selectFirst(":contains(Durum)")?.text()
                ?: ""
            ).lowercase()
        return when {
            "tamamland" in statusText -> SManga.COMPLETED
            "ara ver" in statusText -> SManga.ON_HIATUS
            "bırakıld" in statusText || "birakildi" in statusText -> SManga.CANCELLED
            "devam" in statusText -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private fun Element.parseChapterDate(): Long {
        val dateText = DATE_REGEX.find(text())?.value ?: return 0L
        return dateFormat.tryParse(dateText)
    }

    private fun Element.parseChapterNumber(): Float = selectFirst("> div > div:first-child")!!.text().trim().toFloat()

    companion object {
        const val URL_SEARCH_PREFIX = "slug:"
        private const val PAGE_SIZE = 20
        private const val PAGINATION_LIST_NAME = "Sayfalama"
        private const val BROWSE_MANGA_CARD_SELECTOR =
            "a[href^=/manga/]:not([href*=-bolum-oku]):has(img)"
        private const val CHAPTER_NAME_SUFFIX = "Manga Oku"
        private const val CHAPTER_LIST_SELECTOR = "a[href^=/manga/]:not(div.grid > a)"
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr"))
        val DATE_REGEX = """\d{2}\.\d{2}\.\d{4}""".toRegex()
    }
}
