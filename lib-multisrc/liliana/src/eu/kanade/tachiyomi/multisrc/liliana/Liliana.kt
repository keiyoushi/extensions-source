package eu.kanade.tachiyomi.multisrc.liliana

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class Liliana : KeiSource() {

    protected open val usesPostSearch: Boolean = false

    override val supportsLatest = true

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = popularMangaParse(client.get("$baseUrl/ranking/week/$page").asJsoup())

    protected open fun popularMangaParse(document: Document): MangasPage {
        val elements = document.select(popularMangaSelector())
        val mangas = elements.map { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector()?.let { selector ->
            document.selectFirst(selector) != null
        } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun popularMangaSelector(): String = "div#main div.grid > div"

    protected open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.imgAttr()
        with(element.selectFirst(".text-center a")!!) {
            title = text()
            setUrlWithoutDomain(attr("abs:href"))
        }
    }

    protected open fun popularMangaNextPageSelector(): String? = ".blog-pager > span.pagecurrent + span"

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = popularMangaParse(client.get("$baseUrl/all-manga/$page/?sort=last_update&status=0").asJsoup())

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank() && usesPostSearch) {
            return postSearch(query)
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

        return popularMangaParse(client.get(url).asJsoup())
    }

    private suspend fun postSearch(query: String): MangasPage {
        val formBody = FormBody.Builder()
            .add("search", query)
            .build()

        val formHeaders = headersBuilder().apply {
            add("Accept", "application/json, text/javascript, */*; q=0.01")
            add("Host", baseUrl.toHttpUrl().host)
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val mangaList = client.post("$baseUrl/ajax/search", formHeaders, formBody)
            .parseAs<SearchResponseDto>().list.map { manga ->
                SManga.create().apply {
                    setUrlWithoutDomain(manga.url)
                    title = manga.name
                    thumbnail_url = baseUrl + manga.cover
                }
            }

        return MangasPage(mangaList, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga" || url.pathSegments.size < 2) return null

        val mangaUrl = "/manga/${url.pathSegments[1]}"
        return mangaDetailsParse(client.get(baseUrl + mangaUrl).asJsoup()).apply { this.url = mangaUrl }
    }

    // =============================== Filters ==============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = parseFilters(client.get("$baseUrl/filter").asJsoup()).toJsonElement()

    protected open fun parseFilters(document: Document) = FilterData(
        genreName = document.selectFirst("div.advanced-genres > h3")?.text() ?: "",
        genres = document.select("div.advanced-genres > div > .advance-item").map {
            it.text() to it.selectFirst("span")!!.attr("data-genre")
        },
        chapterCountName = document.getSelectName("select-count"),
        chapterCounts = document.getSelectData("select-count"),
        statusName = document.getSelectName("select-status"),
        statuses = document.getSelectData("select-status"),
        genderName = document.getSelectName("select-gender"),
        genders = document.getSelectData("select-gender"),
        sortName = document.getSelectName("select-sort"),
        sorts = document.getSelectData("select-sort"),
    )

    private fun Document.getSelectName(selectorClass: String): String = this.selectFirst(".select-div > label.$selectorClass")?.text() ?: ""

    private fun Document.getSelectData(selectorId: String): List<Pair<String, String>> = this.select("#$selectorId > option").map {
        it.text() to it.attr("value")
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("NOTE: Ignored if using text search!"),
            Filter.Separator(),
        )

        if (filterData.genres.isNotEmpty()) {
            filters.add(GenreFilter(filterData.genreName, filterData.genres))
        }
        if (filterData.chapterCounts.isNotEmpty()) {
            filters.add(ChapterCountFilter(filterData.chapterCountName, filterData.chapterCounts))
        }
        if (filterData.statuses.isNotEmpty()) {
            filters.add(StatusFilter(filterData.statusName, filterData.statuses))
        }
        if (filterData.genders.isNotEmpty()) {
            filters.add(GenderFilter(filterData.genderName, filterData.genders))
        }
        if (filterData.sorts.isNotEmpty()) {
            filters.add(SortFilter(filterData.sortName, filterData.sorts))
        }

        return FilterList(filters)
    }

    // =========================== Manga Details ============================

    // details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            mangaDetailsParse(document),
            document.select(chapterListSelector()).map { chapterFromElement(it) },
        )
    }

    protected open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        description = document.selectFirst("div#syn-target")?.text()
        thumbnail_url = document.selectFirst(".a1 > figure img")?.imgAttr()
        title = document.selectFirst(".a2 header h1")!!.text()
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

    protected open fun chapterListSelector() = "ul > li.chapter"

    protected open fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("time[datetime]")?.also {
            date_upload = it.attr("datetime").toLongOrNull()?.let { time -> time * 1000L } ?: 0L
        }
        with(element.selectFirst("a")!!) {
            name = text()
            setUrlWithoutDomain(attr("abs:href"))
        }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl).asJsoup()
        val script = document.selectFirst("script:containsData(const CHAPTER_ID)")?.data()
            ?: throw Exception("Failed to get chapter id")

        val chapterId = script.substringAfter("const CHAPTER_ID = ").substringBefore(";")

        val pageHeaders = headersBuilder().apply {
            add("Accept", "application/json, text/javascript, */*; q=0.01")
            add("Host", baseUrl.toHttpUrl().host)
            set("Referer", chapterUrl)
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val data = client.get("$baseUrl/ajax/image/list/chap/$chapterId", pageHeaders)
            .parseAs<PageListResponseDto>()

        if (!data.status) {
            throw Exception(data.msg ?: "Unknown error")
        }

        return pageListParse(Jsoup.parseBodyFragment(data.html, chapterUrl))
    }

    protected open fun pageListParse(document: Document): List<Page> = if (document.selectFirst("div.separator[data-index]") == null) {
        document.select("div.separator")
            .mapNotNull { page -> page.selectFirst("a")?.attr("abs:href") }
            .filter(::isPageImageUrl)
            .mapIndexed { i, url -> Page(i, imageUrl = url) }
    } else {
        document.select("div.separator[data-index]")
            .mapNotNull { page ->
                val url = page.selectFirst("a")?.attr("abs:href") ?: return@mapNotNull null
                if (!isPageImageUrl(url)) return@mapNotNull null
                Page(page.attr("data-index").toInt(), imageUrl = url)
            }
            .sortedBy { it.index }
    }

    private fun isPageImageUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        val path = lowerUrl.substringBefore('?').substringBefore('#')
        return !path.endsWith(".svg") && !lowerUrl.contains("loading_comments")
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Accept", "image/avif,image/webp,*/*")
        .header("Host", page.imageUrl!!.toHttpUrl().host)
        .removeHeader("Referer")
        .removeHeader("Origin")
        .build()

    // ============================= Utilities ==============================

    // From mangathemesia
    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }
}
