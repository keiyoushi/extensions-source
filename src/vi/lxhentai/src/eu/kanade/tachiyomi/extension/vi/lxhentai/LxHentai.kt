package eu.kanade.tachiyomi.extension.vi.lxhentai

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.map

class LxHentai :
    ParsedHttpSource(),
    ConfigurableSource {

    override val name = "LXManga"

    override val id = 6495630445796108150

    private val defaultBaseUrl = "https://lxmanga.space"

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val lang = "vi"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList(SortBy(3)))

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList(SortBy(0)))

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = when {
        query.startsWith(PREFIX_ID_SEARCH) -> {
            val slug = query.substringAfter(PREFIX_ID_SEARCH)
            val mangaUrl = "/truyen/$slug"
            fetchMangaDetails(SManga.create().apply { url = mangaUrl })
                .map { MangasPage(listOf(it), false) }
        }

        else -> super.fetchSearchManga(page, query, filters)
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
                            Filter.TriState.STATE_INCLUDE -> addQueryParameter("filter[accept_genres]", genre.id)
                            Filter.TriState.STATE_EXCLUDE -> addQueryParameter("filter[reject_genres]", genre.id)
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
        description = document.select("p:contains(Tóm tắt) ~ p").joinToString("\n") { it.wholeText() }.trim()
        thumbnail_url = document.selectFirst(".cover")?.attr("style")?.let {
            IMAGE_REGEX.find(it)?.groups?.get(1)?.value
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
        date_upload = dateFormat.tryParse(element.select("span.timeago").attr("datetime"))

        val match = CHAPTER_NUMBER_REGEX.findAll(name)
        chapter_number = if (match.count() > 1 && name.lowercase().startsWith("vol")) {
            match.elementAt(1)
        } else {
            match.elementAtOrNull(0)
        }?.value?.toFloat() ?: -1f
    }

    override fun pageListParse(document: Document): List<Page> = document
        .select("div.text-center div.lazy")
        .mapIndexed { idx, element -> Page(idx, imageUrl = element.absUrl("data-src")) }

    override fun imageRequest(page: Page): Request {
        val rawUrl = page.imageUrl!!
        val imageUrl = when {
            rawUrl.startsWith("//") -> "https:$rawUrl"
            else -> rawUrl
        }
        return GET(imageUrl, imageHeaders)
    }

    private val imageHeaders by lazy {
        headersBuilder()
            .set("Origin", baseUrl)
            .set("Token", "364b9dccc5ef526587f108c4d4fd63ee35286e19e36ec55b93bd4d79410dbbf6")
            .build()
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }

    private class SortBy(state: Int = 0) :
        UriPartFilter(
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

    private class Status :
        UriPartFilter(
            "Trạng thái",
            arrayOf(
                Pair("Tất cả", "ongoing,completed,paused"),
                Pair("Đang tiến hành", "ongoing"),
                Pair("Đã hoàn thành", "completed"),
                Pair("Tạm ngưng", "paused"),
            ),
        )

    private class Genre(name: String, val id: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

    private class Author : Filter.Text("Tác giả", "")
    private class Doujinshi : Filter.Text("Doujinshi", "")

    override fun getFilterList(): FilterList = FilterList(
        SortBy(),
        GenreList(getGenreList()),
        Filter.Header("Không dùng được với nhau và với tìm tựa đề"),
        Status(),
        Author(),
        Doujinshi(),
    )

    // console.log([...document.querySelectorAll("label.ml-3.inline-flex.items-center.cursor-pointer")].map(e => `Genre("${e.querySelector(".truncate").innerText}", ${e.getAttribute("@click").replace('toggleGenre(\'', '').replace('\')', '')}),`).join("\n"))
    private fun getGenreList(): List<Genre> = listOf(
        Genre("3D", "3d"),
        Genre("Adult", "adult"),
        Genre("Ahegao", "ahegao"),
        Genre("Anal", "anal"),
        Genre("Animal ", "animal-girl"),
        Genre("Art Book", "art-book"),
        Genre("Artist", "artist"),
        Genre("Bbm", "bbm"),
        Genre("BDSM", "bdsm"),
        Genre("Beach", "beach"),
        Genre("Beast", "beast"),
        Genre("Big breasts ", "big-breasts"),
        Genre("Big dick", "big-dick"),
        Genre("Big vagina", "big-vagina"),
        Genre("Blowjob", "blowjob"),
        Genre("Body modifications", "body-modifications"),
        Genre("Breast Sucking", "breast-sucking"),
        Genre("Bukkake", "bukkake"),
        Genre("CG", "cg"),
        Genre("Chikan", "chikan"),
        Genre("Comic 18+", "comic-18+"),
        Genre("Condom", "condom"),
        Genre("Cosplay", "cosplay"),
        Genre("Creampie", "creampie"),
        Genre("Đam mỹ", "dam-my"),
        Genre("Defloration", "defloration"),
        Genre("Dirty old man", "dirty-old-man"),
        Genre("Double", "double-penetration"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Elf", "elf"),
        Genre("Fantasy", "fantasy"),
        Genre("Femdom", "femdom"),
        Genre("Fingering", "fingering"),
        Genre("First time", "first-time"),
        Genre("Footjob", "footjob"),
        Genre("Foursome", "foursome"),
        Genre("Full color", "full-color"),
        Genre("Funny", "funny"),
        Genre("Furry", "furry"),
        Genre("Futanari", "futanari"),
        Genre("Gangbang", "gangbang"),
        Genre("Gender bender", "gender-bender"),
        Genre("Girl love", "girl-love"),
        Genre("glasses", "glasses"),
        Genre("Group", "group"),
        Genre("Handjob", "handjob"),
        Genre("Harem", "harem"),
        Genre("Housewife", "housewife"),
        Genre("Incest", "incest"),
        Genre("Incomplete", "incomplete"),
        Genre("Insect", "insect"),
        Genre("Inseki", "inseki"),
        Genre("Kinh dị", "kinh dị"),
        Genre("Kogal", "kogal"),
        Genre("Lãng mãn", "lang-man"),
        Genre("Lếu lều", "leu-leu"),
        Genre("Lingerie", "lingerie"),
        Genre("Loạn luân chị em", "loan-luan-chi-em"),
        Genre("Loli", "loli"),
        Genre("LXHENTAI", "lxhentai"),
        Genre("Maid", "maid"),
        Genre("Manhwa", "manhwa"),
        Genre("Masturbation", "masturbation"),
        Genre("Mature", "mature"),
        Genre("Milf", "milf"),
        Genre("Mind break", "mind-break"),
        Genre("Mind control", "mind-control"),
        Genre("Monster", "monster"),
        Genre("Monster Girl", "monster-girl"),
        Genre("mother", "mother"),
        Genre("No sex ", "no-sex"),
        Genre("NTR", "ntr"),
        Genre("NUN", "nun"),
        Genre("Nurse", "nurse"),
        Genre("Office", "office-lady"),
        Genre("OneShot", "oneshot"),
        Genre("Orgasm denial", "orgasm-denial"),
        Genre("Pregnant", "pregnant"),
        Genre("Rape", "rape"),
        Genre("SCAT", "scat"),
        Genre("Schoolboy outfit", "schoolboy-outfit"),
        Genre("Schoolgirl outfit", "schoolgirl-outfit"),
        Genre("Series", "series"),
        Genre("Shota", "shota"),
        Genre("Slave", "slave"),
        Genre("Small", "small-breasts"),
        Genre("Socks", "socks"),
        Genre("Sole female", "sole-female"),
        Genre("Sole male", "sole-male"),
        Genre("Sport", "sport"),
        Genre("Squirting", "squirting"),
        Genre("Story arc", "story-arc"),
        Genre("Succubus", "succubus"),
        Genre("Supernatural", "supernatural"),
        Genre("swimsuit", "swimsuit"),
        Genre("Swinging", "swinging"),
        Genre("Teacher", "teacher"),
        Genre("Three some", "three-some"),
        Genre("Toys", "toys"),
        Genre("Trap", "trap"),
        Genre("Truyện ngắn", "truyen-ngan"),
        Genre("Tự sướng", "tu-suong"),
        Genre("Uncensored", "uncensored"),
        Genre("Vanilla", "vanilla"),
        Genre("virginity", "virginity"),
        Genre("Xúc tua", "xuc-tua"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
    )

    private val preferences: SharedPreferences = getPreferences()

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
        val baseUrlPref = EditTextPreference(screen.context).apply {
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
        val IMAGE_REGEX = """url\('([^']+)""".toRegex()

        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
