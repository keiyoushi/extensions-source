package eu.kanade.tachiyomi.extension.vi.dualeotruyen

import android.content.SharedPreferences
import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferencesLazy
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DuaLeoTruyen :
    ParsedHttpSource(),
    ConfigurableSource {

    override val name = "Dưa Leo Truyện"

    private val isCi = System.getenv("CI") == "true"

    override val baseUrl: String get() = when {
        isCi -> defaultBaseUrl
        else -> preferences.prefBaseUrl
    }

    private var cachedBaseUrl: String = ""
    private val SharedPreferences.prefBaseUrl: String get() {
        return cachedBaseUrl.takeIf(String::isNotBlank)
            ?: getString(BASE_URL_PREF, defaultBaseUrl)!!.also { cachedBaseUrl = it }
    }

    private val preferences: SharedPreferences by getPreferencesLazy {
        getString(DEFAULT_BASE_URL_PREF, "").let { domain ->
            if (domain != defaultBaseUrl) {
                edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    private val defaultBaseUrl: String = "https://dualeotruyenvh.com"

    override val lang = "vi"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top-ngay.html?page=$page", headers)

    override fun popularMangaSelector() = ".box_list > .li_truyen"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst(".name")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
    }

    override fun popularMangaNextPageSelector() = "div.page_redirect > a.active:not(:last-child)"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/truyen-moi-cap-nhat.html?page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addPathSegment("tim-kiem.html")
                addQueryParameter("key", query)
            } else {
                val genreFilter = filters.ifEmpty { getFilterList() }
                    .filterIsInstance<GenreFilter>()
                    .firstOrNull() ?: return popularMangaRequest(page)
                addPathSegments(genreFilter.genre[genreFilter.state].path)
            }

            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val statusText = document.selectFirst(".info-item:has(.fa-rss)")
            ?.text()
            ?.removePrefix("Tình trang: ")

        title = document.selectFirst(".box_info_right h1")!!.text()
        description = document.selectFirst(".story-detail-info")?.text()
        genre = document.select("ul.list-tag-story li a").joinToString { it.text() }
        status = when (statusText) {
            "Đang cập nhật" -> SManga.ONGOING
            "Full" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst(".box_info_left img")?.absUrl("src")
    }

    override fun chapterListSelector() = ".list-chapters .chapter-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst(".chap_name a")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            name = it.text()
        }
        date_upload = element.selectFirst(".chap_update")?.let { parseDate(it.text()) } ?: 0L
    }

    private fun decodeImageUrl(url: String): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return url
        val filename = httpUrl.pathSegments.lastOrNull() ?: return url
        val filenameStem = filename.substringBeforeLast('.')
        val filenameExt = filename.substringAfterLast('.')

        val decryptedFilenameStem = runCatching { Base64.decode(filenameStem, Base64.URL_SAFE or Base64.NO_PADDING) }
            .getOrElse { return url }
            .mapIndexed { i, byte -> (byte.toInt() xor XOR_KEY[i % XOR_KEY.size].toInt()).toChar() }
            .joinToString("")

        return httpUrl.newBuilder()
            .setPathSegment(httpUrl.pathSize - 1, "$decryptedFilenameStem.$filenameExt")
            .build()
            .toString()
    }

    override fun pageListParse(document: Document): List<Page> {
        countView(document)

        return document.select(".content_view_chap img").mapIndexed { i, img ->
            val url = img.absUrl("data-img").ifEmpty { img.absUrl("src") }
            Page(i, imageUrl = if (img.hasAttr("data-img")) decodeImageUrl(url) else url)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Không dùng được khi tìm kiếm bằng tên truyện"),
        GenreFilter(getGenreList()),
    )

    private fun countView(document: Document) {
        val chapterId = document.selectFirst("input[name=chap]")!!.`val`()
        val comicsId = document.selectFirst("input[name=truyen]")!!.`val`()
        val form = FormBody.Builder()
            .add("action", "update_view_chap")
            .add("truyen", comicsId)
            .add("chap", chapterId)
            .build()
        val request = POST("$baseUrl/process.php", headers, form)

        client.newCall(request).execute().close()
    }

    private fun parseDate(date: String): Long {
        val dateParts = date.split(" ")

        if (dateParts.size == 1) {
            return DATE_FORMAT.parse(date)!!.time
        }

        val calendar = Calendar.getInstance().apply {
            val amount = -dateParts[0].toInt()
            val field = when (dateParts[1]) {
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

    private class Genre(val name: String, val path: String)

    private class GenreFilter(val genre: List<Genre>) :
        Filter.Select<String>(
            "Thể loại",
            genre.map { it.name }.toTypedArray(),
        )

    // copy([...document.querySelectorAll(".sub_menu .li_sub a")].map((e) => `Genre("${e.textContent.trim()}", "${new URL(e).pathname.replace("/", "")}"),`).join("\n"))
    // "Tất cả" and "Truyện full" are custom genres that are lumped in to make my life easier.
    private fun getGenreList() = listOf(
        Genre("Top Ngày", "top-ngay.html"),
        Genre("Top Tuần", "top-tuan.html"),
        Genre("Top Tháng", "top-thang.html"),
        Genre("Top Năm", "top-nam.html"),
        Genre("Truyện full", "truyen-hoan-thanh.html"),
        Genre("Truyện mới", "truyen-tranh-moi.html"),
        Genre("Manga", "the-loai/manga.html"),
        Genre("Manhua", "the-loai/manhua.html"),
        Genre("Manhwa", "the-loai/manhwa.html"),
        Genre("18+", "the-loai/18-.html"),
        Genre("Đam Mỹ", "the-loai/dam-my.html"),
        Genre("Harem", "the-loai/harem.html"),
        Genre("Truyện Màu", "the-loai/truyen-mau.html"),
        Genre("BoyLove", "the-loai/boylove.html"),
        Genre("GirlLove", "the-loai/girllove.html"),
        Genre("Phiêu lưu", "the-loai/phieu-luu.html"),
        Genre("Yaoi", "the-loai/yaoi.html"),
        Genre("Hài Hước", "the-loai/hai-huoc.html"),
        Genre("Bách Hợp", "the-loai/bach-hop.html"),
        Genre("Chuyển Sinh", "the-loai/chuyen-sinh.html"),
        Genre("Drama", "the-loai/drama.html"),
        Genre("Hành Động", "the-loai/hanh-dong.html"),
        Genre("Kịch Tính", "the-loai/kich-tinh.html"),
        Genre("Cổ Đại", "the-loai/co-dai.html"),
        Genre("Echi", "the-loai/echi.html"),
        Genre("Hentai", "the-loai/hentai.html"),
        Genre("Lãng Mạn", "the-loai/lang-man.html"),
        Genre("Người Thú", "the-loai/nguoi-thu.html"),
        Genre("Tình Cảm", "the-loai/tinh-cam.html"),
        Genre("Yuri", "the-loai/yuri.html"),
        Genre("Oneshot", "the-loai/oneshot.html"),
        Genre("Doujinshi", "the-loai/doujinshi.html"),
        Genre("ABO", "the-loai/abo.html"),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = URL_PREF_SUMMARY

            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"

            setDefaultValue(defaultBaseUrl)

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Edit URL"
        private const val URL_PREF_SUMMARY = "For temporary uses. Updating the extension will erase this setting. Leave blank to use the default URL"
        private const val RESTART_APP_MESSAGE = "Restart app to apply new setting."
        private val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        private val XOR_KEY = "dualeo_salt_2025".toByteArray()
    }
}
