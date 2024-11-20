package eu.kanade.tachiyomi.extension.vi.xxmanhwa

import android.app.Application
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.random.Random

class XxManhwa : ParsedHttpSource(), ConfigurableSource {

    override val name = "XXManhwa"

    override val lang = "vi"

    private val defaultBaseUrl = "https://google.xxmanhwa2.top"

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/tat-ca-cac-truyen?page_num=$page", headers)

    override fun popularMangaSelector() = "div[data-type=story]"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val a = element.selectFirst("a")!!

        setUrlWithoutDomain(a.attr("abs:href"))
        title = a.attr("title")
        thumbnail_url = element.selectFirst("div.posts-list-avt")?.attr("abs:data-img")
    }

    override fun popularMangaNextPageSelector() = "div.public-part-page span.current:not(:last-child)"

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")

            // There's definitely a page parameter somewhere, but none of the search queries I've
            // tried on this website goes beyond page 1. Even if I forced `page_num` it still
            // refuses to go to the next page. This is a placeholder.
            addQueryParameter("page_num", page.toString())
            addQueryParameter("s", query)
            addQueryParameter("post_type", "story")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst(".summary__content")?.text()
        thumbnail_url = document.selectFirst("div.col-inner.img-max-width img")?.attr("abs:src")

        val html = document.html()
        val genreMap = "[${html.substringAfter("'cat_story': [").substringBefore("],")}]"
            .parseAs<List<CategoryDto>>()
            .associate { it.termId to it.name }
            .toMap()
        genre = document.selectFirst("div.each-to-taxonomy")
            ?.attr("data-id")
            ?.split(",")
            ?.joinToString { genreMap[it] ?: "Unknown" }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body.string()
        val hidePaidChapters = preferences.getBoolean(KEY_HIDE_PAID_CHAPTERS, false)

        return html
            .substringAfter("var scope_data=")
            .substringBefore(";</script")
            .parseAs<List<ChapterDto>>()
            .filter { it.memberType.isBlank() or !hidePaidChapters }
            .map { it.toSChapter() }
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    private val expiryRegex = Regex("""expire:(\d+)""")

    private val tokenRegex = Regex("""token:"([0-9a-f.]+)"""")

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val loginRequired = document.selectFirst(".story_view_permisstion p.yellowcolor")

        if (loginRequired != null) {
            throw Exception("${loginRequired.text()}. Hãy đăng nhập trong WebView.")
        }

        val html = document.html()
        val body = FormBody.Builder().apply {
            val mangaId = response.request.url.pathSegments.reversed()[1]
            val chapterId = response.request.url.pathSegments.last().split("-")[0]
            val expiry = expiryRegex.find(html)?.groupValues?.get(1)
                ?: throw Exception("Could not find token expiry")
            val token = tokenRegex.find(html)?.groupValues?.get(1)
                ?: throw Exception("Could not find token")
            val src = document.selectFirst("div.cur p[data-src]")?.attr("data-src")
                ?: throw Exception("Could not get filename of first image")
            val iid = buildString {
                repeat(12) {
                    append(('2'..'7') + ('a'..'z'))
                }
            }

            document.selectFirst("form[method=post] > input[type=hidden]")?.let { csrf ->
                add(csrf.attr("name"), csrf.attr("value"))
            }

            add("iid", "_0_$iid")
            add("ipoi", "1")
            add("sid", chapterId)
            add("cid", mangaId)
            add("expiry", expiry)
            add("token", token)
            add("src", "/${src.substringAfterLast("/")}")

            val ebeCaptchaKey = html.substringAfter("action_ebe_captcha('").substringBefore("')")
            val ebeCaptchaRequest = POST(
                "$baseUrl/$ebeCaptchaKey?_wpnonce=$WP_NONCE",
                headers,
                FormBody.Builder().add("nse", Random.nextDouble().toString()).build(),
            )
            val ebeCaptchaResponse = client.newCall(ebeCaptchaRequest).execute().asJsoup()

            ebeCaptchaResponse.select("input").forEach {
                add(it.attr("name"), it.attr("value"))
            }

            add("doing_ajax", "1")
        }.build()

        val req = POST("$baseUrl/chaps/img", headers, body)
        val resp = client.newCall(req).execute().body.string().parseAs<PageDto>()
        val basePageUrl = "https://${resp.media}/${resp.src.substringBeforeLast("/")}/"

        return document.select("div.cur p[data-src]").mapIndexed { i, it ->
            Page(i, imageUrl = basePageUrl + it.attr("data-src").substringAfterLast("/"))
        }
    }

    override fun pageListParse(document: Document) = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

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
        SwitchPreferenceCompat(screen.context).apply {
            key = KEY_HIDE_PAID_CHAPTERS
            title = "Ẩn các chương cần tài khoản"
            summary = "Ẩn các chương truyện cần nạp VIP để đọc.\n$baseUrl/thong-tin-cap-bac-tai-khoan"
            setDefaultValue(false)
        }.let(screen::addPreference)

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
        }.let(screen::addPreference)
    }

    private inline fun <reified T> String.parseAs(): T = json.decodeFromString(this)

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    companion object {
        private const val KEY_HIDE_PAID_CHAPTERS = "hidePaidChapters"

        // The website generates this by creating a canvas, doing some funny things to it, and then
        // gets the SHA256 of the canvas' data URI. Pretty much a static string until the site updates.
        private const val WP_NONCE = "e732af2390628a21d8b7500e621b1493c28d9330b415e88f27b8b4e2f9a440a3"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
