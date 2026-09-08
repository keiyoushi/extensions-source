package eu.kanade.tachiyomi.extension.zh.wnacg

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable

@Source
abstract class WNACG :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val preferences = getPreferences { preferenceMigration() }

    override val baseUrl = when (System.getenv("CI")) {
        "true" -> getCiBaseUrl()
        else -> preferences.baseUrl
    }

    private val updateUrlInterceptor = UpdateUrlInterceptor(preferences)

    override val client = network.client.newBuilder()
        .addInterceptor(updateUrlInterceptor)
        .build()

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0")
        .set("Referer", baseUrl)
        .set("Sec-Fetch-Mode", "no-cors")
        .set("Sec-Fetch-Site", "cross-site")

    // Popular

    private val popularPagingState = FilterPagingState()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/albums-favorite_ranking-page-$page-type-week.html", headers)

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response).filterBlockedTitles()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val blacklist = preferences.titleBlacklist
        if (blacklist.isEmpty()) {
            popularPagingState.reset()
            return super.fetchPopularManga(page)
        }
        val maxScanPages = preferences.blacklistMaxScanPages

        return fetchFilteredMangaPage(
            appPage = page,
            key = "$maxScanPages|${blacklist.joinToString("\u0000")}",
            state = popularPagingState,
            requestForPage = ::popularMangaRequest,
            blacklist = blacklist,
            maxScanPages = maxScanPages,
        )
    }

    // Latest

    private val latestPagingState = FilterPagingState()

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/albums-index-page-$page.html", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response).filterBlockedTitles()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        val blacklist = preferences.titleBlacklist
        if (blacklist.isEmpty()) {
            latestPagingState.reset()
            return super.fetchLatestUpdates(page)
        }
        val maxScanPages = preferences.blacklistMaxScanPages

        return fetchFilteredMangaPage(
            appPage = page,
            key = "$maxScanPages|${blacklist.joinToString("\u0000")}",
            state = latestPagingState,
            requestForPage = ::latestUpdatesRequest,
            blacklist = blacklist,
            maxScanPages = maxScanPages,
        )
    }

    // Search

    private val searchPagingState = FilterPagingState()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) {
            val tagFilter = filters.firstInstanceOrNull<TagFilter>()
            if (tagFilter != null && tagFilter.state.isNotEmpty()) {
                return GET("$baseUrl/albums-index-page-$page-tag-${tagFilter.state}.html", headers)
            }
            val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
            if (categoryFilter != null && categoryFilter.toUriPart().isNotEmpty()) {
                return GET("$baseUrl/" + categoryFilter.toUriPart().format(page), headers)
            }
            return popularMangaRequest(page)
        }
        val url = "$baseUrl/search/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("s", "create_time_DESC")
            .addQueryParameter("q", query)
            .addQueryParameter("p", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangasPage = mangaListParse(response)
        return if (preferences.filterSearchResults) mangasPage.filterBlockedTitles() else mangasPage
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val blacklist = preferences.titleBlacklist
        if (!preferences.filterSearchResults || blacklist.isEmpty()) {
            searchPagingState.reset()
            return super.fetchSearchManga(page, query, filters)
        }

        val requestForPage = { sourcePage: Int -> searchMangaRequest(sourcePage, query, filters) }
        val maxScanPages = preferences.blacklistMaxScanPages
        val key = requestForPage(1).url.toString() + "|$maxScanPages|" + blacklist.joinToString("\u0000")
        return fetchFilteredMangaPage(
            appPage = page,
            key = key,
            state = searchPagingState,
            requestForPage = requestForPage,
            blacklist = blacklist,
            maxScanPages = maxScanPages,
        )
    }

    // Manga details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h2")!!.text()
            artist = document.selectFirst("div.uwuinfo p")?.text()
            author = document.selectFirst("div.uwuinfo p")?.text()
            genre = document.select("a.tagshow").eachText().joinToString(", ").ifEmpty { null }
            thumbnail_url = "http:" + document.selectFirst("div.uwthumb img")!!.attr("src")
            description = document.selectFirst("div.asTBcell p")?.html()?.replace("<br>", "\n")
            status = SManga.COMPLETED
        }
    }

    // Chapter list

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Ch. 1"
        }
        return Observable.just(listOf(chapter))
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // Pages

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url.replace("-index-", "-gallery-"), headers)

    override fun pageListParse(response: Response): List<Page> = pageImageRegex.findAll(response.body.string()).mapIndexedTo(ArrayList()) { index, match ->
        Page(index, imageUrl = "http:" + match.value)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("注意：分类和标签均不支持搜索"),
        CategoryFilter(),
        Filter.Separator(),
        Filter.Header("注意：仅支持 1 个标签，不支持分类"),
        TagFilter(),
    )

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context, preferences, updateUrlInterceptor.isUpdated)
            .forEach(screen::addPreference)
    }

    // Helpers

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".gallary_item").map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("span.thispage + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun MangasPage.filterBlockedTitles(
        blacklist: List<String> = preferences.titleBlacklist,
    ): MangasPage {
        if (blacklist.isEmpty()) return this

        val filteredMangas = mangas.filterNot { manga ->
            blacklist.any { keyword -> manga.title.contains(keyword, ignoreCase = true) }
        }
        return MangasPage(filteredMangas, hasNextPage)
    }

    private fun fetchFilteredMangaPage(
        appPage: Int,
        key: String,
        state: FilterPagingState,
        requestForPage: (Int) -> Request,
        blacklist: List<String>,
        maxScanPages: Int,
    ): Observable<MangasPage> {
        val snapshot = state.snapshot(appPage, key)
        val sourcePage = appPage + snapshot.offset
        return scanFilteredMangaPages(
            appPage = appPage,
            sourcePage = sourcePage,
            state = state,
            key = key,
            generation = snapshot.generation,
            requestForPage = requestForPage,
            blacklist = blacklist,
            maxScanPages = maxScanPages,
        )
    }

    private fun scanFilteredMangaPages(
        appPage: Int,
        sourcePage: Int,
        state: FilterPagingState,
        key: String,
        generation: Int,
        requestForPage: (Int) -> Request,
        blacklist: List<String>,
        maxScanPages: Int,
        pagesScanned: Int = 1,
    ): Observable<MangasPage> = client.newCall(requestForPage(sourcePage))
        .asObservableSuccess()
        .map { response -> mangaListParse(response).filterBlockedTitles(blacklist) }
        .flatMap { page ->
            when {
                page.mangas.isNotEmpty() -> {
                    state.updateOffset(key, generation, sourcePage - appPage)
                    Observable.just(page)
                }
                !page.hasNextPage -> Observable.just(MangasPage(emptyList(), false))
                pagesScanned >= maxScanPages -> Observable.error(
                    Exception("连续 $maxScanPages 页均无可显示结果，请调整黑名单或扫描页数后刷新。"),
                )
                else -> scanFilteredMangaPages(
                    appPage = appPage,
                    sourcePage = sourcePage + 1,
                    state = state,
                    key = key,
                    generation = generation,
                    requestForPage = requestForPage,
                    blacklist = blacklist,
                    maxScanPages = maxScanPages,
                    pagesScanned = pagesScanned + 1,
                )
            }
        }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst(".title > a")!!
        url = link.attr("href")
        title = link.text()
        thumbnail_url = element.selectFirst("img")!!.absUrl("src").replaceBefore(':', "http")
    }

    companion object {
        private val pageImageRegex = Regex("""//\S*(jpeg|jpg|png|webp|gif)""")
    }
}

private class PagingSnapshot(
    val generation: Int,
    val offset: Int,
)

private class FilterPagingState {
    private var key: String? = null
    private var generation = 0
    private var offset = 0

    @Synchronized
    fun snapshot(appPage: Int, key: String): PagingSnapshot {
        if (appPage == 1 || this.key != key) {
            this.key = key
            generation++
            offset = 0
        }
        return PagingSnapshot(generation, offset)
    }

    @Synchronized
    fun reset() {
        key = null
        generation++
        offset = 0
    }

    @Synchronized
    fun updateOffset(key: String, generation: Int, offset: Int) {
        if (this.key != key || this.generation != generation) return
        this.offset = offset
    }
}
