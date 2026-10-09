package eu.kanade.tachiyomi.extension.ko.sbxh

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Newtoki : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getWorkList(page, ListingFilter.listings[0], "views", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = getWorkList(page, ListingFilter.listings[0], "new", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            val listing = filters.firstInstanceOrNull<ListingFilter>()?.selected ?: ListingFilter.listings[0]
            val sort = filters.firstInstanceOrNull<SortFilter>()?.selected ?: "new"
            return getWorkList(page, listing, sort, filters)
        }

        val kind = filters.firstInstanceOrNull<SearchKindFilter>()?.selected
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .apply { kind?.let { addQueryParameter("kind", it) } }
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.select(".search-results-grid a.card[href]").mapNotNull { element ->
            val path = element.absUrl("href").toHttpUrl().pathSegments
            if (path.size != 2 || path[0] !in TYPES) return@mapNotNull null
            SManga.create().apply {
                this.url = "/${path[0]}/${path[1]}"
                title = element.selectFirst(".subject")!!.text()
                thumbnail_url = element.selectFirst("img.search-thumb-img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a[href*=page=${page + 1}]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private suspend fun getWorkList(page: Int, listing: Listing, sort: String, filters: FilterList): MangasPage {
        val url = when (listing.type) {
            "webtoon" -> "$baseUrl/api/works".toHttpUrl().newBuilder().apply {
                addQueryParameter("status", listing.status)
                if (listing.status == "ongoing") {
                    addQueryParameter("cat", filters.firstInstanceOrNull<CategoryFilter>()?.selected ?: "all")
                    filters.firstInstanceOrNull<DayFilter>()?.selected?.let { addQueryParameter("day", it) }
                }
                filters.firstInstanceOrNull<WebtoonGenreFilter>()?.selected?.let { addQueryParameter("tag", it) }
                filters.firstInstanceOrNull<PlatformFilter>()?.selected?.let { addQueryParameter("plat", it) }
            }
            else -> "$baseUrl/api/manhwa-list".toHttpUrl().newBuilder().apply {
                addQueryParameter("status", listing.status)
                filters.firstInstanceOrNull<MangaGenreFilter>()?.selected?.let { addQueryParameter("g", it) }
            }
        }
            .apply { if (sort != "new") addQueryParameter("sort", sort) }
            .addQueryParameter("page", page.toString())
            .addQueryParameter("pageSize", PAGE_SIZE.toString())
            .build()

        val response = client.get(url).parseAs<WorkListDto>()
        return MangasPage(response.works.map { it.toSManga(listing.type) }, response.hasMore)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val path = url.pathSegments
        if (path.size < 2 || path[0] !in TYPES) return null
        val manga = SManga.create().apply { this.url = "/${path[0]}/${path[1]}" }
        return parseMangaDetails(manga, client.get(getMangaUrl(manga)).asJsoup())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            parseMangaDetails(manga, document),
            if (fetchChapters) parseChapterList(manga, document) else chapters,
        )
    }

    private fun parseMangaDetails(manga: SManga, document: Document) = SManga.create().apply {
        url = manga.url
        title = document.selectFirst("h1.hero-v2-title")!!.text()
        thumbnail_url = document.selectFirst(".hero-v2-thumb img")?.absUrl("src")
        author = document.select(".hero-v2-author a").joinToString { it.text() }.ifEmpty { null }
        description = document.selectFirst(".hero-v2-desc")?.wholeText()?.trim()
        genre = document.select(".hero-v2-tags a").joinToString { it.text().removePrefix("#").trim() }.ifEmpty { null }
        status = when (document.selectFirst(".pill-status")?.text()?.substringAfter("●")?.trim()) {
            "연재중" -> SManga.ONGOING
            "완결" -> SManga.COMPLETED
            "휴재" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private suspend fun parseChapterList(manga: SManga, document: Document): List<SChapter> {
        val firstPage = document.select(CHAPTER_SELECTOR).map(::parseChapter)
        val firstPageSize = document.select("li.ep-row-v2").size
        val total = document.selectFirst(".ep-section-count")?.text()?.filter(Char::isDigit)?.toIntOrNull()
        if (firstPageSize == 0 || total == null || total <= firstPageSize) return firstPage

        val pageCount = ceil(total.toDouble() / firstPageSize).toInt()
        val otherPages = coroutineScope {
            (2..pageCount).map { page ->
                async {
                    val url = getMangaUrl(manga).toHttpUrl().newBuilder()
                        .addQueryParameter("epage", page.toString())
                        .build()
                    client.get(url).asJsoup().select(CHAPTER_SELECTOR).map(::parseChapter)
                }
            }.awaitAll()
        }
        return (firstPage + otherPages.flatten()).distinctBy { it.url }
    }

    private fun parseChapter(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a.ep-row-v2-link")!!.absUrl("href"))
        name = element.selectFirst(".ep-row-v2-title strong")!!.text()
        element.attr("data-episode-number").toFloatOrNull()?.let { chapter_number = it }
        date_upload = dateFormat.tryParseDate(element.selectFirst(".ep-row-v2-date")?.text(), seoul)
    }

    // Image URLs are only issued to the site's own viewer script (fingerprint session + ad check),
    // so the chapter is rendered in a WebView and the resulting <img> sources are collected.
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val storeKey = (1..16).map { ('a'..'z').random() }.joinToString("")
        val script = COLLECT_IMAGES_JS.replace(STORE_KEY_PLACEHOLDER, storeKey)
        val pages = runWebView<List<String>>(timeout = 60.seconds) {
            userAgent = headers["User-Agent"]!!
            poll(500.milliseconds) {
                evaluateJs(script) { result ->
                    val state = result.parseAs<String>().parseAs<ViewerState>()
                    if (state.ready) resolve(state.pages)
                }
            }
            loadUrl(getChapterUrl(chapter))
        }
        return pages.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("검색어 입력 시 '검색 대상'만 적용됩니다"),
        SearchKindFilter(),
        Filter.Separator(),
        ListingFilter(),
        SortFilter(),
        CategoryFilter(),
        DayFilter(),
        WebtoonGenreFilter(),
        PlatformFilter(),
        MangaGenreFilter(),
    )

    @Serializable
    private class ViewerState(
        val ready: Boolean,
        val pages: List<String> = emptyList(),
    )

    companion object {
        private const val PAGE_SIZE = 42

        // Rows under repair ("수정중") are rendered without a link and can't be opened
        private const val CHAPTER_SELECTOR = "li.ep-row-v2:has(a.ep-row-v2-link)"
        private val TYPES = setOf("webtoon", "manhwa")
        private val dateFormat = DateTimeFormatter.ofPattern("yy.MM.dd")
        private val seoul = ZoneId.of("Asia/Seoul")

        private const val STORE_KEY_PLACEHOLDER = "__STORE_KEY__"

        // The viewer may virtualize long chapters, so collected sources are kept across polls
        // and the last mounted image is scrolled into view until every page has been seen.
        private const val COLLECT_IMAGES_JS = """
            (function() {
              var area = document.querySelector('.vw-imgs[data-viewer-image-count]');
              if (!area) return JSON.stringify({ready: false});
              var expected = parseInt(area.getAttribute('data-viewer-image-count'), 10) || 0;
              var store = window['__STORE_KEY__'] || (window['__STORE_KEY__'] = {});
              var nodes = area.querySelectorAll('img[alt]');
              var last = null;
              for (var i = 0; i < nodes.length; i++) {
                var m = /^page\s+(\d+)$/i.exec(nodes[i].getAttribute('alt') || '');
                var src = nodes[i].getAttribute('src') || '';
                if (!m || !/^https?:\/\//i.test(src)) continue;
                store[m[1]] = src;
                last = nodes[i];
              }
              var pages = [];
              for (var p = 1; p <= expected && store[p]; p++) pages.push(store[p]);
              var ready = expected > 0 && pages.length === expected;
              if (!ready && last) last.scrollIntoView({block: 'end'});
              return JSON.stringify({ready: ready, pages: ready ? pages : []});
            })()
        """
    }
}
