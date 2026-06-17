package eu.kanade.tachiyomi.extension.vi.cuutruyenmoe

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CuuTruyenMoe :
    HttpSource(),
    ConfigurableSource {

    override val name = "CuuTruyen (unoriginal)"

    override val baseUrl = "https://cuutruyen.moe"

    override val lang = "vi"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (!request.url.toString().startsWith(baseUrl)) return@addInterceptor response

            val body = response.peekBody(Long.MAX_VALUE).string()
            if (!body.contains("wire:initial-data") || !body.contains("enter-secret")) {
                return@addInterceptor response
            }

            response.close()
            submitConfiguredPassword(chain, body)
            chain.proceed(request)
        }
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "website_password"
            title = "Mật khẩu truy cập website"
            summary = "Mặc định: $DEFAULT_PASSWORD"
            setDefaultValue(DEFAULT_PASSWORD)
        }.also(screen::addPreference)
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/tim-kiem?sort=-views&filter[status]=2,1&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("div.manga-vertical").map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("div.p-2 a")!!
                setUrlWithoutDomain(linkElement.absUrl("href"))
                title = linkElement.text()
                thumbnail_url = element.selectFirst("div.cover")?.extractBackgroundImage()
            }
        }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = document.selectFirst("a[href*='page=${currentPage + 1}']") != null

        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/tim-kiem?sort=-updated_at&filter[status]=2,1&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder().apply {
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.toUriPart())
                    is StatusFilter -> {
                        val status = filter.toUriPart()
                        if (status.isNotEmpty()) {
                            addQueryParameter("filter[status]", status)
                        }
                    }
                    is GenreFilter -> {
                        val selectedGenres = filter.state
                            .filter { it.state }
                            .joinToString(",") { it.id }
                        if (selectedGenres.isNotEmpty()) {
                            addQueryParameter("filter[accept_genres]", selectedGenres)
                        }
                    }
                    else -> {}
                }
            }

            if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
            }
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList(): FilterList = getFilters()

    // =============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("span.grow.text-lg")!!.text()
            author = document.selectFirst("a[href*=/tac-gia/]")?.text()
            genre = document.select("div.mt-2.flex.flex-wrap.gap-1 a[href*=/the-loai/]").joinToString { it.text() }
            thumbnail_url = document.selectFirst("div.cover-frame div.cover, div.cover-frame")?.extractBackgroundImage()
            description = document.selectFirst("div.mg-plot")?.select("p")
                ?.drop(1)
                ?.joinToString("\n") { it.text() }
                ?.trim()
                ?.takeIf { it.isNotBlank() }

            status = document.selectFirst("a[href*='filter[status]'] span, a[href*='filter%5Bstatus%5D'] span")
                ?.text()
                ?.let { statusText ->
                    when {
                        statusText.contains("Đã hoàn thành") -> SManga.COMPLETED
                        statusText.contains("Đang tiến hành") -> SManga.ONGOING
                        else -> SManga.UNKNOWN
                    }
                } ?: SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("ul.overflow-y-auto a[href*=/truyen/]").mapNotNull { chapterElement ->
            val chapterName = chapterElement.selectFirst("div.grow span.text-ellipsis, div.grow span.truncate")
                ?.text()
                ?.trim()
                .orEmpty()
            if (chapterName.isEmpty()) return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(chapterElement.absUrl("href"))
                name = chapterName
                date_upload = dateFormat.tryParse(chapterElement.selectFirst("span.timeago[datetime]")?.attr("datetime"))
            }
        }.distinctBy { it.url }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }

    // ============================== Password ===============================

    private fun submitConfiguredPassword(chain: okhttp3.Interceptor.Chain, html: String) {
        val wireDataStr = WIRE_INITIAL_DATA_REGEX.find(html)?.groupValues?.get(1)
            ?.let { Parser.unescapeEntities(it, true) }
            ?: throw IOException("Gate: wire:initial-data not found")

        val csrfToken = LIVEWIRE_TOKEN_REGEX.find(html)?.groupValues?.get(1)
            ?: throw IOException("Gate: CSRF token not found")

        val password = preferences.getString("website_password", DEFAULT_PASSWORD)!!
            .ifBlank { DEFAULT_PASSWORD }

        val wireData = wireDataStr.parseAs<JsonObject>()
        val fingerprint = wireData["fingerprint"]!!.jsonObject
        val serverMemo = wireData["serverMemo"]!!.jsonObject

        val livewireHeaders = Headers.Builder()
            .add("Content-Type", "application/json")
            .add("X-CSRF-TOKEN", csrfToken)
            .add("X-Livewire", "true")
            .add("Accept", "text/html, application/xhtml+xml")
            .add("Referer", "$baseUrl/")
            .build()

        val submitPayload = buildJsonObject {
            put("fingerprint", fingerprint)
            put("serverMemo", serverMemo)
            putJsonArray("updates") {
                addJsonObject {
                    put("type", "syncInput")
                    putJsonObject("payload") {
                        put("id", "s1")
                        put("name", "password")
                        put("value", password)
                    }
                }
                addJsonObject {
                    put("type", "callMethod")
                    putJsonObject("payload") {
                        put("id", "c1")
                        put("method", "submit")
                        putJsonArray("params") {}
                    }
                }
            }
        }

        val submitRequest = POST(
            "$baseUrl/livewire/message/enter-secret",
            livewireHeaders,
            submitPayload.toJsonRequestBody(),
        )
        chain.proceed(submitRequest).close()
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val imageUrls = document.select("div.text-center > img.max-w-full").mapNotNull { image ->
            image.extractImageUrl()
        }.takeIf { it.isNotEmpty() }
            ?: throw Exception("Could not find image data")

        return imageUrls.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    private fun Element.extractImageUrl(): String? {
        for (attr in arrayOf("src", "data-src", "data-original", "data-lazy-src")) {
            val url = absUrl(attr)
            if (url.isNotBlank()) return url
        }
        return null
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    private fun Element.extractBackgroundImage(): String? {
        val style = attr("style")
        return BACKGROUND_IMAGE_REGEX.find(style)?.groupValues?.get(1)
    }

    companion object {
        private val BACKGROUND_IMAGE_REGEX = Regex("""background-image:\s*url\(['"]?(.*?)['"]?\)""")
        private val WIRE_INITIAL_DATA_REGEX = Regex("""wire:initial-data="([^"]+)"""")
        private val LIVEWIRE_TOKEN_REGEX = Regex("""livewire_token\s*=\s*'([^']+)'""")
        private const val DEFAULT_PASSWORD = "5"
    }
}
