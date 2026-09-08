package eu.kanade.tachiyomi.extension.vi.panomic

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Panomic : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    private fun Element.lazyImgUrl(): String? = absUrl("data-lazy-src")
        .ifEmpty { absUrl("data-src") }
        .ifEmpty { absUrl("src") }
        .takeUnless { it.isBlank() || it.startsWith("data:") }
        ?.toPreferredThumbnailUrl()

    private fun String.toPreferredThumbnailUrl(): String = replace(thumb150Regex, "-300x404$1")

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/nhieu-xem-nhat/").asJsoup()
        val mangas = parseListManga(document.select("ul.most-views.single-list-comic li.position-relative"))
        return MangasPage(mangas, hasNextPage = false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        return parseLatestPage(client.get(url).asJsoup())
    }

    private fun parseLatestPage(document: Document): MangasPage {
        val mangas = document.select(".col-md-3.col-xs-6.comic-item")
            .filter { element ->
                element
                    .selectFirst(".comic-title-link a[href], .comic-img a[href]")
                    ?.absUrl("href")
                    ?.contains("/truyen/") == true
            }
            .mapNotNull { element ->
                val linkElement = element.selectFirst(".comic-title-link a[href], .comic-img a[href]")
                    ?: return@mapNotNull null
                val title = element.selectFirst("h3.comic-title")
                    ?.text()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null

                SManga.create().apply {
                    this.title = title
                    setUrlWithoutDomain(linkElement.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")?.lazyImgUrl()
                }
            }

        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("action", "searchtax")
                .add("keyword", query)
                .build()

            return parseSearchApiResponse(client.post(searchAjaxUrl, searchHeaders, formBody))
        }

        val filterUri = filters.firstSelectedFilterUri()
        if (filterUri != null) {
            val pageSuffix = if (page == 1) "" else "page/$page/"
            val filterUrl = "$baseUrl${filterUri.ensureTrailingSlash()}$pageSuffix"
            return parseFilterPage(client.get(filterUrl).asJsoup())
        }

        return getLatestUpdates(page)
    }

    private fun parseSearchApiResponse(response: Response): MangasPage {
        val searchResponse = response.parseAs<SearchResponse>()

        val mangas = searchResponse.data
            .filter { result -> result.link.contains("/truyen/") && result.title.isNotEmpty() }
            .map { result ->
                SManga.create().apply {
                    title = result.title
                    setUrlWithoutDomain(result.link)
                    thumbnail_url = result.img?.toPreferredThumbnailUrl()
                }
            }
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun parseFilterPage(document: Document): MangasPage {
        val items = document.select("#archive-list-table li.position-relative")
            .ifEmpty {
                document.select("ul.single-list-comic li.position-relative")
                    .filter { item -> item.selectFirst("p.super-title a[href*='/truyen/']") != null }
            }
        val mangas = parseListManga(items)
        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a[href]:not([href='#'])") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseListManga(items: List<Element>): List<SManga> {
        return items.mapNotNull { element ->
            val linkElement = element.selectFirst("p.super-title a[href]") ?: return@mapNotNull null
            val title = linkElement.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val mangaUrl = linkElement.absUrl("href")
            if (!mangaUrl.contains("/truyen/")) return@mapNotNull null

            SManga.create().apply {
                this.title = title
                setUrlWithoutDomain(mangaUrl)
                thumbnail_url = element.selectFirst("img.list-left-img, img")?.lazyImgUrl()
            }
        }
    }

    private fun FilterList.firstSelectedFilterUri(): String? = firstInstanceOrNull<GenreFilter>()?.toUriPart()?.ifEmpty { null }
        ?: firstInstanceOrNull<GroupFilter>()?.toUriPart()?.ifEmpty { null }
        ?: firstInstanceOrNull<SeriesTypeFilter>()?.toUriPart()?.ifEmpty { null }
        ?: firstInstanceOrNull<KeywordFilter>()?.toUriPart()?.ifEmpty { null }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "truyen") return null

        val manga = SManga.create().apply {
            setUrlWithoutDomain(url.encodedPath)
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h2.info-title, .info-title")
            ?.text()
            ?.takeIf { it.isNotEmpty() }
            ?: error("Missing manga title")
        thumbnail_url = document.selectFirst("div.col-sm-4 img.img-thumbnail, .detail-info img.img-thumbnail")?.lazyImgUrl()
        author = document.selectFirst("strong:contains(Tác giả) + span")?.text()?.ifEmpty { null }
        status = document.selectFirst("span.comic-stt")?.text()
            ?.let(::parseStatus)
            ?: SManga.UNKNOWN
        genre = document.select("a[href*=/the-loai/]")
            .joinToString { it.text() }
            .ifEmpty { null }
        description = document.selectFirst("div.text-justify")?.text()?.ifEmpty { null }
    }

    private fun parseStatus(status: String): Int {
        val normalizedStatus = status.lowercase(Locale.ROOT)
        return when {
            "đang tiến hành" in normalizedStatus -> SManga.ONGOING
            "hoàn thành" in normalizedStatus -> SManga.COMPLETED
            "trọn bộ" in normalizedStatus -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    private fun parseChapterList(document: Document): List<SChapter> {
        return document.select(".table-scroll table tr")
            .mapNotNull { row ->
                val linkElement = row.selectFirst("a.text-capitalize[href], a[href*='-chap-']") ?: return@mapNotNull null

                SChapter.create().apply {
                    val chapterUrl = linkElement.absUrl("href")
                    setUrlWithoutDomain(chapterUrl)
                    name = parseChapterName(linkElement.text(), chapterUrl)
                    date_upload = row.selectFirst("td.hidden-xs.hidden-sm")?.text()
                        ?.let(::parseChapterDate)
                        ?: 0L
                }
            }
    }

    private fun parseChapterName(rawName: String, chapterUrl: String): String {
        val trailingPart = rawName
            .substringAfterLast("–")
            .substringAfterLast("-")
            .trim()

        chapterNameRegex.find(trailingPart)?.value?.trim()?.let { return it }
        chapterNameRegex.find(rawName)?.value?.trim()?.let { return it }

        chapterUrlNumberRegex.find(chapterUrl)?.groupValues?.getOrNull(1)?.let { chapterNumber ->
            return "Chap $chapterNumber"
        }

        return trailingPart.ifEmpty { rawName.trim() }
    }

    private fun parseChapterDate(date: String): Long = runCatching {
        LocalDate.parse(date, dateFormatShort)
            .atStartOfDay(dateZone)
            .toInstant()
            .toEpochMilli()
    }.recoverCatching {
        LocalDate.parse(date, dateFormatLong)
            .atStartOfDay(dateZone)
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val imageUrls = client.get("$baseUrl${chapter.url}").use { response ->
            val html = response.body.string()
            val loginButton = Jsoup.parse(html).selectFirst(
                "button.v-btn.v-big-btn[data-toggle=modal][data-target='#info-modal']",
            )
            if (loginButton?.text() == "Đăng nhập") {
                throw Exception("Đăng nhập webview bằng tài khoản phù hợp để xem chương này")
            }

            ImageDecryptor.extractImageUrls(
                html,
                response.request.url.toString(),
            )
        }

        return imageUrls.distinct().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl).asJsoup()
        return FilterData(
            genres = document.parseFilterOptions("#nav-tags"),
            groups = document.parseFilterOptions("#nav-teams"),
            series = document.parseFilterOptions("#nav-series"),
            keywords = document.parseFilterOptions("#nav-hashtags"),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<FilterData>())

    private fun Document.parseFilterOptions(selector: String): List<FilterOption> = select("$selector a[href]")
        .mapNotNull { link ->
            val name = link.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            FilterOption(name, link.absUrl("href").toRelativeUrl())
        }
        .distinctBy { it.uri }

    // ============================== Related ===============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        val relatedSection = document.select("h3.blue-title")
            .firstOrNull { it.text() == "Truyện liên quan" }
            ?.parent()
            ?: return emptyList()

        return relatedSection.select(".comic-item-box").mapNotNull { card ->
            val link = card.selectFirst(".comic-title-link a[href*='/truyen/']") ?: return@mapNotNull null
            val title = link.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = card.selectFirst(".comic-img img")?.lazyImgUrl()
            }
        }.distinctBy { it.url }
    }

    private fun String.toRelativeUrl(): String {
        val parsed = toHttpUrlOrNull() ?: return this
        return buildString {
            append(parsed.encodedPath)
            parsed.encodedQuery?.let {
                append('?')
                append(it)
            }
        }
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"

    private val searchAjaxUrl get() = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl()
    private val searchHeaders: Headers
        get() = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
    private val dateFormatShort = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT)
    private val dateFormatLong = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val chapterNameRegex = Regex("Chap\\s*\\d+(\\.\\d+)?", RegexOption.IGNORE_CASE)
    private val chapterUrlNumberRegex = Regex("-chap-(\\d+(?:\\.\\d+)?)/?", RegexOption.IGNORE_CASE)
    private val thumb150Regex = Regex("-150x150(\\.[a-zA-Z0-9]+)$")
}
