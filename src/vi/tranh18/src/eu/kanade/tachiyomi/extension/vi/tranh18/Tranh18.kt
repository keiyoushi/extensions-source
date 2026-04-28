package eu.kanade.tachiyomi.extension.vi.tranh18

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
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class Tranh18 :
    HttpSource(),
    ConfigurableSource {
    override val lang: String = "vi"

    override val name: String = "Tranh18"

    private val defaultBaseUrl = "https://tranh18.cc"

    override val baseUrl get() = getPrefBaseUrl()

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Common ======================================

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".box-body ul li, .manga-list ul li").map { element ->
            SManga.create().apply {
                val sel = element.selectFirst(".mh-item, .manga-list-2-cover")!!
                val a = sel.selectFirst("a")!!
                setUrlWithoutDomain(a.absUrl("href"))
                title = a.attr("title")
                thumbnail_url = sel.selectFirst("p.mh-cover")?.attr("style")?.let { style ->
                    if (style.contains("url(")) {
                        baseUrl + style.substringAfter("url(").substringBefore(")")
                    } else {
                        null
                    }
                } ?: (baseUrl + sel.selectFirst("img")?.attr("data-original"))
            }
        }
        val hasNextPage = document.selectFirst(".page-pagination li.active ~ li:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/update" + if (page > 1) "?page=$page" else "", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Search ======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("keyword", query)
            } else {
                addPathSegment("comics")
                (filters.ifEmpty { getFilterList() }).forEach {
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

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Details ======================================

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
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

    // ============================== Chapters ======================================

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select("ul.detail-list-select li")
        .map { element ->
            SChapter.create().apply {
                val a = element.selectFirst("a")!!
                setUrlWithoutDomain(a.absUrl("href"))
                name = a.text()
                date_upload = System.currentTimeMillis()
                chapter_number = Regex("""(\d+(?:\.\d+)*)""").find(name)?.value?.toFloatOrNull() ?: 0f
            }
        }
        .sortedByDescending { it.chapter_number }

    // ============================== Pages ======================================

    override fun pageListParse(response: Response): List<Page> = response.asJsoup()
        .select("img.lazy").mapIndexed { index, it ->
            val url = it.absUrl("data-original")
            val finalUrl = if (url.startsWith("https://external-content.duckduckgo.com/iu/")) {
                url.toHttpUrl().queryParameter("u")
            } else {
                url
            }
            Page(index, imageUrl = finalUrl)
        }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ======================================

    override fun getFilterList() = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng từ khóa."),
        GenreList(),
        StatusList(),
        KeywordList(getGenreList()),
    )

    private class GenreList :
        Filter.Select<Genre>(
            "Thể loại",
            arrayOf(
                Genre("Tất cả", "-1"),
                Genre("Manhua", "1"),
                Genre("Manhwa", "2"),
                Genre("Manga", "3"),
            ),
        )

    private class StatusList :
        Filter.Select<Genre>(
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

    // ============================== Preferences ======================================

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

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
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
        }.also(screen::addPreference)
    }

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
