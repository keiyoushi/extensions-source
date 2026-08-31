package eu.kanade.tachiyomi.extension.vi.vihentai

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
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ViHentai :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    // ============================ Password Gate ============================

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (request.url.host != baseUrl.toHttpUrl().host) return@addInterceptor response
            val contentType = response.body.contentType()
            val isHtml = contentType?.let {
                (it.type == "text" && it.subtype == "html") || it.subtype == "xhtml+xml"
            } == true
            if (!isHtml) return@addInterceptor response

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

    private fun submitConfiguredPassword(chain: Interceptor.Chain, html: String) {
        val wireDataStr = wireInitialDataRegex.find(html)?.groupValues?.get(1)
            ?.let { Parser.unescapeEntities(it, true) }
            ?: throw IOException("Gate: wire:initial-data not found")
        val csrfToken = livewireTokenRegex.find(html)?.groupValues?.get(1)
            ?: throw IOException("Gate: CSRF token not found")
        val password = preferences.getString("website_password", defaultPassword)!!
            .ifBlank { defaultPassword }

        val wireData = wireDataStr.parseAs<LivewireInitialData>()
        val livewireHeaders = Headers.Builder()
            .add("Content-Type", "application/json")
            .add("X-CSRF-TOKEN", csrfToken)
            .add("X-Livewire", "true")
            .add("Accept", "text/html, application/xhtml+xml")
            .add("Referer", "$baseUrl/")
            .build()

        val submitPayload = LivewireRequest(
            fingerprint = wireData.fingerprint,
            serverMemo = wireData.serverMemo,
            updates = listOf(
                LivewireUpdate(
                    type = "syncInput",
                    payload = SyncInputPayload("s1", "password", password),
                ).toJsonElement(),
                LivewireUpdate(
                    type = "callMethod",
                    payload = CallMethodPayload("c1", "submit", emptyList()),
                ).toJsonElement(),
            ),
        )

        val submitRequest = POST(
            "$baseUrl/livewire/message/enter-secret",
            livewireHeaders,
            submitPayload.toJsonRequestBody(),
        )
        chain.proceed(submitRequest).close()
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
                    is StatusFilter -> addQueryParameter("filter[status]", filter.toUriPart())
                    is GenreFilter -> {
                        val genres = filter.state.filter { it.state }.joinToString(",") { it.id }
                        if (genres.isNotEmpty()) addQueryParameter("filter[accept_genres]", genres)
                    }
                    else -> {}
                }
            }

            if (query.isNotBlank()) addQueryParameter("keyword", query)
            addQueryParameter("page", page.toString())
        }.build()

        return parseMangaPage(client.get(url))
    }

    // =============================== Details ==============================

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("span.grow.text-lg")!!.text()
        author = document.selectFirst("a[href*=/tac-gia/]")?.text()
        genre = document.select("div.mt-2.flex.flex-wrap.gap-1 a[href*=/the-loai/]").joinToString { it.text() }
        thumbnail_url = document.selectFirst("div.cover-frame div.cover, div.cover-frame")?.extractBackgroundImage()
        description = document.selectFirst("div.mg-plot [x-ref=content]")
            ?.wholeText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")
                ?.substringBefore(" - Việt Hentai")

        status = document.selectFirst("a[href*='filter[status]'] span, a[href*='filter%5Bstatus%5D'] span")
            ?.text()
            ?.lowercase()
            ?.let { statusText ->
                when {
                    statusText.contains("đã hoàn thành") -> SManga.COMPLETED
                    statusText.contains("đang tiến hành") -> SManga.ONGOING
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

    private fun parseChapterList(document: Document): List<SChapter> = document.select("ul.overflow-y-auto > li").mapNotNull { chapterRow ->
        val chapterLink = chapterRow.selectFirst("a[href*=/truyen/]") ?: return@mapNotNull null
        val chapterName = chapterRow.selectFirst("span.truncate.text-ellipsis")?.text().orEmpty()
        if (chapterName.isEmpty()) return@mapNotNull null

        SChapter.create().apply {
            setUrlWithoutDomain(chapterLink.absUrl("href"))
            name = chapterName
            date_upload = parseDate(chapterRow.selectFirst("span.timeago[datetime]")?.attr("datetime"))
        }
    }.distinctBy { it.url }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        val packedScript = document.select("script").map { it.data() }
            .firstOrNull { it.contains("eval(function(h,u,n,t,e,r)") }
            ?: return emptyList()

        return runCatching { ViHentaiPacker.extractImageUrls(packedScript) }
            .getOrDefault(emptyList())
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    // ============================== Filters ===============================

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

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        val relatedSectionTitles = listOf("Truyện cùng tác giả", "Có thể bạn thích")

        return relatedSectionTitles.flatMap { sectionTitle ->
            document.select("h5")
                .firstOrNull { it.text() == sectionTitle }
                ?.parent()
                ?.select("div.flex.gap-2.w-full")
                .orEmpty()
                .mapNotNull { card ->
                    val link = card.selectFirst("a[href*=/truyen/]") ?: return@mapNotNull null
                    SManga.create().apply {
                        setUrlWithoutDomain(link.absUrl("href"))
                        title = link.text()
                        thumbnail_url = card.selectFirst("div.cover-sm")?.extractBackgroundImage()
                    }
                }
        }
            .filterNot { it.url == manga.url }
            .distinctBy { it.url }
    }

    // ============================= Utilities ==============================

    private fun Element.extractBackgroundImage(): String? = backgroundImageRegex.find(attr("style"))?.groupValues?.get(1)

    private fun parseDate(date: String?): Long {
        if (date.isNullOrEmpty()) return 0L
        return runCatching {
            LocalDateTime.parse(date, dateFormat)
                .atZone(dateZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private val backgroundImageRegex = Regex("""background-image:\s*url\(['"]?(.*?)['"]?\)""")
    private val genreIdRegex = Regex("""toggleGenre\('(\d+)'\)""")
    private val wireInitialDataRegex = Regex("""wire:initial-data="([^"]+)"""")
    private val livewireTokenRegex = Regex("""livewire_token\s*=\s*'([^']+)'""")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val defaultPassword = "5"
}
