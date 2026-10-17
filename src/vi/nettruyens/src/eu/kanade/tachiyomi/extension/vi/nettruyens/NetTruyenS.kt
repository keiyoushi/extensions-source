package eu.kanade.tachiyomi.extension.vi.nettruyens

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

@Source
abstract class NetTruyenS : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder()
            .apply { if (page > 1) addPathSegment(page.toString()) }
            .addQueryParameter("sort", "views")
            .build()
        return parseMangaList(url)
    }

    private suspend fun parseMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select("div.items div.item").map(::mangaFromElement)
        return MangasPage(mangas, hasNextPage(document))
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(("$baseUrl/danh-sach-truyen" + if (page > 1) "/$page" else "").toHttpUrl())

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .apply { if (page > 1) addPathSegment(page.toString()) }
                .addQueryParameter("keyword", query)
                .build()
            return parseMangaList(url)
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

        return parseMangaList(url.build())
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
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

    private fun chapterListParse(document: Document): List<SChapter> = document.select("div.list-chapter li.row:not(.heading)").map { element ->
        SChapter.create().apply {
            val link: Element = element.selectFirst("a")!!
            name = link.text()
            setUrlWithoutDomain(link.absUrl("href"))
            date_upload = element.selectFirst("div.col-xs-4")?.text().parseRelativeDate()
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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val chapterUrl = response.request.url.toString()
        val document = response.asJsoup()

        val imageUrls = document.select("div.page-chapter > img").mapNotNull { it.imageUrl() }
            .filterNot { it.startsWith("data:") }
            .distinct()

        if (imageUrls.isNotEmpty()) {
            return imageUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
        }

        val chapterId = CHAPTER_ID_REGEX.find(document.html())?.groupValues?.get(1)
            ?: return emptyList()

        val ajaxHeaders = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .set("Referer", chapterUrl)
            .build()

        val html = client.post(
            "$baseUrl/ajax/image/list/chap/$chapterId?cache=0",
            ajaxHeaders,
            FormBody.Builder().build(),
        ).parseAs<AjaxImageListDto>().html
        val ajaxDoc = Jsoup.parseBodyFragment(html, baseUrl)
        return ajaxDoc.select("div.page-chapter > img").mapNotNull { it.imageUrl() }
            .filterNot { it.startsWith("data:") }
            .distinct()
            .mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    private fun Element.imageUrl(): String? = when {
        hasAttr("data-original") -> absUrl("data-original")
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("src") -> absUrl("src")
        else -> null
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
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
