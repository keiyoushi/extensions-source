package eu.kanade.tachiyomi.extension.vi.truyenqq

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TruyenQQ : ParsedHttpSource() {

    override val name: String = "TruyenQQ"

    override val lang: String = "vi"

    override val baseUrl: String = "https://truyenqqne.com/"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder().add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    // Trang html chứa popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/truyen-yeu-thich/trang-$page.html", headers)

    // Selector trả về array các manga (chọn cả ảnh cx được tí nữa parse)
    override fun popularMangaSelector(): String = "ul.grid > li"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val anchor = element.selectFirst(".book_info .qtip a")!!
        setUrlWithoutDomain(anchor.attr("href"))
        title = anchor.text()
        thumbnail_url = element.select(".book_avatar img").attr("abs:src")
    }

    // Selector của nút trang kế tiếp
    override fun popularMangaNextPageSelector(): String =
        ".page_redirect > a:nth-last-child(2) > p:not(.active)"

    // Trang html chứa Latest (các cập nhật mới nhất)
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/truyen-moi-cap-nhat/trang-$page.html", headers)

    // Selector trả về array các manga update (giống selector ở trên)
    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    // Tìm kiếm
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/tim-kiem/trang-$page.html".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
                .toString()
        } else {
            val builder = "$baseUrl/tim-kiem-nang-cao/trang-$page.html".toHttpUrl().newBuilder()
            (if (filters.isEmpty()) getFilterList() else filters).filterIsInstance<UriFilter>()
                .forEach { it.addToUri(builder) }
            builder.build().toString()
        }
        return GET(url, headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst(".list-info")!!

        title = document.select("h1").text()
        author = info.select(".org").joinToString { it.text() }
        genre = document.select(".list01 li").joinToString { it.text() }
        description = document.select(".story-detail-info").textWithLinebreaks()
        thumbnail_url = document.select("img[itemprop=image]").attr("abs:src")
        status = when (info.select(".status > p:last-child").text()) {
            "Đang Cập Nhật" -> SManga.ONGOING
            "Hoàn Thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun Elements.textWithLinebreaks(): String {
        this.select("p").prepend("\\n")
        this.select("br").prepend("\\n")
        return this.text().replace("\\n", "\n").replace("\n ", "\n")
    }

    // Chapters
    override fun chapterListSelector(): String = "div.works-chapter-list div.works-chapter-item"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a").text().trim()
        date_upload = parseDate(element.select(".time-chap").text())
    }

    private fun parseDate(date: String): Long = runCatching {
        dateFormat.parse(date)?.time
    }.getOrNull() ?: 0L

    override fun pageListRequest(chapter: SChapter): Request = super.pageListRequest(chapter)
        .newBuilder()
        .cacheControl(CacheControl.FORCE_NETWORK)
        .build()

    // Pages
    override fun pageListParse(document: Document): List<Page> =
        document.select(".page-chapter img")
            .mapIndexed { idx, it ->
                Page(idx, imageUrl = it.attr("abs:src"))
            }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used")

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng tên"),
        CountryFilter(),
        StatusFilter(),
        ChapterCountFilter(),
        SortByFilter(),
        GenreList(getGenreList()),
    )

    interface UriFilter {
        fun addToUri(builder: HttpUrl.Builder)
    }

    open class UriPartFilter(
        name: String,
        private val query: String,
        private val vals: Array<Pair<String, String>>,
    ) : UriFilter, Filter.Select<String>(name, vals.map { it.first }.toTypedArray()) {
        override fun addToUri(builder: HttpUrl.Builder) {
            builder.addQueryParameter(query, vals[state].second)
        }
    }

    class CountryFilter : UriPartFilter(
        "Quốc gia",
        "country",
        arrayOf(
            "Tất cả" to "0",
            "Trung Quốc" to "1",
            "Việt Nam" to "2",
            "Hàn Quốc" to "3",
            "Nhật Bản" to "4",
            "Mỹ" to "5",
        ),
    )

    class StatusFilter : UriPartFilter(
        "Tình trạng",
        "status",
        arrayOf(
            "Tất cả" to "-1",
            "Đang tiến hành" to "0",
            "Hoàn thành" to "2",
        ),
    )

    class ChapterCountFilter : UriPartFilter(
        "Số lượng chương",
        "minchapter",
        arrayOf(
            "0" to "0",
            ">= 100" to "100",
            ">= 200" to "200",
            ">= 300" to "300",
            ">= 400" to "400",
            ">= 500" to "500",
        ),
    )

    class SortByFilter : UriFilter, Filter.Sort(
        "Sắp xếp",
        arrayOf("Ngày đăng", "Ngày cập nhật", "Lượt xem"),
        Selection(2, false),
    ) {
        override fun addToUri(builder: HttpUrl.Builder) {
            val index = state?.index ?: 2
            val ascending = if (state?.ascending == true) 1 else 0
            builder.addQueryParameter("sort", (index * 2 + ascending).toString())
        }
    }

    class Genre(name: String, val id: String) : Filter.TriState(name)

    class GenreList(state: List<Genre>) : UriFilter, Filter.Group<Genre>("Thể loại", state) {
        override fun addToUri(builder: HttpUrl.Builder) {
            val genres = mutableListOf<String>()
            val genresEx = mutableListOf<String>()

            state.forEach {
                when (it.state) {
                    TriState.STATE_INCLUDE -> genres.add(it.id)
                    TriState.STATE_EXCLUDE -> genresEx.add(it.id)
                    else -> {}
                }
            }

            builder.addQueryParameter("category", genres.joinToString(","))
            builder.addQueryParameter("notcategory", genresEx.joinToString(","))
        }
    }

    // console.log([...document.querySelectorAll(".genre-item")].map(e => `Genre("${e.innerText}", "${e.querySelector("span").dataset.id}")`).join(",\n"))
    private fun getGenreList() = listOf(
        Genre("Action", "26"),
        Genre("Adventure", "27"),
        Genre("Anime", "62"),
        Genre("Chuyển Sinh", "91"),
        Genre("Cổ Đại", "90"),
        Genre("Comedy", "28"),
        Genre("Comic", "60"),
        Genre("Demons", "99"),
        Genre("Detective", "100"),
        Genre("Doujinshi", "96"),
        Genre("Drama", "29"),
        Genre("Fantasy", "30"),
        Genre("Gender Bender", "45"),
        Genre("Harem", "47"),
        Genre("Historical", "51"),
        Genre("Horror", "44"),
        Genre("Huyền Huyễn", "468"),
        Genre("Isekai", "85"),
        Genre("Josei", "54"),
        Genre("Mafia", "69"),
        Genre("Magic", "58"),
        Genre("Manhua", "35"),
        Genre("Manhwa", "49"),
        Genre("Martial Arts", "41"),
        Genre("Military", "101"),
        Genre("Mystery", "39"),
        Genre("Ngôn Tình", "87"),
        Genre("One shot", "95"),
        Genre("Psychological", "40"),
        Genre("Romance", "36"),
        Genre("School Life", "37"),
        Genre("Sci-fi", "43"),
        Genre("Seinen", "42"),
        Genre("Shoujo", "38"),
        Genre("Shoujo Ai", "98"),
        Genre("Shounen", "31"),
        Genre("Shounen Ai", "86"),
        Genre("Slice of life", "46"),
        Genre("Sports", "57"),
        Genre("Supernatural", "32"),
        Genre("Tragedy", "52"),
        Genre("Trọng Sinh", "82"),
        Genre("Truyện Màu", "92"),
        Genre("Webtoon", "55"),
        Genre("Xuyên Không", "88"),
    )
}
