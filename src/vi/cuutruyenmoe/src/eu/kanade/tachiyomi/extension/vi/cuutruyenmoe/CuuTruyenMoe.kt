package eu.kanade.tachiyomi.extension.vi.cuutruyenmoe

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class CuuTruyenMoe :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
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
        rateLimit(5)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "website_password"
            title = "Mật khẩu truy cập website"
            summary = "Mặc định: $defaultPassword"
            setDefaultValue(defaultPassword)
        }.also(screen::addPreference)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage(client.get("$baseUrl/tim-kiem?sort=-views&filter[status]=2,1&page=$page"))

    private fun parseMangaPage(response: Response): MangasPage {
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

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(client.get("$baseUrl/tim-kiem?sort=-updated_at&filter[status]=2,1&page=$page"))

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        return parseMangaPage(client.get(url))
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tim-kiem").asJsoup()
        .select("label")
        .mapNotNull { element ->
            val id = genreIdRegex.matchEntire(element.attr("@click"))
                ?.groupValues
                ?.get(1)
                ?: return@mapNotNull null
            val name = element.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            GenreOption(name, id)
        }
        .distinctBy { it.id }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Details ==============================

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
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

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen/$slug")
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    // ============================== Related ===============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        val relatedSection = document.select("h5")
            .firstOrNull { it.text() == "Có thể bạn thích" }
            ?.parent()
            ?: return emptyList()

        return relatedSection.select("div.flex.gap-2.w-full").mapNotNull { card ->
            val link = card.selectFirst("a[href*=/truyen/]") ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("div.cover-sm")?.extractBackgroundImage()
            }
        }.distinctBy { it.url }
    }

    // ============================== Chapters ==============================

    private fun parseChapterList(document: Document): List<SChapter> {
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

    private fun submitConfiguredPassword(chain: Interceptor.Chain, html: String) {
        val wireDataStr = wireInitialDataRegex.find(html)?.groupValues?.get(1)
            ?.let { Parser.unescapeEntities(it, true) }
            ?: throw IOException("Gate: wire:initial-data not found")

        val csrfToken = livewireTokenRegex.find(html)?.groupValues?.get(1)
            ?: throw IOException("Gate: CSRF token not found")

        val password = preferences.getString("website_password", defaultPassword)!!
            .ifBlank { defaultPassword }

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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()

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

    // ============================= Utilities ==============================

    private fun Element.extractBackgroundImage(): String? {
        val style = attr("style")
        return backgroundImageRegex.find(style)?.groupValues?.get(1)
    }

    private val backgroundImageRegex = Regex("""background-image:\s*url\(['"]?(.*?)['"]?\)""")
    private val genreIdRegex = Regex("""toggleGenre\('(\d+)'\)""")
    private val wireInitialDataRegex = Regex("""wire:initial-data="([^"]+)"""")
    private val livewireTokenRegex = Regex("""livewire_token\s*=\s*'([^']+)'""")
    private val defaultPassword = "5"
}
