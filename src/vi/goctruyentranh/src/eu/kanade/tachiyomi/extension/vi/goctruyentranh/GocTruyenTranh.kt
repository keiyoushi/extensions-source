package eu.kanade.tachiyomi.extension.vi.goctruyentranh

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GocTruyenTranh :
    HttpSource(),
    ConfigurableSource {

    override val lang = "vi"

    private val defaultBaseUrl = "https://goctruyentranh.com"

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val name = "GocTruyenTranh"

    override val supportsLatest = true

    private val searchUrl by lazy { "${getPrefBaseUrl()}/baseapi/comics/filterComic" }

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/danh-sach/truyen-moi-cap-nhat?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val manga = document.select("section.mt-12 > .grid > .flex").map { element ->
            SManga.create().apply {
                element.selectFirst("a.line-clamp-2")!!.let {
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = it.text()
                }
                thumbnail_url = element.selectFirst("img")
                    ?.absUrl("src")
                    ?.let { url ->
                        url.toHttpUrlOrNull()
                            ?.queryParameter("url")
                            ?: url
                    }
            }
        }
        val hasNextPage = document.selectFirst("nav ul li") != null
        return MangasPage(manga, hasNextPage)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/danh-sach/truyen-hot?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapter = document.select("section ul li a").map { element ->
            SChapter.create().apply {
                element.selectFirst("a")!!.let {
                    setUrlWithoutDomain(it.absUrl("href"))
                    name = it.selectFirst(".items-center:contains(Chapter)")!!.text()
                    date_upload = parseDate(it.select(".text-center").text())
                }
            }
        }
        return chapter
    }

    private fun parseDate(date: String): Long = runCatching {
        val calendar = Calendar.getInstance()
        val number = date.replace(Regex("[^0-9]"), "").trim().toInt()
        when (date.replace(Regex("[0-9]"), "").lowercase().trim()) {
            "giây trước" -> calendar.apply { add(Calendar.SECOND, -number) }.timeInMillis
            "phút trước" -> calendar.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            "giờ trước" -> calendar.apply { add(Calendar.HOUR, -number) }.timeInMillis
            "ngày trước" -> calendar.apply { add(Calendar.DAY_OF_YEAR, -number) }.timeInMillis
            else -> dateFormat.tryParse(date)
        }
    }.getOrNull() ?: 0L

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.select("section aside:first-child h1").text()
        genre = document.select("span:contains(Thể loại:) ~ a").joinToString { it.text().trim(',', ' ') }
        description = document.select("div.mt-3").joinToString {
            it.select("a, strong").unwrap()
            it.wholeText().trim()
        }
        thumbnail_url = document.selectFirst("section aside:first-child img")
            ?.absUrl("src")
            ?.let { url ->
                url.toHttpUrlOrNull()
                    ?.queryParameter("url")
                    ?: url
            }
        status = parseStatus(document.selectFirst("span:contains(Trạng thái:) + b")?.text())
        author = document.selectFirst("span:contains(Tác giả:) + b")?.text()
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        listOf("Đang Tiến Hành", "Đang Cập Nhật").any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
        listOf("Hoàn Thành", "Đã Hoàn Thành").any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
        listOf("Tạm Ngưng", "Tạm Hoãn").any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("img.lozad").mapIndexed { i, e ->
        Page(i, imageUrl = e.absUrl("data-src"))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = searchUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("keyword", query)
            addQueryParameter("page", page.toString())

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is GenreList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("categories", it) }

                    is StatusList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("status", it) }

                    is ChapterCountList -> addQueryParameter("minChap", filter.values[filter.state].id)

                    is SortByList -> addQueryParameter("sort", filter.values[filter.state].id)

                    is CountryList ->
                        filter.state
                            .filter { it.state }
                            .map { it.id }
                            .forEach { addQueryParameter("country", it) }

                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val json = response.parseAs<SearchDTO>()
        val manga = json.comics.data.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = it.thumbnail
                setUrlWithoutDomain("$baseUrl/" + it.slug)
            }
        }
        val hasNextPage = json.comics.current_page != json.comics.last_page
        return MangasPage(manga, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        GenreList(getGenreList()),
        StatusList(),
        ChapterCountList(),
        SortByList(),
        CountryList(),
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
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
