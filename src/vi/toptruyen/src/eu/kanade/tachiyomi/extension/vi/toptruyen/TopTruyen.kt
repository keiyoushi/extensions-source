package eu.kanade.tachiyomi.extension.vi.toptruyen

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class TopTruyen :
    WPComics(),
    ConfigurableSource {

    override val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    override val gmtOffset = null

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("div[id^=page_].page-chapter img").mapIndexed { index, element ->
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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> filter.toUriPart()?.let { url.addPathSegment(it) }
                is StatusFilter -> filter.toUriPart()?.let { url.addQueryParameter("status", it) }
                else -> {}
            }
        }

        when {
            query.isNotBlank() -> url.addQueryParameter(queryParam, query)
            else -> url.addQueryParameter("page", page.toString())
        }

        return GET(url.toString(), headers)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1.title-manga")!!.text()
        description = document.select("p.detail-summary").joinToString { it.wholeText().trim() }
        status = document.selectFirst("li.status p.detail-info span")?.text().toStatus()
        genre = document.select("li.category p.detail-info a").joinToString { it.text() }
        thumbnail_url = imageOrNull(document.selectFirst("img.image-comic")!!)
    }

    override fun chapterListSelector() = "div.list-chapter li.row:not(.heading):not([style])"

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        date_upload = element.select(".chapters + div").text().toDate()
    }

    override val genresSelector = ".categories-detail ul.nav li:not(.active) a"

    override fun getFilterList(): FilterList {
        if (genreList.isEmpty()) {
            genreList = listOf(
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
        return super.getFilterList()
    }

    // Configurable, automatic change domain
    private val preferences: SharedPreferences = getPreferences()
    private var hasCheckedRedirect = false

    // Catch redirects
    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
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
        .rateLimit(5)
        .build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val autoDomainPref = androidx.preference.SwitchPreferenceCompat(screen.context).apply {
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
    }
}
