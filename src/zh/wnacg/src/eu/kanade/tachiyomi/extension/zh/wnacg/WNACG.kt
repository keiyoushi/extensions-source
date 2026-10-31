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
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    // Mobile user agents get a mobile layout
    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0")
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

    private fun popularMangaUrl(page: Int, period: String = "week") = "$baseUrl/albums-favorite_ranking-page-$page-type-$period.html"

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

    private fun latestUpdatesUrl(page: Int) = "$baseUrl/albums-index-page-$page.html"

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
                return baseUrl.toHttpUrl().newBuilder()
                    .addPathSegment("albums-index-page-$page-tag-${tagFilter.state}.html")
                    .build()
                    .toString()
            }
            val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
            if (categoryFilter != null && categoryFilter.toUriPart().isNotEmpty()) {
                return "$baseUrl/${categoryFilter.toUriPart().format(page)}"
            }
            return popularMangaUrl(page, filters.firstInstanceOrNull<RankingFilter>()?.toUriPart() ?: "week")
        }
        return "$baseUrl/search/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("s", filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "create_time_DESC")
            .addQueryParameter("q", query)
            .addQueryParameter("p", page.toString())
            .build()
            .toString()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val (aid, sid) = mangaUrlRegex.matchEntire(url.encodedPath)?.destructured ?: return null
        val path = "/photos-index-aid-${sid.ifEmpty { aid }}.html"

        return mangaDetailsParse(client.get(baseUrl + path).asJsoup()).apply {
            this.url = path
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            mangaDetailsParse(document).apply { url = manga.url },
            if (fetchChapters) chaptersParse(document, manga) else chapters,
        )
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h2")!!.text()
        artist = document.selectFirst("div.uwuinfo p")?.text()
        author = artist
        val category = document.selectFirst("div.uwconn label:contains(分類：)")?.text()
            ?.removePrefix("分類：")?.split('／').orEmpty()
        genre = (category + document.select("div.addtags a.tagshow").eachText()).distinct().joinToString().ifEmpty { null }
        thumbnail_url = document.selectFirst("div.uwthumb img")?.attrOrNull("src")?.toImageUrl()
        description = document.selectFirst("div.uwconn p:contains(簡介：)")?.wholeText()
            ?.removePrefix("簡介：")?.trim()?.ifEmpty { null }
        // Collections (合集) carry a chapter count; single galleries don't change
        if (document.selectFirst("div.uwconn label:contains(章節：)") == null) {
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        } else {
            val statusText = document.selectFirst("div.uwconn label:contains(狀態)")?.text().orEmpty()
            status = if ("連載中" in statusText) SManga.ONGOING else SManga.COMPLETED
        }
    }

    private suspend fun chaptersParse(document: Document, manga: SManga): List<SChapter> {
        val lastPage = document.select("div.f_left.paginator a[href]")
            .mapNotNull { chapterListPageRegex.find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull()
            ?: 1
        val aid = mangaAidRegex.find(manga.url)!!.groupValues[1]
        val otherPages = coroutineScope {
            (2..lastPage).map { page ->
                async { client.get("$baseUrl/photos-index-aid-$aid-page-$page.html").asJsoup().select(CHAPTER_SELECTOR) }
            }.awaitAll()
        }
        val chapterElements = (document.select(CHAPTER_SELECTOR) + otherPages.flatten())
            .distinctBy { it.attr("data-chid") }

        if (chapterElements.isEmpty()) {
            return listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Ch. 1"
                    date_upload = chapterDateFormat.tryParseDate(
                        document.selectFirst("li.gallary_item .info_col")?.text()?.let { chapterDateRegex.find(it)?.value },
                        chapterZone,
                    )
                },
            )
        }
        return chapterElements.map { element ->
            SChapter.create().apply {
                url = "/photos-index-aid-${element.attr("data-chid")}.html"
                name = element.text()
                date_upload = chapterDateFormat.tryParseDate(
                    chapterDateRegex.find(element.attr("title"))?.value,
                    chapterZone,
                )
            }
        }.reversed()
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga)).asJsoup()
        .select("a.ar_item")
        .mapNotNull { element ->
            SManga.create().apply {
                url = element.attrOrNull("href") ?: return@mapNotNull null
                title = element.attrOrNull("title") ?: return@mapNotNull null
                thumbnail_url = element.selectFirst("img")?.attrOrNull("src")?.toImageUrl()
            }
        }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val html = client.get(getChapterUrl(chapter).replace("-index-", "-gallery-")).body.string()
        return pageImageRegex.findAll(html).mapIndexedTo(ArrayList()) { index, match ->
            Page(index, imageUrl = match.value.toImageUrl())
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("注意：排序仅用于关键词搜索"),
        SortFilter(),
        Filter.Separator(),
        Filter.Header("注意：分类和标签均不支持搜索"),
        CategoryFilter(),
        Filter.Header("注意：排行仅在未选分类和标签时生效"),
        RankingFilter(),
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

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst(".title > a")!!
        url = link.attr("href")
        title = link.text()
        thumbnail_url = element.selectFirst("img")?.attrOrNull("src")?.toImageUrl()
    }

    // Protocol-relative image urls, some with extra leading slashes
    private fun String.toImageUrl() = "https://" + trimStart('/')

    companion object {
        private const val CHAPTER_SELECTOR = "div.sr_compact a.tagshow[data-chid]"
        private val pageImageRegex = Regex(
            """//[^\s"'\\]+\.(?:jpeg|jpg|png|webp|gif)(?:\?[^\s"'\\]*)?""",
            RegexOption.IGNORE_CASE,
        )

        // Gallery page, reader and chapter links; a chapter's sid is its collection
        private val mangaUrlRegex = Regex("""/photos-(?:index|slide|list|gallery)-aid-(\d+)(?:-sid-(\d+))?(?:-page-\d+)?\.html""")
        private val mangaAidRegex = Regex("""photos-index-aid-(\d+)""")
        private val chapterListPageRegex = Regex("""photos-index-aid-\d+-page-(\d+)\.html""")
        private val chapterDateRegex = Regex("""\d{4}-\d{2}-\d{2}""")
        private val chapterDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val chapterZone = ZoneId.of("Asia/Taipei")
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
