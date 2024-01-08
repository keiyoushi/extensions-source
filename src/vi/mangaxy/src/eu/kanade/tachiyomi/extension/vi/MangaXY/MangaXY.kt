package eu.kanade.tachiyomi.extension.vi.MangaXY

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MangaXY : ParsedHttpSource() {

    override val name = "MangaXY"

    override val baseUrl = "https://mangaxy.com"

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = Headers.Builder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    override fun popularMangaRequest(page: Int) = GET(
        "$baseUrl/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("act", "search")
            .addQueryParameter("sort", "xem")
            .addQueryParameter("view", "thumb")
            .addQueryParameter("page", page.toString())
            .toString(),
        headers,
    )

    override fun popularMangaSelector() = ".container > .row > div.col-12.col-lg-9 > #tblChap > .thumb"

    override fun popularMangaNextPageSelector(): String = "div#tblChap p.page a:contains(Cuối)"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.name").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text().trim()
        }
        manga.thumbnail_url = element.select(".item img")
            .first()!!
            .attr("style")
            .substringAfter("url('")
            .substringBefore("')")
            .replace("//", "https:")
            .replace("http:", "")
        return manga
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int) = GET(
        "$baseUrl/search.php".toHttpUrl().newBuilder()
            .addQueryParameter("act", "search")
            .addQueryParameter("sort", "chap")
            .addQueryParameter("view", "thumb")
            .addQueryParameter("page", page.toString())
            .toString(),
        headers,
    )

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        state: Int = 0,
    ) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }

    private class SortByFilter : UriPartFilter(
        "Sắp xếp theo",
        arrayOf(
            Pair("Chap mới", "chap"),
            Pair("Truyện mới", "truyen"),
            Pair("Xem nhiều", "xem"),
            Pair("Theo ABC", "ten"),
            Pair("Số Chương", "sochap"),
        ),
        2,
    )

    private class SearchTypeFilter : UriPartFilter(
        "Kiểu tìm",
        arrayOf(
            Pair("AND/và", "and"),
            Pair("OR/hoặc", "or"),
        ),
    )

    private class ForFilter : UriPartFilter(
        "Dành cho",
        arrayOf(
            Pair("Bất kì", ""),
            Pair("Con gái", "gai"),
            Pair("Con trai", "trai"),
            Pair("Con nít", "nit"),
        ),
    )

    private class AgeFilter : UriPartFilter(
        "Bất kỳ",
        arrayOf(
            Pair("Bất kì", ""),
            Pair("= 13", "13"),
            Pair("= 14", "14"),
            Pair("= 15", "15"),
            Pair("= 16", "16"),
            Pair("= 17", "17"),
            Pair("= 18", "18"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Tình trạng",
        arrayOf(
            Pair("Bất kì", ""),
            Pair("Đang dịch", "Ongoing"),
            Pair("Hoàn thành", "Complete"),
            Pair("Tạm ngưng", "Drop"),
        ),
    )

    private class OriginFilter : UriPartFilter(
        "Quốc gia",
        arrayOf(
            Pair("Bất kì", ""),
            Pair("Nhật Bản", "nhat"),
            Pair("Trung Quốc", "trung"),
            Pair("Hàn Quốc", "han"),
            Pair("Việt Nam", "vietnam"),
        ),
    )

    private class ReadingModeFilter : UriPartFilter(
        "Kiểu đọc",
        arrayOf(
            Pair("Bất kì", ""),
            Pair("Chưa xác định", "chưa xác định"),
            Pair("Phải qua trái", "xem từ phải qua trái"),
            Pair("Trái qua phải", "xem từ trái qua phải"),
        ),
    )

    private class YearFilter : Filter.Text("Năm phát hành")
    private class UserFilter : Filter.Text("Đăng bởi thành viên")
    private class AuthorFilter : Filter.Text("Tên tác giả")
    private class SourceFilter : Filter.Text("Nguồn/Nhóm dịch")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET(
        "$baseUrl/search.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("act", "timnangcao")
            addQueryParameter("view", "thumb")
            addQueryParameter("page", page.toString())

            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is SortByFilter -> addQueryParameter("sort", filter.toUriPart())
                    is SearchTypeFilter -> addQueryParameter("andor", filter.toUriPart())
                    is ForFilter -> if (filter.state != 0) {
                        addQueryParameter("danhcho", filter.toUriPart())
                    }
                    is AgeFilter -> if (filter.state != 0) {
                        addQueryParameter("DoTuoi", filter.toUriPart())
                    }
                    is StatusFilter -> if (filter.state != 0) {
                        addQueryParameter("TinhTrang", filter.toUriPart())
                    }
                    is OriginFilter -> if (filter.state != 0) {
                        addQueryParameter("quocgia", filter.toUriPart())
                    }
                    is ReadingModeFilter -> if (filter.state != 0) {
                        addQueryParameter("KieuDoc", filter.toUriPart())
                    }
                    is YearFilter -> if (filter.state.isNotEmpty()) {
                        addQueryParameter("NamPhaHanh", filter.state)
                    }
                    is UserFilter -> if (filter.state.isNotEmpty()) {
                        addQueryParameter("u", filter.state)
                    }
                    is AuthorFilter -> if (filter.state.isNotEmpty()) {
                        addQueryParameter("TacGia", filter.state)
                    }
                    is SourceFilter -> if (filter.state.isNotEmpty()) {
                        addQueryParameter("Nguon", filter.state)
                    }
                    is GenreList -> {
                        addQueryParameter(
                            "baogom",
                            filter.state
                                .filter { it.state == Filter.TriState.STATE_INCLUDE }
                                .joinToString(",") { it.id },
                        )
                        addQueryParameter(
                            "khonggom",
                            filter.state
                                .filter { it.state == Filter.TriState.STATE_EXCLUDE }
                                .joinToString(",") { it.id },
                        )
                    }
                    else -> {}
                }
            }
        }.build().toString(),
        headers,
    )

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.selectFirst(".tab-content")!!
        val infoTop = document.selectFirst(".detail-top-wrap")!!
        val statusString0 = infoElement.select("div.manga-info > ul > li:nth-child(3) > a").firstOrNull()?.text()
        val statusString1 = infoElement.select("div.manga-info > ul > li:nth-child(4) > a").firstOrNull()?.text()
        val statusString2 = infoElement.select("div.manga-info > ul > li:nth-child(5) > a").firstOrNull()?.text()
        title = infoTop.select("h1.comics-title").text()
        author = infoTop.select(".created-by").joinToString { it.text() }
        genre = infoTop.select(".top-comics-type a").toList()
            .filter { it.text().isNotEmpty() }
            .joinToString(", ") { it.text() }
        description = infoElement.select(".manga-info p").textWithLinebreaks()
        thumbnail_url = infoTop.select(".detail-top-right img")
            .first()!!
            .attr("style")
            .substringAfter("url('")
            .substringBefore("')")
            .replace("//", "https:")
            .replace("http:", "")
        if (statusString0 == "Tạm ngưng" || statusString0 == "Đã Hoàn Thành" || statusString0 == "Đang tiến hành") {
            status = when (statusString0) {
                "Đang tiến hành" -> SManga.ONGOING
                "Đã Hoàn Thành" -> SManga.COMPLETED
                "Tạm ngưng" -> SManga.ON_HIATUS
                null -> SManga.UNKNOWN
                else -> SManga.UNKNOWN
            }
        }
        if (statusString1 == "Tạm ngưng" || statusString1 == "Đã Hoàn Thành" || statusString1 == "Đang tiến hành") {
            status = when (statusString1) {
                "Đang tiến hành" -> SManga.ONGOING
                "Đã Hoàn Thành" -> SManga.COMPLETED
                "Tạm ngưng" -> SManga.ON_HIATUS
                null -> SManga.UNKNOWN
                else -> SManga.UNKNOWN
            }
        }
        if (statusString2 == "Tạm ngưng" || statusString2 == "Đã Hoàn Thành" || statusString2 == "Đang tiến hành") {
            status = when (statusString2) {
                "Đang tiến hành" -> SManga.ONGOING
                "Đã Hoàn Thành" -> SManga.COMPLETED
                "Tạm ngưng" -> SManga.ON_HIATUS
                null -> SManga.UNKNOWN
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun Elements.textWithLinebreaks(): String {
        this.select("p").prepend("\\n")
        this.select("br").prepend("\\n")
        return this.text().replace("\\n", "\n").replace("\n ", "\n")
    }

    override fun chapterListSelector() = "#ChapList > .episode-item"
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select(".episode-item").first()!!.attr("abs:href"))
        name = element.select(".episode-title").first()!!.text()
        date_upload = runCatching {
            dateFormat.parse(element.select("div.episode-date > time").attr("datetime"))?.time
        }.getOrNull() ?: 0L
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.img-fluid").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    open class Genre(name: String, val id: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

    override fun getFilterList() = FilterList(
        GenreList(getGenreList()),
        SortByFilter(),
        SearchTypeFilter(),
        ForFilter(),
        AgeFilter(),
        StatusFilter(),
        OriginFilter(),
        ReadingModeFilter(),
        YearFilter(),
        UserFilter(),
        AuthorFilter(),
        SourceFilter(),
    )

    private fun getGenreList() = listOf(
        Genre("Phát Hành Tại TT8", "106"),
        Genre("Webtoons", "112"),
        Genre("Manga", "141"),
        Genre("Truyện Màu", "113"),
        Genre("Action", "52"),
        Genre("Adult", "53"),
        Genre("Adventure", "65"),
        Genre("Anime", "107"),
        Genre("Biseinen", "123"),
        Genre("Bishounen", "122"),
        Genre("Comedy", "50"),
        Genre("Demons", ""),
        Genre("Doujinshi", "72"),
        Genre("Drama", "73"),
        Genre("Ecchi", "74"),
        Genre("Fantasy", "75"),
        Genre("Gender Bender", "76"),
        Genre("Harem", "77"),
        Genre("Hentai", ""),
        Genre("Historical", "78"),
        Genre("Horror", "79"),
        Genre("Isekai", "139"),
        Genre("Josei", "80"),
        Genre("Live action", "81"),
        Genre("Magic", "116"),
        Genre("Martial Arts", "84"),
        Genre("Mature", "85"),
        Genre("Manhua", "82"),
        Genre("Manhwa", "83"),
        Genre("Mecha", "86"),
        Genre("Mystery", "87"),
        Genre("One-shot", "88"),
        Genre("Oneshot", ""),
        Genre("Other", ""),
        Genre("Psychological", "89"),
        Genre("Romance", "90"),
        Genre("School Life", "91"),
        Genre("Sci fi", "92"),
        Genre("Seinen", "93"),
        Genre("Shotacon", ""),
        Genre("Shoujo", "94"),
        Genre("Shoujo Ai", "66"),
        Genre("Shounen", "96"),
        Genre("Shounen Ai", "97"),
        Genre("Slash", "121"),
        Genre("Slice of Life", "98"),
        Genre("Smut", "99"),
        Genre("Sports", "101"),
        Genre("Super power", ""),
        Genre("Supernatural", "102"),
        Genre("Tragedy", "104"),
        Genre("Yaoi", "114"),
        Genre("Yuri", "111"),
    )
}
