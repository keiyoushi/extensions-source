package eu.kanade.tachiyomi.extension.ko.toon11

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// K만화's own template, over the same series, episodes and images as 11toon, with 11toon's URLs kept for both
internal class Kmana(private val source: Toon11) {

    suspend fun getPopularManga(page: Int): MangasPage = getList(page, orderType = POPULAR_ORDER)

    suspend fun getLatestUpdates(page: Int): MangasPage = getList(page)

    suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return getList(page, keyWord = query.trim())
        val popular = filters.firstInstanceOrNull<SortFilter>()?.selected != sortList[1].value
        val completed = filters.firstInstanceOrNull<StatusFilter>()?.selected == statusList[1].value
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.let { GENRES.indexOf(it.values[it.state]) }?.coerceAtLeast(0) ?: 0
        return getList(
            page,
            type = genre,
            orderType = if (popular) POPULAR_ORDER else "",
            listFrom = if (completed) COMPLETED_LIST else LATEST_LIST,
        )
    }

    // The popular, newest and search lists come on one page, the others 102 at a time
    private suspend fun getList(
        page: Int,
        keyWord: String = "",
        type: Int = 0,
        orderType: String = "",
        listFrom: String = LATEST_LIST,
    ): MangasPage {
        // The server reads these in the order the site sends them
        val url = "${source.baseUrl}/api/toon/list".toHttpUrl().newBuilder()
            .addQueryParameter("keyWord", keyWord)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("type", type.toString())
            .addQueryParameter("orderType", orderType)
            .addQueryParameter("toonListFrom", listFrom)
            .build()
        // Now and then it answers an empty list without a total, for a page it has
        val result = fetchList(url).takeUnless { it.isEmpty } ?: fetchList(url)
        val paged = keyWord.isEmpty() && orderType.isEmpty()
        return MangasPage(result.toSMangas(), paged && page * PAGE_SIZE < result.total)
    }

    private suspend fun fetchList(url: HttpUrl): ToonListResponse = source.client.get(url, source.headers, Fetch.XHR).parseAs()

    suspend fun getMangaById(id: String): SManga {
        val document = getSeriesPage(id, 1)
        return parseDetails(id, document).apply {
            url = mangaUrl(title, id)
            initialized = true
        }
    }

    fun getSeriesId(url: HttpUrl): String? = url.pathSegments.takeIf { it.firstOrNull() in LINK_PATHS }
        ?.getOrNull(1)
        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    fun getMangaUrl(manga: SManga): String = seriesUrl(seriesId(manga.url), 1).toString()

    suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchChapters: Boolean): SMangaUpdate {
        val id = seriesId(manga.url)
        val document = getSeriesPage(id, 1)
        return SMangaUpdate(
            manga = { parseDetails(id, document).apply { url = manga.url } },
            chapters = { if (fetchChapters) getChapters(id, document) else chapters },
        )
    }

    private suspend fun getSeriesPage(id: String, page: Int): Document = source.client.get(seriesUrl(id, page), source.headers, Fetch.DOCUMENT).asJsoup()

    private fun seriesUrl(id: String, page: Int): HttpUrl = source.baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("episode")
        .addPathSegment(id)
        .addPathSegment(page.toString())
        .addPathSegment("1")
        .build()

    // 11toon tells finished series apart, K만화 only by its list of them
    private suspend fun parseDetails(id: String, document: Document): SManga {
        val info = document.selectFirst(".contents_view_info")!!
        val title = info.selectFirst(".date h4")!!.text()
        val completed = getList(1, keyWord = title, listFrom = COMPLETED_LIST).mangas.any { seriesId(it.url) == id }
        return SManga.create().apply {
            this.title = title
            thumbnail_url = info.selectFirst(".img [data-src]")?.absUrl("data-src")
            author = info.field("작가")
            description = info.field("소개")
            genre = info.field("장르")?.split(",")?.joinToString { it.trim() }
            status = if (completed) SManga.COMPLETED else SManga.ONGOING
        }
    }

    private fun Element.field(label: String): String? = select(".date_info_item")
        .firstOrNull { it.selectFirst(".d_name")?.text()?.removeSuffix(":")?.trim() == label }
        ?.selectFirst(".d_info")
        ?.text()
        ?.ifEmpty { null }

    // 100 episodes a page; their dates have no year, so they are left out
    private suspend fun getChapters(id: String, firstPage: Document): List<SChapter> {
        val title = firstPage.selectFirst(".contents_view_info .date h4")!!.text()
        val total = TOTAL.find(firstPage.select("script").joinToString("\n") { it.data() })?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val pages = (total + CHAPTER_PAGE_SIZE - 1) / CHAPTER_PAGE_SIZE
        val documents = listOf(firstPage) + (2..pages).map { getSeriesPage(id, it) }
        return documents.flatMap { it.select("a.mEpisodeItem[href^=/detail/]") }.map { link ->
            SChapter.create().apply {
                url = chapterUrl(link.attr("href").substringAfterLast('/'), title, id)
                name = link.selectFirst(".tit h5")!!.text()
            }
        }
    }

    fun getChapterUrl(chapter: SChapter): String = readerUrl(chapter).toString()

    private fun readerUrl(chapter: SChapter): HttpUrl = source.baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("detail")
        .addPathSegment(seriesId(chapter.url))
        .addPathSegment(WR_ID.find(chapter.url)!!.groupValues[1])
        .build()

    // The reader builds each image's address from the page's variables and the hosts of the site's config
    suspend fun getPageList(chapter: SChapter): List<Page> {
        val script = source.client.get(readerUrl(chapter), source.headers, Fetch.DOCUMENT).asJsoup()
            .selectFirst("script:containsData(const urls)")?.data() ?: return emptyList()
        val vars = READER_VAR.findAll(script).associate { it.groupValues[1] to it.groupValues[2] }
        val config = source.client.get("${source.baseUrl}/js/config.js", source.headers, Fetch.SCRIPT).body.string()
        val hosts = CONFIG_HOST.findAll(config).associate { it.groupValues[1] to it.groupValues[2].trimEnd('/') }
        val folder = vars["folder"].orEmpty()
        val folder2 = vars["folder2"].orEmpty().substringBefore('/')
        val parentId = vars["parentId"].orEmpty()
        val id = vars["id"].orEmpty()
        return vars["urls"].orEmpty().split(',').filter { it.isNotBlank() }.mapIndexed { i, file ->
            val imageUrl = when {
                folder2.isNotEmpty() -> "https:${hosts["dnsSrc2"]}/kr/$folder2/$parentId/$id/${file.substringAfterLast('/')}" +
                    "#https:${hosts["dnsSrc3"]}/kr/$folder2/$parentId/$id/${file.substringAfterLast('/')}"
                folder.isNotEmpty() -> "https:${hosts["dnsSrc"]}/image_pst-123/$folder/$parentId/$file"
                else -> "https:${hosts["dnsSrc"]}/toon_pst-123/$file"
            }
            Page(i, imageUrl = imageUrl)
        }
    }

    private fun seriesId(url: String): String = IS.find(url)!!.groupValues[1]

    // The form of 11toon's episode links, which the library keeps
    private fun chapterUrl(wrId: String, title: String, id: String) = "/board.php?bo_table=toons&wr_id=$wrId&stx=$title&is=$id"

    @Serializable
    class ToonListResponse(
        private val result: ToonListDto?,
    ) {
        val isEmpty get() = result?.list.isNullOrEmpty() && result?.total?.intOrNull == null
        val total get() = result?.total?.intOrNull ?: 0

        fun toSMangas() = result?.list.orEmpty().map { it.toSManga() }
    }

    @Serializable
    class ToonListDto(
        val list: List<ToonDto>?,
        val total: JsonPrimitive?,
    )

    @Serializable
    class ToonDto(
        private val id: String,
        private val title: String,
        private val src: String?,
    ) {
        fun toSManga() = SManga.create().apply {
            url = mangaUrl(this@ToonDto.title, id)
            title = this@ToonDto.title
            thumbnail_url = src?.let { "https:$it" }
        }
    }

    // K만화 answers 500 now and then, and a second try goes through
    object RetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            if (!KMANA_HOST.matches(request.url.host) || response.code < 500) return response
            response.close()
            return chain.proceed(request)
        }
    }

    companion object {
        private const val PAGE_SIZE = 102
        private const val CHAPTER_PAGE_SIZE = 100
        private const val POPULAR_ORDER = "hit"
        private const val LATEST_LIST = "latestToonList"
        private const val COMPLETED_LIST = "finishToonList"
        private val LINK_PATHS = setOf("episode", "detail")

        // The site's genre menu, in its order
        private val GENRES = listOf(
            "전체", "SF", "TS", "개그", "드라마", "코미디", "먹방", "백합", "붕탁", "순정", "스릴러", "스포츠", "시대", "액션", "인기",
            "일상+치유", "추리", "판타지", "학원", "호러", "BL", "17", "무협", "러브코미디", "이세계", "전생", "라노벨", "애니화", "TL",
        )

        private val IS = Regex("""[?&]is=(\d+)""")
        private val WR_ID = Regex("""[?&]wr_id=(\d+)""")
        private val TOTAL = Regex("""const total = (\d+);""")
        private val READER_VAR = Regex("""const (\w+) = "([^"]*)";""")
        private val CONFIG_HOST = Regex("""export const (dnsSrc\d?) = "([^"]+)";""")
    }
}
