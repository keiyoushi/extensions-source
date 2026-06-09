package eu.kanade.tachiyomi.extension.vi.nettruyens

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

class NetTruyenS : HttpSource() {

    override val name = "NetTruyenS (unoriginal)"

    override val lang = "vi"

    override val baseUrl = "https://nettruyen10s.com"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder()
            .apply { if (page > 1) addPathSegment(page.toString()) }
            .addQueryParameter("sort", "views")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.items div.item").map(::mangaFromElement)
        return MangasPage(mangas, hasNextPage(document))
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/danh-sach-truyen" + if (page > 1) "/$page" else "", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.items div.item").map(::mangaFromElement)
        return MangasPage(mangas, hasNextPage(document))
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .apply { if (page > 1) addPathSegment(page.toString()) }
                .addQueryParameter("keyword", query)
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder()
            .apply { if (page > 1) addPathSegment(page.toString()) }

        val genreFilter = filters.firstInstanceOrNull<GenreGroupFilter>()
        val included = mutableListOf<String>()
        val excluded = mutableListOf<String>()
        genreFilter?.state?.forEach { genre ->
            when (genre.state) {
                Filter.TriState.STATE_INCLUDE -> included.add(genre.id)
                Filter.TriState.STATE_EXCLUDE -> excluded.add(genre.id)
                else -> {}
            }
        }
        url.addQueryParameter("genres", included.joinToString(","))
        url.addQueryParameter("notGenres", excluded.joinToString(","))

        filters.firstInstanceOrNull<StatusFilter>()?.let {
            url.addQueryParameter("status", it.toValue())
        }
        filters.firstInstanceOrNull<GenderFilter>()?.let {
            url.addQueryParameter("sex", it.toValue())
        }
        filters.firstInstanceOrNull<MinChapterFilter>()?.let {
            url.addQueryParameter("chapter_count", it.toValue())
        }
        filters.firstInstanceOrNull<SortFilter>()?.let {
            url.addQueryParameter("sort", it.toValue())
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.items div.item").map(::mangaFromElement)
        return MangasPage(mangas, hasNextPage(document))
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val link: Element = element.selectFirst("h3 a")!!
        title = link.text()
        setUrlWithoutDomain(link.absUrl("href"))
        thumbnail_url = element.selectFirst("div.image img")?.imageUrl()
    }

    private fun hasNextPage(document: Document): Boolean {
        val pageInfo = document.selectFirst(".pagination li.hidden")?.text() ?: return false
        val match = PAGE_REGEX.find(pageInfo) ?: return false
        return match.groupValues[1].toInt() < match.groupValues[2].toInt()
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        val info = document.selectFirst("article#item-detail")!!
        title = info.selectFirst("h1.title-detail")!!.text()
        author = info.selectFirst("li.author p.col-xs-8")?.text()
        status = info.selectFirst("li.status p.col-xs-8")?.text().toStatus()
        genre = info.select("li.kind p.col-xs-8 a").joinToString { it.text() }
        val otherName = info.selectFirst("h2.other-name")?.text()
        description = buildString {
            info.selectFirst("div.detail-content p")?.text()?.let(::append)
            if (!otherName.isNullOrBlank()) {
                append("\n\nTên khác: ")
                append(otherName)
            }
        }
        thumbnail_url = info.selectFirst("div.col-image img")?.imageUrl()
    }

    private fun String?.toStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        contains("Đang tiến hành") -> SManga.ONGOING
        contains("Hoàn thành") -> SManga.COMPLETED
        contains("Tạm ngưng") -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.list-chapter li.row:not(.heading)").map { element ->
            SChapter.create().apply {
                val link: Element = element.selectFirst("a")!!
                name = link.text()
                setUrlWithoutDomain(link.absUrl("href"))
                date_upload = element.selectFirst("div.col-xs-4")?.text().parseRelativeDate()
            }
        }
    }

    private fun String?.parseRelativeDate(): Long {
        this ?: return 0L
        val number = RELATIVE_DATE_REGEX.find(this)?.groupValues?.get(1)?.toIntOrNull() ?: return 0L
        val calendar = Calendar.getInstance()
        when {
            contains("giây") -> calendar.add(Calendar.SECOND, -number)
            contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }
        return calendar.timeInMillis
    }

    // ============================== Pages =================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .flatMap { response ->
                val document = response.asJsoup()

                val imageUrls = document.select("div.page-chapter > img").mapNotNull { it.imageUrl() }
                    .filterNot { it.startsWith("data:") }
                    .distinct()

                if (imageUrls.isNotEmpty()) {
                    Observable.just(imageUrls.mapIndexed { i, url -> Page(i, imageUrl = url) })
                } else {
                    val chapterId = CHAPTER_ID_REGEX.find(document.html())?.groupValues?.get(1)
                        ?: return@flatMap Observable.just(emptyList<Page>())

                    val ajaxRequest = POST(
                        "$baseUrl/ajax/image/list/chap/$chapterId?cache=0",
                        ajaxHeaders(response.request.url.toString()),
                    )
                    client.newCall(ajaxRequest).asObservableSuccess().map { ajaxResponse ->
                        val html = ajaxResponse.parseAs<AjaxImageListDto>().html
                        val ajaxDoc = Jsoup.parseBodyFragment(html, baseUrl)
                        ajaxDoc.select("div.page-chapter > img").mapNotNull { it.imageUrl() }
                            .filterNot { it.startsWith("data:") }
                            .distinct()
                            .mapIndexed { i, url -> Page(i, imageUrl = url) }
                    }
                }
            }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    private fun ajaxHeaders(referer: String) = headersBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .add("Referer", referer)
        .build()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Element.imageUrl(): String? = when {
        hasAttr("data-original") -> absUrl("data-original")
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("src") -> absUrl("src")
        else -> null
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Bộ lọc không dùng được khi tìm kiếm bằng từ khóa"),
        GenreGroupFilter(getGenreList()),
        StatusFilter(),
        GenderFilter(),
        MinChapterFilter(),
        SortFilter(),
    )

    companion object {
        private val PAGE_REGEX = Regex("""Page\s+(\d+)\s*/\s*(\d+)""")
        private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s""")
        private val CHAPTER_ID_REGEX = Regex("""CHAPTER_ID\s*=\s*(\d+)""")
    }
}

@Serializable
private class AjaxImageListDto(
    val html: String,
)
