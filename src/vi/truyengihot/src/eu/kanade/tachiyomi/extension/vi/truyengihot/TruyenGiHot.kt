package eu.kanade.tachiyomi.extension.vi.truyengihot

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.util.Calendar

class TruyenGiHot : ParsedHttpSource() {

    override val name: String = "TruyenGiHot"

    override val baseUrl: String = "https://truyengihotne.com"

    override val lang: String = "vi"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder().add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(
            page,
            "",
            FilterList(
                SortFilter(
                    getSortItems(),
                    Filter.Sort.Selection(2, false),
                ),
            ),
        )

    override fun popularMangaSelector(): String = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(
            page,
            "",
            FilterList(
                SortFilter(
                    getSortItems(),
                    Filter.Sort.Selection(0, false),
                ),
            ),
        )

    override fun latestUpdatesSelector(): String = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = searchMangaNextPageSelector()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                var id = query.removePrefix(PREFIX_ID_SEARCH).trim()
                if (!id.endsWith(".html")) {
                    id += ".html"
                }
                if (!id.startsWith("/")) {
                    id = "/$id"
                }

                fetchMangaDetails(
                    SManga.create().apply {
                        url = id
                    },
                )
                    .map { MangasPage(listOf(it.apply { url = id }), false) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url =
            "$baseUrl/tim-kiem-nang-cao.html?listType=table&page=$page".toHttpUrl().newBuilder()
                .apply {
                    val genres = mutableListOf<String>()
                    val genresEx = mutableListOf<String>()

                    addQueryParameter("text_add", query)

                    (if (filters.isEmpty()) getFilterList() else filters).forEach {
                        when (it) {
                            is UriFilter -> it.addToUri(this)
                            is GenreFilter -> it.state.forEach { genre ->
                                when (genre.state) {
                                    Filter.TriState.STATE_INCLUDE -> genres.add(genre.id)
                                    Filter.TriState.STATE_EXCLUDE -> genresEx.add(genre.id)
                                    else -> {}
                                }
                            }
                            else -> {}
                        }
                    }

                    addQueryParameter("tag_add", genres.joinToString(","))
                    addQueryParameter("tag_remove", genresEx.joinToString(","))
                }.build().toString()
        return GET(url, headers)
    }

