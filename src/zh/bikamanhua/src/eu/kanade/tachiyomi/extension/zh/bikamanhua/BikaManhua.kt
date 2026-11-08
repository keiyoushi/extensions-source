package eu.kanade.tachiyomi.extension.zh.bikamanhua

import android.os.SystemClock
import eu.kanade.tachiyomi.multisrc.mccms.ChapterDataDto
import eu.kanade.tachiyomi.multisrc.mccms.GenreFilter
import eu.kanade.tachiyomi.multisrc.mccms.Intl
import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.ResultDto
import eu.kanade.tachiyomi.multisrc.mccms.SortFilter
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

// Baozi Manhua's catalogue, with its covers and pages from Baozi's servers, under the name of PicACG's 哔咔漫画
@Source
abstract class BikaManhua : MCCMS() {

    private val searchMutex = Mutex()
    private var lastSearchTime = 0L

    // The site answers 搜索繁忙 (search busy) to searches less than about a second apart, as Mihon's first two pages are
    private suspend fun fetchSearchPage(url: HttpUrl): Document = searchMutex.withLock {
        delay(lastSearchTime + SEARCH_INTERVAL - SystemClock.elapsedRealtime())
        try {
            client.get(url, Fetch.DOCUMENT).asJsoup()
        } finally {
            lastSearchTime = SystemClock.elapsedRealtime()
        }
    }

    // The API answers text searches with 非法请求 (illegal request), so they go through the site's search pages
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return super.getSearchMangaList(page, query, filters)
        // Like the site: its search box opens /search?key=, and its pager /search/<key>/<page>
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("search").apply {
            if (page == 1) addQueryParameter("key", query) else addPathSegment(query).addPathSegment(page.toString())
        }.build()
        val document = fetchSearchPage(url)
        if (document.body().text().contains("搜索繁忙")) throw Exception("搜索繁忙，请稍后再试")
        val mangas = document.select(".cate-rows .item > a.main").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.selectFirst(".title")!!.text()
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
        val nextPage = document.select(".fetch-status a.page").firstOrNull { it.text() == "下一页" }
            ?.attr("href")?.substringAfterLast('/')?.toIntOrNull()
        return MangasPage(mangas, nextPage != null && nextPage > page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.endsWith("bikamanhua.com")) return null
        val segment = url.pathSegments.singleOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        // A chapter page (/<series id>-<chapter id>.html) links back to its series
        val path = if (segment.endsWith(".html")) {
            client.get("$baseUrl/$segment", Fetch.DOCUMENT).asJsoup().selectFirst("a.btn-text[href]")?.attr("href") ?: return null
        } else {
            "/$segment"
        }
        val document = client.get(baseUrl + path, Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst(".book-hero") == null) return null
        return mangaDetailsParse(document).apply { this.url = path }
    }

    // The theme looks series up by title in the API, which the site refuses, so they come from their own page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup()
        // The page doesn't show the status, so the one from the catalogue stays
        val details = if (fetchDetails) mangaDetailsParse(document).apply { status = manga.status } else manga
        return SMangaUpdate(details, if (fetchChapters) chapterList(document) else chapters)
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val hero = document.selectFirst(".book-hero")!!
        title = hero.selectFirst(".title")!!.text()
        thumbnail_url = hero.selectFirst("img.bg")?.absUrl("src")
        genre = hero.select(".tags .item").flatMap { it.text().split(' ') }.filter { it.isNotEmpty() }.joinToString().ifEmpty { null }
        author = document.selectFirst(".book-container__author")?.text()?.removePrefix("作者：")
        description = document.selectFirst(".book-container__detail")?.text()
    }

    private suspend fun chapterList(document: Document): List<SChapter> {
        val links = document.select(".episode-list .desc_item > a")
        if (links.isEmpty()) return emptyList()
        // Chapter pages are /<series id>-<chapter id>.html, and the API lists their dates by chapter id
        val seriesId = links.first()!!.attr("href").removePrefix("/").substringBefore('-')
        val dates = client.get("$baseUrl/api/data/chapter?mid=$seriesId", Fetch.XHR)
            .parseAs<ResultDto<List<ChapterDataDto>>>().data.associate { it.id to it.date }
        return links.map { link ->
            SChapter.create().apply {
                url = link.attr("href")
                name = link.text()
                date_upload = dates[url.substringAfter('-').substringBefore('.')] ?: 0L
            }
        }.asReversed()
    }

    // The category page lists the genres in its own markup
    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/category/", Fetch.DOCUMENT).asJsoup()
        val genres = document.select(".cate-box a[href*=/tags/]").map { Pair(it.text(), it.attr("href").substringAfterLast('/')) }
        return (listOf(Pair(Intl.all, "")) + genres).toJsonElement()
    }

    // No status filter: the API only matches the exact values 连载 and 完结, while most series are 连载中 or 已完结
    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Pair<String, String>>>()
        val filters = buildList {
            add(SortFilter())
            if (!genres.isNullOrEmpty()) {
                add(Filter.Separator())
                add(GenreFilter(genres).filter)
            }
        }
        return FilterList(filters)
    }
}

private const val SEARCH_INTERVAL = 1500L
