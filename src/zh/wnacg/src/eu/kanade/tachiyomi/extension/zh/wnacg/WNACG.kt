package eu.kanade.tachiyomi.extension.zh.wnacg

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.getPreferences
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element

@Source
abstract class WNACG :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences { preferenceMigration() }

    override val baseUrl get() = preferences.baseUrl

    private val updateUrlInterceptor = UpdateUrlInterceptor(preferences)

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(updateUrlInterceptor)
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0")
        set("Sec-Fetch-Mode", "no-cors")
        set("Sec-Fetch-Site", "cross-site")
    }

    private val popularPagingState = FilterPagingState()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val blacklist = preferences.titleBlacklist
        if (blacklist.isEmpty()) {
            popularPagingState.reset()
            return mangaListParse(client.get(popularMangaUrl(page))).filterBlockedTitles()
        }
        val maxScanPages = preferences.blacklistMaxScanPages

        return fetchFilteredMangaPage(
            appPage = page,
            key = "$maxScanPages|${blacklist.joinToString("\u0000")}",
            state = popularPagingState,
            urlForPage = ::popularMangaUrl,
            blacklist = blacklist,
            maxScanPages = maxScanPages,
        )
    }

    private val latestPagingState = FilterPagingState()

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val blacklist = preferences.titleBlacklist
        if (blacklist.isEmpty()) {
            latestPagingState.reset()
            return mangaListParse(client.get(latestUpdatesUrl(page))).filterBlockedTitles()
        }
        val maxScanPages = preferences.blacklistMaxScanPages

        return fetchFilteredMangaPage(
            appPage = page,
            key = "$maxScanPages|${blacklist.joinToString("\u0000")}",
            state = latestPagingState,
            urlForPage = ::latestUpdatesUrl,
            blacklist = blacklist,
            maxScanPages = maxScanPages,
        )
    }

    private val searchPagingState = FilterPagingState()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val blacklist = preferences.titleBlacklist
        if (!preferences.filterSearchResults || blacklist.isEmpty()) {
            searchPagingState.reset()
            return mangaListParse(client.get(searchMangaUrl(page, query, filters)))
        }

        val urlForPage = { sourcePage: Int -> searchMangaUrl(sourcePage, query, filters) }
        val maxScanPages = preferences.blacklistMaxScanPages
        return fetchFilteredMangaPage(
            appPage = page,
            key = urlForPage(1) + "|$maxScanPages|" + blacklist.joinToString("\u0000"),
            state = searchPagingState,
            urlForPage = urlForPage,
            blacklist = blacklist,
            maxScanPages = maxScanPages,
        )
    }

    private fun searchMangaUrl(page: Int, query: String, filters: FilterList): String {
        if (query.isBlank()) {
            val tagFilter = filters.firstInstanceOrNull<TagFilter>()
            if (tagFilter != null && tagFilter.state.isNotEmpty()) {
                return "$baseUrl/albums-index-page-$page-tag-${tagFilter.state}.html"
            }
            val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
            if (categoryFilter != null && categoryFilter.toUriPart().isNotEmpty()) {
                return "$baseUrl/${categoryFilter.toUriPart().format(page)}"
            }
            return popularMangaUrl(page)
        }
        return "$baseUrl/search/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("s", "create_time_DESC")
            .addQueryParameter("q", query)
            .addQueryParameter("p", page.toString())
            .build()
            .toString()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !mangaUrlRegex.matches(url.encodedPath)) return null

        return mangaDetailsParse(client.get(url)).apply {
            this.url = url.encodedPath
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) {
            mangaDetailsParse(client.get(getMangaUrl(manga))).apply { url = manga.url }
        } else {
            manga
        }
        val updatedChapters = if (fetchChapters) {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Ch. 1"
                },
            )
        } else {
            chapters
        }
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(
        baseUrl + chapter.url.replace("-index-", "-gallery-"),
    ).use { response ->
        pageImageRegex.findAll(response.body.string()).mapIndexedTo(ArrayList()) { index, match ->
            Page(index, imageUrl = "http:" + match.value)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("注意：分类和标签均不支持搜索"),
        CategoryFilter(),
        Filter.Separator(),
        Filter.Header("注意：仅支持 1 个标签，不支持分类"),
        TagFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        getPreferencesInternal(screen.context, preferences, updateUrlInterceptor.isUpdated)
            .forEach(screen::addPreference)
    }

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

    private suspend fun fetchFilteredMangaPage(
        appPage: Int,
        key: String,
        state: FilterPagingState,
        urlForPage: (Int) -> String,
        blacklist: List<String>,
        maxScanPages: Int,
    ): MangasPage {
        val snapshot = state.snapshot(appPage, key)
        var sourcePage = appPage + snapshot.offset
        repeat(maxScanPages) {
            val page = mangaListParse(client.get(urlForPage(sourcePage))).filterBlockedTitles(blacklist)
            when {
                page.mangas.isNotEmpty() -> {
                    state.updateOffset(key, snapshot.generation, sourcePage - appPage)
                    return page
                }
                !page.hasNextPage -> return MangasPage(emptyList(), false)
            }
            sourcePage++
        }
        throw Exception("连续 $maxScanPages 页均无可显示结果，请调整黑名单或扫描页数后刷新。")
    }

    private fun popularMangaUrl(page: Int) = "$baseUrl/albums-favorite_ranking-page-$page-type-week.html"

    private fun latestUpdatesUrl(page: Int) = "$baseUrl/albums-index-page-$page.html"

    private fun mangaDetailsParse(response: Response): SManga {
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

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst(".title > a")!!
        url = link.attr("href")
        title = link.text()
        thumbnail_url = element.selectFirst("img")!!.absUrl("src").replaceBefore(':', "http")
    }

    companion object {
        private val pageImageRegex = Regex(
            """//[^\s"'\\]+\.(?:jpeg|jpg|png|webp|gif)(?:\?[^\s"'\\]*)?""",
            RegexOption.IGNORE_CASE,
        )
        private val mangaUrlRegex = Regex("""/photos-index-aid-\d+\.html""")
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