    override fun searchMangaSelector(): String = "ul.cw-list li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val anchor = element.select("span.title a")
        setUrlWithoutDomain(anchor.attr("href"))
        title = anchor.text()
        thumbnail_url = baseUrl + element.select("span.thumb").attr("style")
            .substringAfter("url('")
            .substringBefore("')")
    }

    override fun searchMangaNextPageSelector(): String = "li.page-next a:not(.disabled)"

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select(".cover-title").text()
        author = document.select("p.cover-artist:contains(Tác giả) a").joinToString { it.text() }
        genre = document.select("a.manga-tags").joinToString { it.text().removePrefix("#") }
        thumbnail_url = document.select("div.cover-image img").attr("abs:src")

        val tags = document.select("img.top-tags.top-tags-full").map {
            it.attr("src").substringAfterLast("/").substringBefore(".png")
        }
        status = when {
            tags.contains("ongoing") -> SManga.ONGOING
            tags.contains("drop") -> SManga.CANCELLED
            tags.contains("full") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        description = document.select("div.product-synopsis-content").run {
            select("p").first()?.prepend("|truyengihay-split|")
            text().substringAfter("|truyengihay-split|").substringBefore(" Xem thêm")
        }
    }

    override fun chapterListSelector(): String = "ul.episode-list li a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))

        val infoBlock = element.selectFirst("span.info")!!
        name = infoBlock.select("span.no").text()
        date_upload = parseChapterDate(infoBlock.select("span.date").text())
    }

    private fun parseChapterDate(date: String): Long {
        val trimmedDate = date.substringBefore(" trước").split(" ")

        val calendar = Calendar.getInstance().apply {
            val amount = -trimmedDate[0].toInt()
            val field = when (trimmedDate[1]) {
                "giây" -> Calendar.SECOND
                "phút" -> Calendar.MINUTE
                "giờ" -> Calendar.HOUR_OF_DAY
                "ngày" -> Calendar.DAY_OF_MONTH
                "tuần" -> Calendar.WEEK_OF_MONTH
                "tháng" -> Calendar.MONTH
                "năm" -> Calendar.YEAR
                else -> Calendar.SECOND
            }
            add(field, amount)
        }

        return calendar.timeInMillis
    }

    override fun pageListParse(document: Document): List<Page> {
        val tokenScript = document.selectFirst("script:containsData(_token)")?.data()
            ?: throw Exception("Không tìm được token lấy ảnh của chapter")
        val token = tokenScript
            .substringAfter("_token = \"")
            .substringBefore("\";")

        val chapterInfoScript = document.selectFirst("script:containsData(mangaSLUG)")?.data()
            ?: throw Exception("Không tìm thấy thông tin của chapter")
        val chapterInfo = chapterInfoScript.split(";", "\n").associate {
            if (!it.contains("=")) {
                return@associate Pair("", "")
            }
            val kv = it.trim().split("=")
            val key = kv[0].removePrefix("var ").trim()
            val value = kv[1].trim().removeSurrounding("\"")
            Pair(key, value)
        }

        val formBody = FormBody.Builder()
            .add("token", token)
            .add("chapter_id", chapterInfo["cid"]!!)
            .add("m_slug", chapterInfo["mangaSLUG"]!!)
            .add("m_id", chapterInfo["mangaID"]!!)
            .add("chapter", chapterInfo["chapter"]!!)
            .add("g_id", chapterInfo["g_id"]!!)
            .build()
        val request = POST("$baseUrl/frontend_controllers/chapter/content.php", headers, formBody)
        val response = client.newCall(request).execute().body.use {
            it.string()
        }

        val pageHtml = json.parseToJsonElement(response).jsonObject["content"]!!.jsonPrimitive.content
        val pages = Jsoup.parseBodyFragment(pageHtml, baseUrl)

        if (pages.getElementById("getImage_form") != null) {
            throw Exception("Truyện đã bị khoá!")
        }

        return Jsoup.parseBodyFragment(pageHtml, baseUrl).select("img").mapIndexed { idx, it ->
            Page(idx, imageUrl = it.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList(): FilterList = FilterList(
        SearchTypeFilter(),
        CategoryFilter(),
        PublicationTypeFilter(),
        CountryFilter(),
        StatusFilter(),
        ScanlatorFilter(),
        SortFilter(getSortItems()),
        GenreFilter(),
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

    private class SearchTypeFilter : UriPartFilter(
        "Tìm từ khoá theo",
        "text_type",
        arrayOf(
            Pair("Tên truyện", "name"),
            Pair("Tác giả", "authors"),
        ),
    )

    private class CategoryFilter : UriPartFilter(
        "Phân loại",
        "type_add",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Truyện 18+", "truyen-tranh"),
            Pair("Ngôn tình", "ngon-tinh"),
        ),
    )

    private class PublicationTypeFilter : UriPartFilter(
        "Thể loại",
        "genre_add",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Tự sáng tác", "tu-sang-tac"),
            Pair("Khác", "khac"),
        ),
    )

    private class CountryFilter : UriPartFilter(
        "Quốc gia",
        "country_add",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Âu Mỹ", "au-my"),
            Pair("Hàn Quốc", "han-quoc"),
            Pair("Khác", "khac"),
            Pair("Nhật Bản", "nhat-ban"),
            Pair("Trung Quốc", "trung-quoc"),
            Pair("Việt Nam", "viet-nam"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Trạng thái",
        "status_add",
        arrayOf(
            Pair("Tất cả", "0"),
            Pair("Full", "1"),
            Pair("Ongoing", "2"),
            Pair("Drop", "3"),
        ),
    )

    private class SortFilter(
        private val vals: Array<Pair<String, String>>,
        state: Selection = Selection(2, false),
    ) : UriFilter,
        Filter.Sort("Sắp xếp", vals.map { it.first }.toTypedArray(), state) {
        override fun addToUri(builder: HttpUrl.Builder) {
            builder.addQueryParameter("order_add", vals[state?.index ?: 2].second)
            builder.addQueryParameter(
                "order_by_add",
                if (state?.ascending == true) "ASC" else "DESC",
            )
        }
    }

    private fun getSortItems(): Array<Pair<String, String>> = arrayOf(
        Pair("Mới cập nhật", "last_update"),
        Pair("Lượt xem", "views"),
        Pair("Hot", "total_vote"),
        Pair("Vote", "count_vote"),
        Pair("Tên A-Z", "name"),
    )

    private class Genre(name: String, val id: String) : Filter.TriState(name)

    // console.log([...document.querySelectorAll(".wrapper-search-tag .search-content span")].map(e => `Genre("${e.innerText.trim()}", "${e.dataset.val}")`).join(",\n"))
    private class GenreFilter : Filter.Group<Genre>(
        "Chủ đề",
        listOf(
            Genre("16+", "16"),
            Genre("18+", "18"),
            Genre("1Vs1", "1vs1"),
            Genre("3d", "3d"),
            Genre("3some", "3some"),
            Genre("Ác nữ", "ac-nu"),
            Genre("Ác Quỷ", "ac-quy"),
            Genre("Action", "action"),
            Genre("Adult", "adult"),
            Genre("Adventure", "adventure"),
            Genre("ai cập", "ai-cap"),
            Genre("Âm Nhạc", "am-nhac"),
            Genre("Anh chị em", "anh-chi-em"),
            Genre("anh chị em kế", "anh-chi-em-ke"),
            Genre("anh hùng", "anh-hung"),
            Genre("Anime", "anime"),
            Genre("artist cg", "artist-cg"),
            Genre("Âu Cổ", "au-co"),
            Genre("Bách Hợp", "bach-hop"),
            Genre("bad boy", "bad-boy"),
            Genre("bạn thân", "ban-than"),
            Genre("Bạo Lực", "bao-luc"),
            Genre("Bdsm", "bdsm"),
            Genre("BE", "be"),
            Genre("Bí Ẩn", "bi-an"),
            Genre("Bi kịch", "bi-kich"),
            Genre("bị vứt bỏ", "bi-vut-bo"),
            Genre("big breast", "big-breast"),
            Genre("BL/Bách hợp", "bl-bach-hop"),
            Genre("blowjobs", "blowjobs"),
            Genre("bỏ trốn", "bo-tron"),
            Genre("cái chết", "cai-chet"),
            Genre("Cận đại", "can-dai"),
            Genre("Cấu Huyết", "cau-huyet"),
            Genre("Châu Âu", "chau-au"),
            Genre("che", "che"),
            Genre("Chiến Tranh", "chien-tranh"),
            Genre("Chuyển Sinh", "chuyen-sinh"),
            Genre("Chuyển Thế", "chuyen-the"),
            Genre("Cổ Đại", "co-dai"),
            Genre("Cổ Trang", "co-trang"),
            Genre("con gái nô", "con-gai-no"),
            Genre("con ngoài dã thú", "con-ngoai-da-thu"),
            Genre("công sở", "cong-so"),
            Genre("Cung Đấu", "cung-dau"),
            Genre("đẹp trai Nam chính", "dep-trai-nam-chinh"),
            Genre("Dị Giới", "di-gioi"),
            Genre("Dị Năng", "di-nang"),
            Genre("Điền Văn", "dien-van"),
            Genre("dl site", "dl-site"),
            Genre("Đô Thị", "do-thi"),
            Genre("Đoản Văn", "doan-van"),
            Genre("độc ác Nữ chính", "doc-ac-nu-chinh"),
            Genre("Drama", "drama"),
            Genre("Được nhận nuôi", "duoc-nhan-nuoi"),
            Genre("Ecchi", "ecchi"),
            Genre("Fantasy", "fantasy"),
            Genre("Game", "game"),
            Genre("Gây cấn", "gay-can"),
            Genre("Gia Đình", "gia-dinh"),
            Genre("giả gái/trai", "gia-gai-trai"),
            Genre("Giai cấp quý tộc", "giai-cap-quy-toc"),
            Genre("giam cầm", "giam-cam"),
            Genre("giang hồ", "giang-ho"),
            Genre("Hài Hước", "hai-huoc"),
            Genre("hàng khủng", "hang-khung"),
            Genre("hàng xóm", "hang-xom"),
            Genre("Hành Động", "hanh-dong"),
            Genre("Harem", "harem"),
            Genre("HE", "he"),
            Genre("Hệ Thống", "he-thong"),
            Genre("Hentai", "hentai"),
            Genre("Hiện Đại", "hien-dai"),
            Genre("Hiểu lầm", "hieu-lam"),
            Genre("Hoán Đổi", "hoan-doi"),
            Genre("Hoàng gia", "hoang-gia"),
            Genre("Hoạt Hình", "hoat-hinh"),
            Genre("Học Đường", "hoc-duong"),
            Genre("học sinh", "hoc-sinh"),
            Genre("hối hận", "hoi-han"),
            Genre("Hồi hộp", "hoi-hop"),
            Genre("Huyền Ảo", "huyen-ao"),
            Genre("Ít che", "it-che"),
            Genre("kaka*page", "kaka-page"),
            Genre("khổ dâm", "kho-dam"),
            Genre("Khoa Học", "khoa-hoc"),
            Genre("không che", "khong-che"),
            Genre("Không Màu", "khong-mau"),
            Genre("Kiếm Hiệp", "kiem-hiep"),
            Genre("Kinh Dị", "kinh-di"),
            Genre("Lãng mạn", "lang-man"),
            Genre("lezh*n", "lezh-n"),
            Genre("Lịch Sử", "lich-su"),
            Genre("Light Novel", "light-novel"),
            Genre("Live action", "live-action"),
            Genre("loạn luân", "loan-luan"),
            Genre("Loli", "loli"),
            Genre("ma", "ma"),
            Genre("Ma Cà Rồng", "ma-ca-rong"),
            Genre("mang thai", "mang-thai"),
            Genre("Manga", "manga"),
            Genre("Manhua", "manhua"),
            Genre("Manhwa", "manhwa"),
            Genre("Mạt Thế", "mat-the"),
            Genre("mẹ kế", "me-ke"),
            Genre("Mô tả đế chế", "mo-ta-de-che"),
            Genre("mystery", "mystery"),
            Genre("nam duy nhất", "nam-duy-nhat"),
            Genre("nav*r", "nav-r"),
            Genre("nét vẽ Đẹp", "net-ve-dep"),
            Genre("Netflix", "netflix"),
            Genre("Ngây thơ", "ngay-tho"),
            Genre("ngoại tình", "ngoai-tinh"),
            Genre("Ngôn Tình", "ngon-tinh"),
            Genre("Ngược", "nguoc"),
            Genre("người hầu", "nguoi-hau"),
            Genre("nhân thú", "nhan-thu"),
            Genre("Nhân vật chính", "nhan-vat-chinh"),
            Genre("nhân vật game", "nhan-vat-game"),
            Genre("Ninja", "ninja"),
            Genre("nô lệ", "no-le"),
            Genre("ntr", "ntr"),
            Genre("Nữ Cường", "nu-cuong"),
            Genre("nữ duy nhất", "nu-duy-nhat"),
            Genre("Nữ Phụ", "nu-phu"),
            Genre("Oan gia", "oan-gia"),
            Genre("OE", "oe"),
            Genre("old man", "old-man"),
            Genre("oneshot", "oneshot"),
            Genre("otome game", "otome-game"),
            Genre("otp", "otp"),
            Genre("phản diện", "phan-dien"),
            Genre("Phép Thuật", "phep-thuat"),
            Genre("Phiêu Lưu", "phieu-luu"),
            Genre("Phim Bộ", "phim-bo"),
            Genre("Phim Chiếu Rạp", "phim-chieu-rap"),
            Genre("Phim Lẻ", "phim-le"),
            Genre("prologue", "prologue"),
            Genre("psychological", "psychological"),
            Genre("quái vật", "quai-vat"),
            Genre("Quân Sự", "quan-su"),
            Genre("Quý tộc", "quy-toc"),
            Genre("rape", "rape"),
            Genre("Sắc", "sac"),
            Genre("Sạch", "sach"),
            Genre("SE", "se"),
            Genre("seinen", "seinen"),
            Genre("sex toy", "sex-toy"),
            Genre("shoujo", "shoujo"),
            Genre("Shoujo Ai", "shoujo-ai"),
            Genre("Siêu Năng Lực", "sieu-nang-luc"),
            Genre("slice of life", "slice-of-life"),
            Genre("Smut", "smut"),
            Genre("Sở thích tra tấn", "so-thich-tra-tan"),
            Genre("Sủng", "sung"),
            Genre("supernatural", "supernatural"),
            Genre("tái sinh", "tai-sinh"),
            Genre("Tâm Lý", "tam-ly"),
            Genre("thẩm du", "tham-du"),
            Genre("Thám Hiểm", "tham-hiem"),
            Genre("Thần Thoại", "than-thoai"),
            Genre("thánh nữ", "thanh-nu"),
            Genre("thanh xuân vườn trường", "thanh-xuan-vuon-truong"),
            Genre("thầy/cô giáo", "thay-co-giao"),
            Genre("thay Đổi cốt truyện", "thay-doi-cot-truyen"),
            Genre("thay Đổi giới tính", "thay-doi-gioi-tinh"),
            Genre("Thể Thao", "the-thao"),
            Genre("thuần hóa", "thuan-hoa"),
            Genre("Tiên Hiệp", "tien-hiep"),
            Genre("Tiểu Thuyết", "tieu-thuyet"),
            Genre("Tình Cảm", "tinh-cam"),
            Genre("Tình Tay Ba", "tinh-tay-ba"),
            Genre("Tổng Tài", "tong-tai"),
            Genre("trà xanh", "tra-xanh"),
            Genre("Trailer", "trailer"),
            Genre("Trinh Thám", "trinh-tham"),
            Genre("Trọng Sinh", "trong-sinh"),
            Genre("Truyện Màu", "truyen-mau"),
            Genre("tsundere", "tsundere"),
            Genre("Tự Sáng Tác", "tu-sang-tac"),
            Genre("tưởng tượng", "tuong-tuong"),
            Genre("tuyển tập", "tuyen-tap"),
            Genre("vị hôn thê", "vi-hon-the"),
            Genre("Việt Nam", "viet-nam"),
            Genre("Võ Thuật", "vo-thuat"),
            Genre("Vũ Trụ", "vu-tru"),
            Genre("Webtoon", "webtoon"),
            Genre("xúc tua", "xuc-tua"),
            Genre("Xuyên Không", "xuyen-khong"),
            Genre("Xuyên không/Trọng sinh", "xuyen-khong-trong-sinh"),
            Genre("Yandere", "yandere"),
            Genre("Yuri", "yuri"),
        ),
    )

    // console.log([...document.querySelectorAll(".wrapper-search-group .search-content span")].map(e => `Pair("${e.innerText.trim()}", "${e.dataset.val}")`).join(",\n"))
    private class ScanlatorFilter : UriPartFilter(
        "Nhóm dịch",
        "group_add",
        arrayOf(
            Pair("Tất cả", "0"),
            Pair("Aling - Tiểu Thuyết", "383"),
            Pair("Angela Diệp Lạc", "361"),
            Pair("AUTHOR TIỂU MÂY", "362"),
            Pair("Boom novel", "403"),
            Pair("Cà chua Team", "421"),
            Pair("Camellia", "300"),
            Pair("Cậu Muốn Review Gì Nào?", "342"),
            Pair("Chloe's Library", "392"),
            Pair("Delion", "376"),
            Pair("Ecchi Land", "26"),
            Pair("Fluer", "396"),
            Pair("Gangster", "327"),
            Pair("Hien serena", "330"),
            Pair("Hoạ Y", "417"),
            Pair("Khu Vườn Bí Mật Của Rosaria", "401"),
            Pair("Laziel", "377"),
            Pair("Lazy Bee", "420"),
            Pair("Lil Pan", "334"),
            Pair("Lindy", "399"),
            Pair("Lọ Lem Hangul", "6"),
            Pair("Lycoris Radiata - Tiểu Hoa", "407"),
            Pair("MARY CƠM TRÓ", "423"),
            Pair("Mary Hạ Lục", "38"),
            Pair("Mây", "349"),
            Pair("Mảy Dus GL", "425"),
            Pair("Mảy Lành Mạnh", "424"),
            Pair("Mây Mây", "409"),
            Pair("meoluoihamchoi", "385"),
            Pair("Miêu Tặc", "343"),
            Pair("Mộc Trà", "306"),
            Pair("Một Chiếc Mèo Màu Đen", "390"),
            Pair("Nam Tử Sa Page", "20"),
            Pair("Nô Vồ", "393"),
            Pair("NỒI CƠM TRÓ", "382"),
            Pair("NỒI CƠM TRÓ 18+", "426"),
            Pair("Ổ Của Sien", "321"),
            Pair("Reviewer", "369"),
            Pair("Reviews", "419"),
            Pair("RINNIE", "341"),
            Pair("Rose The One", "337"),
            Pair("Roselight Team", "402"),
            Pair("Song Tử", "305"),
            Pair("The Present Translator", "404"),
            Pair("Thiên Mộc Thất Tú", "304"),
            Pair("Thư Viện Latsya", "370"),
            Pair("Tiệm Kẹo Dẻo Ngòn Ngon", "418"),
            Pair("Tiểu Miêu Ngốc", "395"),
            Pair("Tiểu Thuyết Nhà Mây", "347"),
            Pair("Tiểu Vũ", "360"),
            Pair("TIỂU VY", "388"),
            Pair("tieu.yet", "355"),
            Pair("Trà Và Bánh", "40"),
            Pair("Traham", "319"),
            Pair("Truyện dịch Team Behira", "410"),
            Pair("Truyện Tổng Hợp", "23"),
            Pair("Windyzzz", "379"),
            Pair("Xóm Bán Hoa", "364"),
            Pair("Yu", "406"),
            Pair("Đào Lý Tửu", "345"),
            Pair("Đảo San Hô", "397"),
            Pair("Điền Thất", "373"),
        ),
    )
}
