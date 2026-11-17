package eu.kanade.tachiyomi.extension.zh.kanman

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class Kanman : KeiSource() {

    // Last page of the site's listing, last non-empty page of each full list, and where its next page starts
    @Volatile
    private var listingLastPage: Int? = null
    private val fullListEnds = ConcurrentHashMap<String, Int>()
    private val fullListStarts = ConcurrentHashMap<String, Int>()

    override suspend fun getPopularManga(page: Int): MangasPage = getFullList(page, "click")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getFullList(page, "date")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: "click"
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.value.takeIf { query.isBlank() }
        if (query.isBlank() && category == null) return getFullList(page, sort)
        // Keyword and category lists are filtered before paging, so the first empty page ends them
        val comics = mutableListOf<SManga>()
        var apiPage = 1
        do {
            val results = getListPage(sort, category, query, apiPage++)
            comics += results
        } while (results.isNotEmpty())
        return MangasPage(comics, false)
    }

    // Removed series leave gaps, even empty pages, in the full list: it is read four pages at a time
    private suspend fun getFullList(page: Int, sort: String): MangasPage {
        val lastPage = fullListEnds[sort] ?: findFullListEnd(sort).also { fullListEnds[sort] = it }
        var start = if (page == 1) 1 else fullListStarts["$sort:$page"] ?: ((page - 1) * BUNDLE_SIZE + 1)
        val comics = mutableListOf<SManga>()
        // An empty bundle goes on to the next one
        while (comics.isEmpty() && start <= lastPage) {
            val end = minOf(start + BUNDLE_SIZE - 1, lastPage)
            comics += getListPages(sort, start..end).flatten()
            start = end + 1
        }
        fullListStarts["$sort:${page + 1}"] = start
        return MangasPage(comics.distinctBy { it.url }, start <= lastPage)
    }

    // The site's own listing gives the last page, removed series included: the list ends at the last non-empty page before it
    private suspend fun findFullListEnd(sort: String): Int {
        var end = listingLastPage ?: getListingLastPage().also { listingLastPage = it }
        while (end > 1) {
            val start = maxOf(end - BUNDLE_SIZE + 1, 1)
            val last = getListPages(sort, start..end).indexOfLast { it.isNotEmpty() }
            if (last >= 0) return start + last
            end = start - 1
        }
        return 1
    }

    private suspend fun getListingLastPage(): Int = client.get("$baseUrl/sort/all.html", Fetch.DOCUMENT).asJsoup()
        .select("div.J_pages a").mapNotNull { it.text().toIntOrNull() }.maxOrNull() ?: 1

    private suspend fun getListPages(sort: String, pages: IntRange): List<List<SManga>> = coroutineScope {
        pages.map { async { getListPage(sort, null, "", it) } }.awaitAll()
    }

    private suspend fun getListPage(sort: String, category: String?, query: String, page: Int): List<SManga> {
        val url = "$baseUrl/api/getsortlist/".toHttpUrl().newBuilder()
            .addQueryParameter("orderby", sort)
            .addQueryParameter("search_key", query)
            .apply { if (category != null) addQueryParameter("comic_sort", category) }
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", PAGE_SIZE.toString())
            .build()
        return client.get(url, Fetch.XHR).parseAs<ResponseDto<ComicListDto>>().data.data.map { it.toSManga() }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val domain = baseUrl.toHttpUrl().host.removePrefix("www.")
        if (url.host != domain && !url.host.endsWith(".$domain")) return null
        val comicId = url.pathSegments.first().takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val manga = SManga.create().apply { this.url = "/$comicId/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // Unlike the mobile site, the desktop page also lists the paid chapters, with a lock
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val meta = { property: String -> document.selectFirst("meta[property=og:$property]")?.attr("content") }
        val intro = document.selectFirst("div.mhjj")
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("div.description h1.title")!!.text()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.absUrl("content")
            author = meta("novel:author")?.ifBlank { null }
            genre = meta("novel:category")?.split(",")?.filter { it.isNotBlank() }?.joinToString()?.ifEmpty { null }
            status = when (meta("novel:status")) {
                "连载" -> SManga.ONGOING
                "完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = MarkdownDescription.Builder()
                .description(intro?.selectFirst(".desc-box .content"))
                .field("更新", intro?.selectFirst(".update-date")?.text())
                .build()
        }
        val chapterList = document.select("#j_chapter_list > li.item").map { item ->
            val locked = item.hasClass("lock")
            SChapter.create().apply {
                setUrlWithoutDomain(item.selectFirst("a")!!.absUrl("href"))
                name = (if (locked) "🔒 " else "") + item.selectFirst("p.name")!!.text()
                if (locked) memo = buildJsonObject { put(LOCKED, true) }
            }
        }.reversed()
        // The page only dates the latest update
        chapterList.firstOrNull()?.date_upload = DATE_FORMAT.tryParseDate(meta("novel:update_time"), ZONE)
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo[LOCKED]?.booleanOrNull == true) return emptyList()
        val (comicId, file) = chapter.url.trim('/').split('/')
        val url = "$baseUrl/api/getchapterinfov2".toHttpUrl().newBuilder()
            .addQueryParameter("comic_id", comicId)
            .addQueryParameter("chapter_newid", file.removeSuffix(".html"))
            .build()
        // Image links are signed for an hour: always ask for fresh ones
        val images = client.get(url, Fetch.XHR.from(getChapterUrl(chapter)), CacheControl.FORCE_NETWORK)
            .parseAs<ResponseDto<ChapterInfoDto>>().data.currentChapter.chapterImgList
        return images.mapIndexed { i, image -> Page(i, imageUrl = image) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    companion object {
        private const val PAGE_SIZE = 48 // the API's largest page
        private const val BUNDLE_SIZE = 4
        private const val LOCKED = "locked"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val ZONE = ZoneId.of("Asia/Shanghai")
    }
}
