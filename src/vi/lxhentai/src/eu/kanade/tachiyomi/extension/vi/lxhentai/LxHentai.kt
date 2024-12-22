package eu.kanade.tachiyomi.extension.vi.lxhentai

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class LxHentai : ParsedHttpSource(), ConfigurableSource {

    override val name = "LXHentai"

    private val defaultBaseUrl = "https://lxmanga.live"

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int) =
        searchMangaRequest(page, "", FilterList(SortBy(3)))

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) =
        searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() =
        searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) =
        searchMangaRequest(page, "", FilterList(SortBy(0)))

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) =
        searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() =
        searchMangaNextPageSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val slug = query.substringAfter(PREFIX_ID_SEARCH)
                val mangaUrl = "/truyen/$slug"
                fetchMangaDetails(SManga.create().apply { url = mangaUrl })
                    .map { MangasPage(listOf(it), false) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            var canAddTextFilter = true

            addPathSegment("tim-kiem")
            addQueryParameter("page", page.toString())

            if (query.isNotEmpty()) {
                addQueryParameter("filter[name]", query)
                canAddTextFilter = false
            }

            (if (filters.isEmpty()) getFilterList() else filters).forEach {
                when (it) {
                    is GenreList -> it.state.forEach { genre ->
                        when (genre.state) {
                            Filter.TriState.STATE_INCLUDE -> addQueryParameter("filter[accept_genres]", genre.id.toString())
                            Filter.TriState.STATE_EXCLUDE -> addQueryParameter("filter[reject_genres]", genre.id.toString())
                        }
                    }
                    is Author -> if (canAddTextFilter && it.state.isNotEmpty()) {
                        addQueryParameter("filter[artist]", it.state)
                        canAddTextFilter = false
                    }
                    is Doujinshi -> if (canAddTextFilter && it.state.isNotEmpty()) {
                        addQueryParameter("filter[doujinshi]", it.state)
                        canAddTextFilter = false
                    }
                    is Status -> addQueryParameter("filter[status]", it.toUriPart())
                    is SortBy -> addQueryParameter("sort", it.toUriPart())
                    else -> return@forEach
                }
            }
        }.build().toString()
        return GET(url, headers)
    }

    override fun searchMangaSelector(): String = "div.grid div.manga-vertical"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.p-2.truncate a").first()!!.attr("href"))
        title = element.select("div.p-2.truncate a").first()!!.text()
        thumbnail_url = element.selectFirst("div.cover")?.absUrl("data-bg")
    }

    override fun searchMangaNextPageSelector() = "li:contains(Cuối)"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select("div.mb-4 span").first()!!.text()
        author = document.selectFirst("div.grow div.mt-2 > span:contains(Tác giả:) + span a")?.text()
        genre = document.selectFirst("div.grow div.mt-2 > span:contains(Thể loại:) + span")!!
            .select("a")
            .joinToString { it.text().trim(',', ' ') }
        description = document.select("p:contains(Tóm tắt) ~ p").joinToString("\n") {
            it.run {
                select(Evaluator.Tag("br")).prepend("\\n")
                this.text().replace("\\n", "\n").replace("\n ", "\n")
            }
        }.trim()

        thumbnail_url = document.selectFirst(".cover")?.attr("style")?.let {
            IMAGE_REGEX.find(it)?.groups?.get("img")?.value
        }

        val statusString = document.select("div.grow div.mt-2:contains(Tình trạng) a").first()!!.text()
        status = when (statusString) {
            "Đã hoàn thành" -> SManga.COMPLETED
            "Đang tiến hành" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }

        setUrlWithoutDomain(document.location())
    }

    override fun chapterListSelector(): String = "ul.overflow-y-auto.overflow-x-hidden > a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.select("span.text-ellipsis").text()
        date_upload = runCatching {
            dateFormat.parse(element.select("span.timeago").attr("datetime"))?.time
        }.getOrNull() ?: 0L

        val match = CHAPTER_NUMBER_REGEX.findAll(name)
        chapter_number = if (match.count() > 1 && name.lowercase().startsWith("vol")) {
            match.elementAt(1)
        } else {
            match.elementAtOrNull(0)
        }?.value?.toFloat() ?: -1f
    }

    override fun pageListParse(document: Document): List<Page> = document
        .select("div.text-center div.lazy")
        .mapIndexed { idx, element -> Page(idx, "", element.attr("abs:data-src")) }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>, state: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }

    private class SortBy(state: Int = 0) : UriPartFilter(
        "Sắp xếp theo",
        arrayOf(
            Pair("Mới cập nhật", "-updated_at"),
            Pair("Mới nhất", "-created_at"),
            Pair("Cũ nhất", "created_at"),
            Pair("Xem nhiều", "-views"),
            Pair("A-Z", "name"),
            Pair("Z-A", "-name"),
        ),
        state,
    )

    private class Status : UriPartFilter(
        "Trạng thái",
        arrayOf(
            Pair("Tất cả", "1,2"),
            Pair("Đang tiến hành", "2"),
            Pair("Đã hoàn thành", "1"),
        ),
    )

    private class Genre(name: String, val id: Int) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

    private class Author : Filter.Text("Tác giả", "")
    private class Doujinshi : Filter.Text("Doujinshi", "")

    override fun getFilterList(): FilterList = FilterList(
        SortBy(3),
        GenreList(getGenreList()),
        Filter.Header("Không dùng được với nhau và với tìm tựa đề"),
        Author(),
        Doujinshi(),
    )

    // console.log([...document.querySelectorAll("label.ml-3.inline-flex.items-center.cursor-pointer")].map(e => `Genre("${e.querySelector(".truncate").innerText}", ${e.getAttribute("@click").replace('toggleGenre(\'', '').replace('\')', '')}),`).join("\n"))
    private fun getGenreList(): List<Genre> = listOf(
        Genre("Mature", 1),
        Genre("Manhwa", 2),
        Genre("Group", 3),
        Genre("Housewife", 4),
        Genre("NTR", 5),
        Genre("Adult", 6),
        Genre("Series", 7),
        Genre("Complete", 8),
        Genre("Ngực Lớn", 9),
        Genre("Lãng Mạn", 10),
        Genre("Truyện Màu", 11),
        Genre("Mind Break", 12),
        Genre("Mắt Kính", 13),
        Genre("Ngực Nhỏ", 14),
        Genre("Fantasy", 15),
        Genre("Ecchi", 16),
        Genre("Bạo Dâm", 17),
        Genre("Harem", 18),
        Genre("Hài Hước", 19),
        Genre("Cosplay", 20),
        Genre("Hầu Gái", 21),
        Genre("Loli", 22),
        Genre("Shota", 23),
        Genre("Gangbang", 24),
        Genre("Doujinshi", 25),
        Genre("Guro", 26),
        Genre("Virgin", 27),
        Genre("OneShot", 28),
        Genre("Chơi Hai Lỗ", 29),
        Genre("Hậu Môn", 30),
        Genre("Nữ Sinh", 31),
        Genre("Mang Thai", 32),
        Genre("Giáo Viên", 33),
        Genre("Loạn Luân", 34),
        Genre("Truyện Không Che", 35),
        Genre("Futanari", 36),
        Genre("Yuri", 37),
        Genre("Nô Lệ", 38),
        Genre("Đồ Bơi", 39),
        Genre("Thể Thao", 40),
        Genre("Truyện Ngắn", 41),
        Genre("Lão Gìa Dâm", 42),
        Genre("Hãm Hiếp", 43),
        Genre("Monster Girl", 44),
        Genre("Y Tá", 45),
        Genre("Supernatural", 46),
        Genre("3D", 47),
        Genre("Truyện Comic", 48),
        Genre("Animal girl", 49),
        Genre("Thú Vật", 50),
        Genre("Kinh Dị", 51),
        Genre("Quái Vật", 52),
        Genre("Xúc Tua", 53),
        Genre("Gender Bender", 54),
        Genre("Yaoi", 55),
        Genre("CG", 56),
        Genre("Trap", 57),
        Genre("Furry", 58),
        Genre("Mind Control", 59),
        Genre("Elf", 60),
        Genre("Côn Trùng", 61),
        Genre("Kogal", 62),
        Genre("Artist", 63),
        Genre("Scat", 64),
        Genre("Milf", 65),
        Genre("LXHENTAI", 66),
    )

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != defaultBaseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }
        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    companion object {
        const val PREFIX_ID_SEARCH = "id:"

        val CHAPTER_NUMBER_REGEX = Regex("""[+\-]?([0-9]*[.])?[0-9]+""", RegexOption.IGNORE_CASE)
        val IMAGE_REGEX = """url\('(?<img>[^']+)""".toRegex()

        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
