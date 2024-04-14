package eu.kanade.tachiyomi.multisrc.liliana

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.lang.Exception

abstract class Liliana(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
    private val usesPostSearch: Boolean = false,
) : ParsedHttpSource() {
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/ranking/week/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        launchIO { fetchFilters() }

        val entries = document.select(popularMangaSelector())
            .map(::popularMangaFromElement)
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null

        return MangasPage(entries, hasNextPage)
    }

    override fun popularMangaSelector(): String = "div#main div.grid > div"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.imgAttr()
        element.selectFirst(".text-center a")!!.run {
            title = text().trim()
            setUrlWithoutDomain(attr("href"))
        }
    }

    override fun popularMangaNextPageSelector(): String = ".blog-pager > span.pagecurrent + span"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/all-manga/$page/?sort=last_update&status=0", headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        popularMangaParse(response)

    override fun latestUpdatesSelector(): String =
        throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga =
        throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector(): String =
        throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank() && usesPostSearch) {
            val formBody = FormBody.Builder()
                .add("search", query)
                .build()

            val formHeaders = headersBuilder().apply {
                add("Accept", "application/json, text/javascript, */*; q=0.01")
                add("Host", baseUrl.toHttpUrl().host)
                add("Origin", baseUrl)
                add("X-Requested-With", "XMLHttpRequest")
            }.build()

            return POST("$baseUrl/ajax/search", formHeaders, formBody)
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("keyword", query)
            } else {
                addPathSegment("filter")
                filters.filterIsInstance<UrlPartFilter>().forEach {
                    it.addUrlParameter(this)
                }
            }
            addPathSegment(page.toString())
            addPathSegment("")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.method == "GET") {
            return popularMangaParse(response)
        }

        val mangaList = response.parseAs<SearchResponseDto>().list.map { manga ->
            SManga.create().apply {
                setUrlWithoutDomain(manga.url)
                title = manga.name
                thumbnail_url = baseUrl + manga.cover
            }
        }

        return MangasPage(mangaList, false)
    }

    @Serializable
    class SearchResponseDto(
        val list: List<MangaDto>,
    ) {
        @Serializable
        class MangaDto(
            val cover: String,
            val name: String,
            val url: String,
        )
    }

    override fun searchMangaSelector(): String =
        throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element): SManga =
        throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector(): String =
        throw UnsupportedOperationException()

    // =============================== Filters ==============================

    protected var genreName = ""
    protected var genreData = listOf<Pair<String, String>>()
    protected var chapterCountName = ""
    protected var chapterCountData = listOf<Pair<String, String>>()
    protected var statusName = ""
    protected var statusData = listOf<Pair<String, String>>()
    protected var genderName = ""
    protected var genderData = listOf<Pair<String, String>>()
    protected var sortName = ""
    protected var sortData = listOf<Pair<String, String>>()
    private var fetchFilterAttempts = 0

    protected suspend fun fetchFilters() {
        if (
            fetchFilterAttempts < 3 &&
            arrayOf(genreData, chapterCountData, statusData, genderData, sortData).any { it.isEmpty() }
        ) {
            try {
                val doc = client.newCall(filtersRequest())
                    .await()
                    .asJsoup()

                parseFilters(doc)
            } catch (e: Exception) {
                Log.e("$name: Filters", e.stackTraceToString())
            }
            fetchFilterAttempts++
        }
    }

    protected open fun filtersRequest() = GET("$baseUrl/filter", headers)

    protected open fun parseFilters(document: Document) {
        genreName = document.selectFirst("div.advanced-genres > h3")?.text() ?: ""
        genreData = document.select("div.advanced-genres > div > .advance-item").map {
            it.text() to it.selectFirst("span")!!.attr("data-genre")
        }

        chapterCountName = document.getSelectName("select-count")
        chapterCountData = document.getSelectData("select-count")

        statusName = document.getSelectName("select-status")
        statusData = document.getSelectData("select-status")

        genderName = document.getSelectName("select-gender")
        genderData = document.getSelectData("select-gender")

        sortName = document.getSelectName("select-sort")
        sortData = document.getSelectData("select-sort")
    }

    private fun Document.getSelectName(selectorClass: String): String {
        return this.selectFirst(".select-div > label.$selectorClass")?.text() ?: ""
    }

    private fun Document.getSelectData(selectorId: String): List<Pair<String, String>> {
        return this.select("#$selectorId > option").map {
            it.text() to it.attr("value")
        }
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()

        if (genreData.isNotEmpty()) {
            filters.add(GenreFilter(genreName, genreData))
        }
        if (chapterCountData.isNotEmpty()) {
            filters.add(ChapterCountFilter(chapterCountName, chapterCountData))
        }
        if (statusData.isNotEmpty()) {
            filters.add(StatusFilter(statusName, statusData))
        }
        if (genderData.isNotEmpty()) {
            filters.add(GenderFilter(genderName, genderData))
        }
        if (sortData.isNotEmpty()) {
            filters.add(SortFilter(sortName, sortData))
        }
        if (filters.size < 5) {
            filters.add(0, Filter.Header("Press 'reset' to load more filters"))
        } else {
            filters.add(0, Filter.Header("NOTE: Ignored if using text search!"))
            filters.add(1, Filter.Separator())
        }

        return FilterList(filters)
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    protected fun launchIO(block: suspend () -> Unit) = scope.launch { block() }

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        description = document.selectFirst("div#syn-target")?.text()
        thumbnail_url = document.selectFirst(".a1 > figure img")?.imgAttr()
        title = document.selectFirst(".a2 header h1")?.text()?.trim() ?: "N/A"
        genre = document.select(".a2 div > a[rel='tag'].label").joinToString { it.text() }
        author = document.selectFirst("div.y6x11p i.fas.fa-user + span.dt")?.text()?.takeUnless {
            it.equals("updating", true)
        }
        status = document.selectFirst("div.y6x11p i.fas.fa-rss + span.dt").parseStatus()
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "ongoing", "đang tiến hành", "進行中" -> SManga.ONGOING
        "completed", "hoàn thành", "完了" -> SManga.COMPLETED
        "on-hold", "tạm ngưng", "保留" -> SManga.ON_HIATUS
        "canceled", "đã huỷ", "キャンセル" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListSelector() = "ul > li.chapter"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("time[datetime]")?.also {
            date_upload = it.attr("datetime").toLongOrNull()?.let { it * 1000L } ?: 0L
        }
        with(element.selectFirst("a")!!) {
            name = text()
            setUrlWithoutDomain(attr("abs:href"))
        }
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val document = client.newCall(GET(baseUrl + chapter.url, headers)).execute().asJsoup()

        val script = document.selectFirst("script:containsData(const CHAPTER_ID)")?.data()
            ?: throw Exception("Failed to get chapter id")

        val chapterId = script.substringAfter("const CHAPTER_ID = ").substringBefore(";")

        val pageHeaders = headersBuilder().apply {
            add("Accept", "application/json, text/javascript, *//*; q=0.01")
            add("Host", baseUrl.toHttpUrl().host)
            set("Referer", baseUrl + chapter.url)
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        return GET("$baseUrl/ajax/image/list/chap/$chapterId", pageHeaders)
    }

    @Serializable
    class PageListResponseDto(
        val status: Boolean = false,
        val msg: String? = null,
        val html: String,
    )

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PageListResponseDto>()

        if (!data.status) {
            throw Exception(data.msg)
        }

        return pageListParse(
            Jsoup.parseBodyFragment(
                data.html,
                response.request.header("Referer")!!,
            ),
        )
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.separator").mapIndexed { i, page ->
            val url = page.selectFirst("a")!!.attr("abs:href")
            Page(i, document.location(), url)
        }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    // ============================= Utilities ==============================

    // From mangathemesia
    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }
}
