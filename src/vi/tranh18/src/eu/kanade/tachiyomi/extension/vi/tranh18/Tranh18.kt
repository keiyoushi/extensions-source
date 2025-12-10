package eu.kanade.tachiyomi.extension.vi.tranh18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.collections.sortedByDescending

class Tranh18 : ParsedHttpSource() {
    override val lang: String = "vi"

    override val name: String = "Tranh18"

    override val baseUrl: String = "https://tranh18.com"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/update" + if (page > 1) "?page=$page" else "", headers)
    }

    override fun latestUpdatesSelector(): String = ".box-body ul li, .manga-list ul li"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val sel = element.selectFirst(".mh-item, .manga-list-2-cover")
        setUrlWithoutDomain(sel!!.selectFirst("a")!!.absUrl("href"))
        title = sel.select("a").attr("title")
        thumbnail_url = baseUrl + sel.selectFirst("p.mh-cover")?.attr("style")!!
            .substringAfter("url(")
            .substringBefore(")")
            .ifEmpty { baseUrl + sel.selectFirst("img")?.absUrl("data-original") }
    }

    override fun latestUpdatesNextPageSelector(): String = ".mt20"

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun popularMangaSelector(): String = latestUpdatesSelector()

    override fun popularMangaNextPageSelector(): String = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select(".info h1, .detail-main-info-title").text()
        genre = document.select("p.tip:contains(Từ khóa) span a, .detail-main-info-class span a")
            .joinToString { it.text() }
        description = document.select("p.content").takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { it.wholeText().trim().substringBefore("#").trim() }
            ?: document.select("p.detail-desc")
                .joinToString("\n") { it.wholeText().trim().substringBefore("#").trim() }
        author = document.selectFirst(".subtitle:contains(Tác giả：), .detail-main-info-author:contains(Tác giả：) a")
            ?.text()?.removePrefix("Tác giả：")
        status = parseStatus(
            document.select(".block:contains(Trạng thái)").takeIf { it.isNotEmpty() }
                ?.text()
                ?: document.select(".detail-list-title-1").text(),
        )
        thumbnail_url = document.selectFirst(".banner_detail_form .cover img")?.absUrl("src")
            ?.ifEmpty {
                document.selectFirst(".detail-main-cover img")?.absUrl("data-original")
            }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        listOf("Đang Tiến Hành", "Đang Cập Nhật").any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf("Hoàn Thành", "Đã Hoàn Thành", "Đã hoàn tất").any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf("Tạm Ngưng", "Tạm Hoãn").any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector(): String = "ul.detail-list-select li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        name = element.select("a").text()
        date_upload = System.currentTimeMillis()
        val number = Regex("""(\d+(?:\.\d+)*)""").find(name)?.value?.toFloatOrNull() ?: 0f
        chapter_number = number
    }

    override fun chapterListParse(response: Response): List<SChapter> =
        chapterListSelector()
            .let(response.asJsoup()::select)
            .map { element -> chapterFromElement(element) }
            .sortedByDescending { it.chapter_number }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.lazy").mapIndexed { index, it ->
            val url = it.absUrl("data-original")
            val finalUrl = if (url.startsWith("https://external-content.duckduckgo.com/iu/")) {
                url.toHttpUrl().queryParameter("u")
            } else {
                url
            }
            Page(index, imageUrl = finalUrl)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("keyword", query)
            } else {
                addPathSegment("comics")
                (if (filters.isEmpty()) getFilterList() else filters).forEach {
                    when (it) {
                        is KeywordList -> addQueryParameter("tag", it.values[it.state].genre)
                        is StatusList -> addQueryParameter("end", it.values[it.state].genre)
                        is GenreList -> addQueryParameter("area", it.values[it.state].genre)
                        else -> {}
                    }
                }
            }
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun searchMangaSelector(): String = latestUpdatesSelector()

    override fun searchMangaNextPageSelector(): String = latestUpdatesNextPageSelector()

    override fun getFilterList() = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng từ khóa."),
        GenreList(),
        StatusList(),
        KeywordList(getGenreList()),
    )

    private class GenreList : Filter.Select<Genre>(
        "Thể loại",
        arrayOf(
            Genre("Tất cả", "-1"),
            Genre("Manhua", "1"),
            Genre("Manhwa", "2"),
            Genre("Manga", "3"),
        ),
    )

    private class StatusList : Filter.Select<Genre>(
        "Tiến độ",
        arrayOf(
            Genre("Tất cả", "-1"),
            Genre("Đang tiến thành", "2"),
            Genre("Đã hoàn tất", "1"),
        ),
    )

    private class KeywordList(genre: Array<Genre>) : Filter.Select<Genre>("Từ khóa", genre)

    private class Genre(val name: String, val genre: String) {
        override fun toString() = name
    }

    private fun getGenreList() = arrayOf(
        Genre("All", "All"),
        Genre("Adult", "Adult"),
        Genre("Action", "Action"),
        Genre("Comedy", "Comedy"),
        Genre("Drama", "Drama"),
        Genre("Fantasy", "Fantasy"),
        Genre("Harem", "Harem"),
        Genre("Historical", "Historical"),
        Genre("Horror", "Horror"),
        Genre("Ecchi", "Ecchi"),
        Genre("School Life", "School Life"),
        Genre("Seinen", "Seinen"),
        Genre("Shoujo", "Shoujo"),
        Genre("Shoujo Ai", "Shoujo Ai"),
        Genre("Shounen", "Shounen"),
        Genre("Shounen Ai", "Shounen Ai"),
        Genre("Mystery", "Mystery"),
        Genre("Sci-fi", "Sci-fi"),
        Genre("Webtoon", "Webtoon"),
        Genre("Chuyển Sinh", "Chuyển Sinh"),
        Genre("Xuyên Không", "Xuyên Không"),
        Genre("Truyện Màu", "Truyện Màu"),
        Genre("18", "18"),
        Genre("Truyện Tranh 18", "Truyện Tranh 18"),
        Genre("Big Boobs", "Big Boobs"),
    )
}
