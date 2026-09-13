package eu.kanade.tachiyomi.extension.vi.truyentvn

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TruyenTVN : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(5)

    private val ajaxHeaders: Headers
        get() = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .build()

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage(client.get(buildPagedUrl(popularPath, page)).asJsoup())

    // =============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(client.get(buildPagedUrl(latestPath, page)).asJsoup())

    // =============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            return parseAjaxSearch(client.post("$baseUrl$ajaxPath", ajaxHeaders, buildSearchBody(query)))
        }

        val searchPath = if (page > 1) "/advanced-search/page/$page" else "/advanced-search"
        val url = "$baseUrl$searchPath".toHttpUrl().newBuilder().apply {
            filters.forEach { filter ->
                when (filter) {
                    is CountryFilter -> addFilter("country", filter.toUriPart())
                    is StatusFilter -> addFilter("status", filter.toUriPart())
                    is SortFilter -> addFilter("orderby", filter.toUriPart())
                    is AgeRatingFilter -> addFilter("age_rating", filter.toUriPart())
                    is ChapterRangeFilter -> addFilter("chapters_range", filter.toUriPart())
                    is CategoryFilter -> addFilter("category", filter.toUriPart())
                    is GenreFilter -> filter.state.forEach { genre ->
                        when (genre.state) {
                            Filter.TriState.STATE_INCLUDE -> addQueryParameter("include_genres[]", genre.slug)
                            Filter.TriState.STATE_EXCLUDE -> addQueryParameter("exclude_genres[]", genre.slug)
                        }
                    }
                    else -> Unit
                }
            }
        }.build()

        return parseMangaPage(client.get(url).asJsoup())
    }

    private fun HttpUrl.Builder.addFilter(name: String, value: String) {
        if (value.isNotEmpty()) addQueryParameter(name, value)
    }

    private fun buildSearchBody(query: String) = FormBody.Builder()
        .add("action", "baka_ajax")
        .add("type", "search_series")
        .add("q", query)
        .build()

    private fun parseAjaxSearch(response: Response): MangasPage {
        val mangaList = response.parseAs<SearchAjaxResponseDto>().series().map { series ->
            SManga.create().apply {
                title = series.title()
                setUrlWithoutDomain(series.url())
                thumbnail_url = series.thumbnail()
            }
        }
        return MangasPage(mangaList, false)
    }

    // =============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.endsWith(".html")) return null

        val mangaSegments = url.pathSegments.takeWhile { !it.startsWith("chapter-") }
        if (mangaSegments.isEmpty()) return null
        val mangaPath = mangaSegments.joinToString(separator = "/", prefix = "/").let { path ->
            if (path.endsWith(".html")) path else "$path.html"
        }

        val manga = SManga.create().apply { setUrlWithoutDomain(mangaPath) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga.apply {
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val cachedMangaId = manga.memo["mangaId"]?.stringOrNull
        val document = if (fetchDetails || (fetchChapters && cachedMangaId == null)) {
            client.get(getMangaUrl(manga)).asJsoup()
        } else {
            null
        }
        val mangaId = cachedMangaId ?: document?.selectFirst("input#post_manga_id")?.attr("value")
        val updatedManga = when {
            fetchDetails -> parseMangaDetails(checkNotNull(document), manga, mangaId)
            mangaId != null && cachedMangaId == null -> manga.apply { memo = memo.withMangaId(mangaId) }
            else -> manga
        }
        val updatedChapters = if (fetchChapters && mangaId != null) fetchChapterList(mangaId) else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun parseMangaDetails(document: Document, manga: SManga, mangaId: String?): SManga {
        val thumbnailFromFirstChapter = mangaId?.let { fetchFirstChapterThumbnail(it) }
        val fallbackThumbnail = document.selectFirst("#ratingModalCover, #series-thumbnail img")?.extractImageUrl()

        return SManga.create().apply {
            setUrlWithoutDomain(manga.url)
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = thumbnailFromFirstChapter ?: fallbackThumbnail
            author = parseAuthor(document)
            genre = document.select("#genres-tags-container a[href]").joinToString { it.text() }
            status = parseStatus(document.selectFirst("span:has(i[title='Trạng thái'])")?.text())
            description = document.selectFirst("#synopsisText")?.text()
            mangaId?.let { memo = manga.memo.withMangaId(it) }
        }
    }

    private fun parseAuthor(document: Document): String? {
        val preferredAuthor = document.selectFirst("span:has(i[title='Tác Giả']) > span")?.text()
        return preferredAuthor?.takeIf { it.isNotEmpty() }
            ?: document.selectFirst("span:has(i[title='Tác Giả'])")?.text()
    }

    private fun parseStatus(statusText: String?): Int {
        val status = statusText?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "đang tiến hành" in status || "đang cập nhật" in status -> SManga.ONGOING
            "hoàn thành" in status -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private suspend fun fetchFirstChapterThumbnail(parentId: String): String? {
        val chapterHtml = fetchChapterPage(parentId, page = 1, perPage = 1)?.html() ?: return null
        return Jsoup.parseBodyFragment(chapterHtml, baseUrl)
            .selectFirst("div.comic-card img")
            ?.extractImageUrl()
    }

    private suspend fun fetchChapterList(parentId: String): List<SChapter> {
        val firstPage = fetchChapterPage(parentId, page = 1) ?: return emptyList()
        val chapterHtml = mutableListOf<String>()
        firstPage.html()?.let(chapterHtml::add)

        val totalPages = extractTotalChapterPages(firstPage.pagination())
        val remainingPages = coroutineScope {
            (2..totalPages)
                .map { page -> async { fetchChapterPage(parentId, page)?.html() } }
                .awaitAll()
        }
        chapterHtml += remainingPages.filterNotNull()

        return chapterHtml.flatMap { html ->
            Jsoup.parseBodyFragment(html, baseUrl)
                .select("div.comic-card > a[href]")
                .map { chapterElement ->
                    SChapter.create().apply {
                        name = chapterElement.attr("title").ifEmpty {
                            chapterElement.selectFirst("h3")!!.text()
                        }
                        setUrlWithoutDomain(chapterElement.absUrl("href"))
                        date_upload = parseChapterDate(
                            chapterElement.selectFirst("div.absolute.top-2.left-2 span, span.text-white")?.text(),
                        )
                    }
                }
        }
    }

    private suspend fun fetchChapterPage(
        parentId: String,
        page: Int,
        order: String = chapterOrderNewest,
        perPage: Int = chaptersPerPage,
    ): ChaptersAjaxDataDto? {
        val formBody = FormBody.Builder()
            .add("action", "baka_ajax")
            .add("type", "load_chapters_paginated")
            .add("parent_id", parentId)
            .add("page", page.toString())
            .add("order", order)
            .add("per_page", perPage.toString())
            .build()

        return client.post("$baseUrl$ajaxPath", ajaxHeaders, formBody)
            .parseAs<ChaptersAjaxResponseDto>()
            .data()
    }

    private fun extractTotalChapterPages(paginationHtml: String?): Int {
        if (paginationHtml.isNullOrBlank()) return 1
        return chapterPageRegex.findAll(paginationHtml)
            .map { it.groupValues[1].toIntOrNull() ?: 1 }
            .maxOrNull() ?: 1
    }

    private fun parseChapterDate(dateText: String?): Long {
        if (dateText.isNullOrBlank()) return 0L
        parseRelativeDate(dateText)?.let { return it }
        return runCatching {
            LocalDate.parse(dateText, chapterDateFormat)
                .atStartOfDay(vietnamZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun parseRelativeDate(dateText: String): Long? {
        val value = dateText.lowercase()
        if ("vừa xong" in value) return Clock.System.now().toEpochMilliseconds()

        val amount = dateNumberRegex.find(value)?.value?.toIntOrNull() ?: return null
        val duration = when {
            "giây" in value -> amount.seconds
            "phút" in value -> amount.minutes
            "giờ" in value -> amount.hours
            "ngày" in value -> amount.days
            "tuần" in value -> (amount * 7).days
            "tháng" in value -> (amount * 30).days
            "năm" in value -> (amount * 365).days
            else -> return null
        }
        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    // ================================ Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val images = document.select("main.webtoon-mode img.page-image")
            .ifEmpty { document.select("#webtoonContainer img.page-image, #webtoonContainer img") }

        return images.mapIndexedNotNull { index, imageElement ->
            imageElement.extractImageUrl()
                .takeIf { it.isNotEmpty() }
                ?.let { Page(index, imageUrl = it) }
        }
    }

    // =============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/advanced-search").asJsoup()
        val categories = document.select("select[name=category] option[value]")
            .mapNotNull { option ->
                val slug = option.attr("value").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                FilterOption(option.text(), slug)
            }
        val script = document
            .selectFirst("script:containsData(window.advancedSearchGenres)")
            ?.data()
        val genres = script
            ?.let { genreDataRegex.find(it)?.groupValues?.get(1) }
            ?.parseAs<List<FilterOption>>()
            .orEmpty()
        return FilterData(categories, genres).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<FilterData>())

    // =============================== Related ===============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return document.select("#similar-series div.comic-card > a[href$='.html']")
            .map { parseMangaElement(it) }
            .distinctBy { it.url }
    }

    // ============================= Utilities =============================

    private fun parseMangaPage(document: Document): MangasPage {
        val mangaList = document.select("main div.comic-card > a[href]")
            .map(::parseMangaElement)
            .distinctBy { it.url }
        return MangasPage(mangaList, mangaList.isNotEmpty())
    }

    private fun parseMangaElement(mangaElement: Element): SManga = SManga.create().apply {
        title = mangaElement.attr("title").ifEmpty {
            mangaElement.selectFirst("h3, img[alt]")!!.let { titleElement ->
                if (titleElement.tagName() == "img") titleElement.attr("alt") else titleElement.text()
            }
        }
        setUrlWithoutDomain(mangaElement.absUrl("href"))
        thumbnail_url = mangaElement.selectFirst("img")?.extractImageUrl()
    }

    private fun Element.extractImageUrl(): String = absUrl("src").ifEmpty { absUrl("data-src") }

    private fun JsonObject.withMangaId(mangaId: String): JsonObject = JsonObject(this + ("mangaId" to mangaId.toJsonElement()))

    private fun buildPagedUrl(path: String, page: Int): String = if (page > 1) {
        "$baseUrl$path/page/$page"
    } else {
        "$baseUrl$path"
    }

    private val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val chapterPageRegex = Regex("""data-page="(\d+)"""")
    private val dateNumberRegex = Regex("""\d+""")
    private val genreDataRegex = Regex("""window\.advancedSearchGenres\s*=\s*(\[.*?])\s*;""")

    private val ajaxPath = "/wp-admin/admin-ajax.php"
    private val latestPath = "/moi-cap-nhat"
    private val popularPath = "/xem-nhieu-nhat"
    private val chapterOrderNewest = "newest_first"
    private val chaptersPerPage = 16
}
