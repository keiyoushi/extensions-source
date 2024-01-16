package eu.kanade.tachiyomi.extension.vi.doctruyen5s

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class DocTruyen5s : ParsedHttpSource() {

    override val name = "DocTruyen5s"

    override val lang = "vi"

    override val baseUrl = "https://manga.io.vn"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/filter/$page/?sort=views_day&chapter_count=0&sex=All", headers)

    override fun popularMangaSelector() = "div.Blog section div.grid > div"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst("div.text-center a")!!

        setUrlWithoutDomain(anchor.attr("abs:href"))
        title = anchor.text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:data-src")
    }

    override fun popularMangaNextPageSelector() = "span.pagecurrent:not(:last-child)"

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/filter/$page/?sort=latest-updated&chapter_count=0&sex=All", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search/$page/".toHttpUrl().newBuilder().apply {
                addQueryParameter("keyword", query)
            }.build()
        } else {
            val builder = "$baseUrl/filter/$page/".toHttpUrl().newBuilder()

            (if (filters.isEmpty()) getFilterList() else filters).filterIsInstance<UriFilter>()
                .forEach { it.addToUri(builder) }

            builder.build()
        }

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("article header h1")!!.text()
        author = document.selectFirst("div.y6x11p i.fas.fa-user + span.dt")?.text()
        description = document.selectFirst("div#syn-target")?.text()
        genre = document.select("a.label[rel=tag]").joinToString { it.text() }
        status = when (document.selectFirst("div.y6x11p i.fas.fa-rss + span.dt")?.text()) {
            "Đang tiến hành" -> SManga.ONGOING
            "Hoàn thành" -> SManga.COMPLETED
            "Tạm ngưng" -> SManga.ON_HIATUS
            "Đã huỷ" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("figure img")?.attr("abs:src")
    }

    override fun chapterListSelector() = "li.chapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val anchor = element.selectFirst("a")!!

        setUrlWithoutDomain(anchor.attr("abs:href"))
        name = anchor.text()
        date_upload = element
            .selectFirst("time")
            ?.attr("datetime")
            ?.toLongOrNull()
            ?.times(1000L) ?: 0L
    }

    private val mangaIdRegex = Regex("""const MANGA_ID = (\d+);""")
    private val chapterIdRegex = Regex("""const CHAPTER_ID = (\d+);""")

    @Serializable
    data class PageAjaxResponse(
        val status: Boolean = false,
        val msg: String? = null,
        val html: String,
    )

    override fun pageListRequest(chapter: SChapter): Request {
        val html = client.newCall(GET("$baseUrl${chapter.url}")).execute().body.string()
        val chapterId = chapterIdRegex.find(html)?.groupValues?.get(1)
            ?: throw Exception("Không tìm thấy ID của chương truyện.")
        val mangaId = mangaIdRegex.find(html)?.groupValues?.get(1)

        if (mangaId != null) {
            countViews(mangaId, chapterId)
        }

        return POST("https://manga.io.vn/ajax/image/list/chap/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = json.decodeFromString<PageAjaxResponse>(response.body.string())

        if (!data.status) {
            throw Exception(data.msg)
        }

        return pageListParse(Jsoup.parse(data.html))
    }

    override fun pageListParse(document: Document) =
        document.select("a.readImg img").mapIndexed { i, it ->
            Page(i, imageUrl = it.attr("abs:src"))
        }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private fun countViews(mangaId: String, chapterId: String) {
        val body = FormBody.Builder()
            .add("manga", mangaId)
            .add("chapter", chapterId)
            .build()
        val request = POST(
            "$baseUrl/ajax/manga/view",
            headers,
            body,
        )

        runCatching { client.newCall(request).execute().close() }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng tên"),
        ChapterCountFilter(),
        StatusFilter(),
        GenderFilter(),
        OrderByFilter(),
        GenreList(getGenresList()),
    )

    interface UriFilter {
        fun addToUri(builder: HttpUrl.Builder)
    }

    open class UriPartFilter(
        name: String,
        private val query: String,
        private val vals: Array<Pair<String, String>>,
        state: Int = 0,
    ) : UriFilter, Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
        override fun addToUri(builder: HttpUrl.Builder) {
            builder.addQueryParameter(query, vals[state].second)
        }
    }

    class ChapterCountFilter : UriPartFilter(
        "Số chương",
        "chapter_count",
        arrayOf(
            ">= 0" to "0",
            ">= 10" to "10",
            ">= 30" to "30",
            ">= 50" to "50",
            ">= 100" to "100",
            ">= 200" to "200",
            ">= 300" to "300",
            ">= 400" to "400",
            ">= 500" to "500",
        ),
    )

    class GenderFilter : UriPartFilter(
        "Giới tính",
        "sex",
        arrayOf(
            "Tất cả" to "All",
            "Con trai" to "Boy",
            "Con gái" to "Girl",
        ),
    )

    class StatusFilter : UriPartFilter(
        "Trạng thái",
        "status",
        arrayOf(
            "Tất cả" to "",
            "Hoàn thành" to "completed",
            "Đang tiến hành" to "on-going",
            "Tạm ngưng" to "on-hold",
            "Đã huỷ" to "canceled",
        ),
    )

    class OrderByFilter : UriPartFilter(
        "Sắp xếp",
        "sort",
        arrayOf(
            "Mặc định" to "default",
            "Mới cập nhật" to "latest-updated",
            "Xem nhiều" to "views",
            "Xem nhiều nhất tháng" to "views_month",
            "Xem nhiều nhất tuần" to "views_week",
            "Xem nhiều nhất hôm nay" to "views_day",
            "Đánh giá cao" to "score",
            "Từ A-Z" to "az",
            "Từ Z-A" to "za",
            "Số chương nhiều nhất" to "chapters",
            "Mới nhất" to "new",
            "Cũ nhất" to "old",
        ),
        5,
    )

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

            if (genres.size > 0) {
                builder.addQueryParameter("genres", genres.joinToString(","))
            }

            if (genresEx.size > 0) {
                builder.addQueryParameter("notGenres", genresEx.joinToString(","))
            }
        }
    }

    /*
        Get the list by navigating to https://manga.io.vn/filter/1 and paste in the code below
        ```
        copy([...document.querySelectorAll("div.advanced-genres div.advance-item")].map((e) => {
            const genreId = e.querySelector("span").dataset.genre;
            const genreName = e.querySelector("label").textContent;
            return `Genre("${genreName}", "${genreId}"),`
        }).join("\n"))
        ```
     */
    private fun getGenresList() = listOf(
        Genre("16+", "788"),
        Genre("Action", "129"),
        Genre("Adult", "837"),
        Genre("Adventure", "810"),
        Genre("Bi Kịch", "393"),
        Genre("Cải Biên Tiểu Thuyết", "771"),
        Genre("Chuyển sinh", "287"),
        Genre("Chuyển Thể", "803"),
        Genre("Cổ Đại", "809"),
        Genre("Cổ Trang", "340"),
        Genre("Comedy", "131"),
        Genre("Comic", "828"),
        Genre("Cooking", "834"),
        Genre("Doujinshi", "201"),
        Genre("Drama", "149"),
        Genre("Ecchi", "300"),
        Genre("Fantasy", "132"),
        Genre("Full màu", "189"),
        Genre("Game", "38"),
        Genre("Gender Bender", "133"),
        Genre("gender_bender", "832"),
        Genre("Girls Love", "815"),
        Genre("Hài Hước", "791"),
        Genre("Hào Môn", "779"),
        Genre("Harem", "187"),
        Genre("Hiện đại", "285"),
        Genre("Historical", "836"),
        Genre("Hoạt Hình", "497"),
        Genre("Horror", "191"),
        Genre("Huyền Huyễn", "475"),
        Genre("Isekai", "811"),
        Genre("Josei", "395"),
        Genre("Lịch Sử", "561"),
        Genre("Ma Mị", "764"),
        Genre("Magic", "160"),
        Genre("Main Mạnh", "763"),
        Genre("Manga", "151"),
        Genre("Manh Bảo", "807"),
        Genre("Mạnh Mẽ", "818"),
        Genre("Manhua", "153"),
        Genre("Manhwa", "193"),
        Genre("Martial Arts", "614"),
        Genre("Mystery", "155"),
        Genre("Ngôn Tình", "156"),
        Genre("Ngọt Sủng", "799"),
        Genre("Nữ Cường", "819"),
        Genre("Oneshot", "65"),
        Genre("Phép Thuật", "808"),
        Genre("Phiêu Lưu", "478"),
        Genre("Psychological", "180"),
        Genre("Quái Vật", "758"),
        Genre("Romance", "756"),
        Genre("School Life", "31"),
        Genre("school_life", "833"),
        Genre("Sci-Fi", "812"),
        Genre("Seinen", "172"),
        Genre("Shoujo", "68"),
        Genre("Shoujo Ai", "136"),
        Genre("Shounen", "140"),
        Genre("Shounen Ai", "203"),
        Genre("Showbiz", "436"),
        Genre("siêu nhiên", "765"),
        Genre("Slice Of Life", "8"),
        Genre("Sports", "167"),
        Genre("Sư Tôn", "794"),
        Genre("Sủng", "820"),
        Genre("Sủng Nịch", "806"),
        Genre("Supernatural", "150"),
        Genre("Tận Thế", "759"),
        Genre("Thú Thê", "800"),
        Genre("Tiên Hiệp", "773"),
        Genre("Tình cảm", "814"),
        Genre("Tragedy", "822"),
        Genre("Tranh Sủng", "805"),
        Genre("Trap (Crossdressing)", "147"),
        Genre("Trinh Thám", "336"),
        Genre("Trọng Sinh", "398"),
        Genre("Trùng Sinh", "392"),
        Genre("Truy Thê", "780"),
        Genre("Truyện Màu", "154"),
        Genre("Truyện Nam", "761"),
        Genre("Truyện Nữ", "776"),
        Genre("Tu Tiên", "477"),
        Genre("Viễn Tưởng", "438"),
        Genre("VNComic", "787"),
        Genre("Vườn Trường", "813"),
        Genre("Webtoon", "198"),
        Genre("Xuyên Không", "157"),
        Genre("Yaoi", "593"),
        Genre("Yuri", "137"),
    )
}
