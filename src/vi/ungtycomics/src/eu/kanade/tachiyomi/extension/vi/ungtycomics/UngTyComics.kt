package eu.kanade.tachiyomi.extension.vi.ungtycomics

import android.annotation.SuppressLint
import android.app.Application
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class UngTyComics : ParsedHttpSource(), ConfigurableSource {

    override val name = "Ưng Tỷ Comics"

    override val baseUrl by lazy {
        when {
            System.getenv("CI") == "true" -> MIRRORS.joinToString("#, ")
            else -> preferences.getString(PREF_BASE_URL, MIRRORS[0])!!
        }
    }

    override val lang = "vi"

    override val supportsLatest = true

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .ignoreAllSSLErrors()
        .build()

    private fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
        val naiveTrustManager = @SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        }
        val insecureSocketFactory = SSLContext.getInstance("TLSv1.2").apply {
            val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
            init(null, trustAllCerts, SecureRandom())
        }.socketFactory
        sslSocketFactory(insecureSocketFactory, naiveTrustManager)
        hostnameVerifier { _, _ -> true }
        return this
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/truyen-hot?page=$page", headers)

    override fun popularMangaSelector() = "div.item-comics"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst(".content-title a")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        thumbnail_url = element.selectFirst(".content-image img")?.absUrl("data-src")
    }

    override fun popularMangaNextPageSelector() = "ul.pagination li:has(.fa-angle-double-right):not(.disabled)"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/truyen-tranh?page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genreFilter = filters.ifEmpty { getFilterList() }
            .filterIsInstance<GenreFilter>()
            .firstOrNull()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addPathSegment("search")
                addQueryParameter("query_string", query)
            } else if (genreFilter != null) {
                addPathSegments(genreFilter.genres[genreFilter.state].path)
            } else {
                addPathSegment("truyen-tranh")
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
        val statusText = document.selectFirst(".comics-info .meta_label:contains(Trạng thái) + .meta_info")?.text()

        title = document.selectFirst(".title-heading")!!.text()
        author = document.selectFirst(".comics-info .meta_label:contains(Tác giả) + .meta_info")?.text()
        genre = document.selectFirst(".comics-info .meta_label:contains(Thể loại) + .meta_info")?.text()
        status = when (statusText) {
            "Đang tiến hành" -> SManga.ONGOING
            "Đã đủ bộ" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst(".comics-thumbnail img")?.absUrl("src")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        var page = 2

        return buildList {
            document.select(chapterListSelector())
                .forEach { add(chapterFromElement(it)) }

            while (document.selectFirst(chapterNextPageSelector()) != null) {
                val url = response.request.url.newBuilder()
                    .addQueryParameter("page", page.toString())
                    .build()

                document = client.newCall(GET(url, headers)).execute().asJsoup()
                document.select(chapterListSelector())
                    .forEach { add(chapterFromElement(it)) }
                page++
            }
        }
    }

    override fun chapterListSelector() = ".list-comics-chapter .item-chapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst(".episode-title a")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            name = it.text()
        }
        date_upload = try {
            val date = element.selectFirst(".episode-date span")!!.text()

            DATE_FORMAT.parse(date)!!.time
        } catch (_: Exception) {
            0L
        }
    }

    private fun chapterNextPageSelector() = popularMangaNextPageSelector()

    override fun pageListParse(document: Document) =
        document.select("img.chapter-img").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("data-src"))
        }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Không dùng được khi tìm kiếm bằng chữ"),
        GenreFilter(getGenreList()),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_BASE_URL
            title = "Tên miền"
            summary = "%s"
            entries = MIRRORS
            entryValues = MIRRORS
            setDefaultValue(MIRRORS[0])

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Khởi động lại ứng dụng để áp dụng cài đặt mới.", Toast.LENGTH_LONG)
                    .show()
                true
            }
        }.also(screen::addPreference)
    }
}

private const val PREF_BASE_URL = "baseUrl"
private val MIRRORS = arrayOf("https://www.ungtycomics.io", "https://ungtycomicsvip.com", "https://topdammy.com")
private val DATE_FORMAT = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT)

private class GenreFilter(val genres: List<Genre>) : Filter.Select<String>(
    "Thể loại",
    genres.map { it.name }.toTypedArray(),
)

private class Genre(val name: String, val path: String)

// https://ungtycomicsvip.com/truyen-tranh
// copy([...document.querySelectorAll(".item-category a")].map((e) => `Genre("${e.textContent.trim()}", "${new URL(e.href).pathname.replace("/", "")}"),`).join("\n"))
// removed the Truyện Chữ genre since this is not a light novel reader. there's nothing in that genre
// anyways.
private fun getGenreList() = listOf(
    Genre("Tất cả", "truyen-tranh"),
    Genre("Truyện hot", "truyen-hot"),
    Genre("Giới giải trí", "gioi-giai-tri.html"),
    Genre("Ngôn Tình", "ngon-tinh.html"),
    Genre("Cổ Trang", "co-trang.html"),
    Genre("Lãng Mạn", "lang-man.html"),
    Genre("Đam Mỹ", "dam-my.html"),
    Genre("Boys Love", "boys-love.html"),
    Genre("Manhua", "manhua.html"),
    Genre("Romance", "romance.html"),
    Genre("Ngược", "nguoc.html"),
    Genre("Sủng", "sung.html"),
    Genre("Cung Đấu", "cung-dau.html"),
    Genre("Drama", "drama.html"),
    Genre("Trinh Thám", "trinh-tham.html"),
    Genre("Học Đường", "hoc-duong.html"),
    Genre("Xuyên Không", "xuyen-khong.html"),
    Genre("Trọng Sinh", "trong-sinh.html"),
    Genre("School Life", "school-life.html"),
    Genre("Hiện Đại", "hien-dai.html"),
    Genre("Võng Du", "vong-du.html"),
    Genre("Báo Thù", "bao-thu.html"),
    Genre("Tổng Tài", "tong-tai.html"),
    Genre("ABO", "abo.html"),
    Genre("Hài Hước", "hai-huoc.html"),
    Genre("Niên Hạ", "nien-ha.html"),
    Genre("Hiện Thực", "hien-thuc.html"),
    Genre("Xuyên Nhanh", "xuyen-nhanh.html"),
    Genre("Sư Đồ Luyến", "su-do-luyen.html"),
    Genre("Hệ Thống", "he-thong.html"),
    Genre("Huyết Tộc", "huyet-toc.html"),
    Genre("Hắc Bang", "hac-bang.html"),
    Genre("Full Trọn Bộ", "full-tron-bo.html"),
    Genre("Phá Án", "pha-an.html"),
    Genre("Linh Dị", "linh-di.html"),
    Genre("Tu Tiên", "tu-tien.html"),
    Genre("eSports", "esports.html"),
)
