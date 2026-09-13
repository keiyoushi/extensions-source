package eu.kanade.tachiyomi.extension.vi.toptruyen

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TopTruyen :
    WPComics(),
    ConfigurableSource {

    override val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT)

    override val dateZone: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    override val gmtOffset = null

    override suspend fun parsePageList(response: Response): List<Page> = response.asJsoup().select("div[id^=page_].page-chapter img").mapIndexed { index, element ->
        val img = element.attr("abs:src")
        Page(index, imageUrl = img)
    }.distinctBy { it.imageUrl }

    override fun popularMangaSelector() = "div.item-manga div.item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("h3 a").let {
            title = it.text()
            setUrlWithoutDomain(it.attr("abs:href"))
        }
        thumbnail_url = imageOrNull(element.selectFirst("img")!!)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder().apply {
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.toUriPart()?.let { addPathSegment(it) }
                    is StatusFilter -> filter.toUriPart()?.let { addQueryParameter("status", it) }
                    else -> {}
                }
            }

            if (query.isNotBlank()) {
                addQueryParameter(queryParam, query)
            } else {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return parseMangaPage(client.get(url), searchMangaSelector(), ::searchMangaFromElement)
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.title-manga")!!.text()
        author = document.select("li.author p.col-sm-8").text()
        description = document.select("p.detail-summary").joinToString("\n") { it.wholeText().trim() }
        status = document.selectFirst("li.status p.detail-info span")?.text().toStatus()
        genre = document.select("li.category p.detail-info a").joinToString { it.text() }
        thumbnail_url = imageOrNull(document.selectFirst("img.image-comic")!!)
    }

    override fun chapterListSelector() = "div.list-chapter li.row:not(.heading):not([style])"

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        date_upload = element.select(".chapters + div").text().toDate()
    }

    override val genresSelector = ".categories-detail ul.nav li:not(.active) a"

    override fun getFilterList(genres: List<Pair<String?, String>>): FilterList = super.getFilterList(genres.ifEmpty { STATIC_GENRES })

    // Configurable, automatic change domain
    private val preferences: SharedPreferences = getPreferences()
    private var hasCheckedRedirect = false

    // Catch redirects
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor { chain ->
            val originalRequest = chain.request()
            val response = chain.proceed(originalRequest)
            if (!hasCheckedRedirect && preferences.getBoolean(AUTO_CHANGE_DOMAIN_PREF, false)) {
                hasCheckedRedirect = true
                val originalHost = baseUrl.toHttpUrl().host
                val newHost = response.request.url.host
                if (newHost != originalHost) {
                    val newBaseUrl = "${response.request.url.scheme}://$newHost"
                    preferences.edit()
                        .putString(BASE_URL_PREF, newBaseUrl)
                        .apply()
                }
            }
            response
        }
        rateLimit(5)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val autoDomainPref = SwitchPreferenceCompat(screen.context).apply {
            key = AUTO_CHANGE_DOMAIN_PREF
            title = AUTO_CHANGE_DOMAIN_TITLE
            summary = AUTO_CHANGE_DOMAIN_SUMMARY
            setDefaultValue(false)
        }
        screen.addPreference(autoDomainPref)
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val AUTO_CHANGE_DOMAIN_PREF = "autoChangeDomain"
        private const val AUTO_CHANGE_DOMAIN_TITLE = "Tự động cập nhật domain"
        private const val AUTO_CHANGE_DOMAIN_SUMMARY =
            "Khi mở ứng dụng, ứng dụng sẽ tự động cập nhật domain mới nếu website chuyển hướng."

        private val STATIC_GENRES = listOf(
            Pair(null, "Tất cả"),
            Pair("action", "Action"),
            Pair("truong-thanh", "Adult"),
            Pair("phieu-luu", "Adventure"),
            Pair("anime", "Anime"),
            Pair("chuyen-sinh", "Chuyển Sinh"),
            Pair("comedy", "Comedy"),
            Pair("nau-an", "Cooking"),
            Pair("comic", "Comic"),
            Pair("co-dai", "Cổ Đại"),
            Pair("drama", "Drama"),
            Pair("dam-my", "Đam Mỹ"),
            Pair("ecchi", "Ecchi"),
            Pair("fantasy", "Fantasy"),
            Pair("harem", "Harem"),
            Pair("historical", "Historical"),
            Pair("horror", "Horror"),
            Pair("live-action", "Live action"),
            Pair("manga", "Manga"),
            Pair("manhua", "Manhua"),
            Pair("manhwa", "Manhwa"),
            Pair("martial-arts", "Martial Arts"),
            Pair("mature", "Mature"),
            Pair("mystery", "Mystery"),
            Pair("mecha", "Mecha"),
            Pair("ngon-tinh", "Ngôn Tình"),
            Pair("one-shot", "One shot"),
            Pair("psychological", "Psychological"),
            Pair("romance", "Romance"),
            Pair("school-life", "School Life"),
            Pair("shoujo", "Shoujo"),
            Pair("shoujo-ai", "Shoujo Ai"),
            Pair("shounen", "Shounen"),
            Pair("slice-of-life", "Slice of Life"),
            Pair("seinen", "Seinen"),
            Pair("smut", "Smut"),
            Pair("sci-fi", "Sci-fi"),
            Pair("soft-yaoi", "Soft Yaoi"),
            Pair("soft-yuri", "Soft Yuri"),
            Pair("sports", "Sports"),
            Pair("supernatural", "Supernatural"),
            Pair("josei", "Josei"),
            Pair("thieu-nhi", "Thiếu Nhi"),
            Pair("trinh-tham", "Trinh Thám"),
            Pair("truyen-mau", "Truyện Màu"),
            Pair("tragedy", "Tragedy"),
            Pair("webtoon", "Webtoon"),
            Pair("xuyen-khong", "Xuyên Không"),
            Pair("gender-bender", "Gender Bender"),
            Pair("yuri", "Yuri"),
            Pair("he-thong", "Hệ Thống"),
            Pair("yaoi", "Yaoi"),
        )
    }
}
