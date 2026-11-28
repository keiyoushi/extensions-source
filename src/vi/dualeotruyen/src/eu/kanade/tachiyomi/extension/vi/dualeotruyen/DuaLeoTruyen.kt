package eu.kanade.tachiyomi.extension.vi.dualeotruyen

import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class DuaLeoTruyen : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(5)

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/truyen-tranh-hot?page=$page", Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/truyen-moi-cap-nhat?page=$page", Fetch.DOCUMENT).asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .build()
            return mangaListParse(client.get(url, Fetch.DOCUMENT).asJsoup())
        }

        val genrePath = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        return if (genrePath != null) {
            mangaListParse(client.get("$baseUrl$genrePath?page=$page", Fetch.DOCUMENT).asJsoup())
        } else {
            getPopularManga(page)
        }
    }

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select(".box_list .li_truyen").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".pagination a.next") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("a[href*=/truyen-tranh/]")!!
        setUrlWithoutDomain(linkElement.absUrl("href"))
        title = linkElement.selectFirst(".name")!!.text()
        thumbnail_url = linkElement.selectFirst(".img img")?.absUrl("data-src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen-tranh") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen-tranh/$slug")
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document, manga),
            chapters = chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = SManga.create().apply {
        url = manga.url
        title = document.selectFirst(".box_info_right h1")!!.text()
        genre = document.select(".list-tag-story a")
            .joinToString { it.text() }
            .ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".story-detail-info"))
            .field("Nhóm dịch", document.infoItem("Nhóm dịch"))
            .altTitles(document.infoItem("Tên Khác")?.split(",").orEmpty(), "Tên khác")
            .build()
        status = parseStatus(document.infoItem("Tình trang"))
        thumbnail_url = document.selectFirst(".box_info_left .img img")?.absUrl("src")
    }

    private fun Document.infoItem(label: String): String? = selectFirst(".info-item:containsOwn($label:)")
        ?.text()
        ?.substringAfter(':')

    private fun parseStatus(statusText: String?): Int {
        val normalized = statusText?.lowercase(Locale.ROOT)

        return when {
            normalized == null -> SManga.UNKNOWN
            "hoàn thành" in normalized || "full" in normalized -> SManga.COMPLETED
            "đang cập nhật" in normalized -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private fun chapterListParse(document: Document): List<SChapter> = document
        .select(".chapter-item")
        .map { element ->
            SChapter.create().apply {
                val linkElement = element.selectFirst(".chap_name a")!!
                setUrlWithoutDomain(linkElement.absUrl("href"))
                name = linkElement.text()
                date_upload = parseDate(element.selectFirst(".chap_update")?.text())
            }
        }

    private fun parseDate(dateStr: String?): Long = dateFormat.tryParseDate(dateStr, dateZone)

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter)
        val capture = synchronized(pageCaptures) {
            pageCaptures[url]
        }
        if (capture != null) {
            return capture.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
        }

        val pages = captureMutex.withLock {
            synchronized(pageCaptures) {
                pageCaptures[url]
            } ?: capturePages(url).also { captured ->
                synchronized(pageCaptures) {
                    if (pageCaptures.size >= 10) {
                        pageCaptures.remove(pageCaptures.entries.first().key)
                    }
                    pageCaptures[url] = captured
                }
            }
        }
        return pages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    @OptIn(NativeWebViewApi::class)
    private suspend fun capturePages(chapterUrl: String): List<String> {
        val bridgeName = randomName()
        val errorBridgeName = randomName()
        val passName = randomName()
        val rejectName = randomName()
        val siteHost = baseUrl.toHttpUrl().host
        val siteOrigin = chapterUrl.toHttpUrl().run { "$scheme://$host" }
        return runWebView<String>(timeout = 60.seconds) {
            val emptyResponse = WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
            interceptRequest { request ->
                val requestUrl = request.url?.toString()?.toHttpUrlOrNull()
                    ?: return@interceptRequest emptyResponse
                val host = requestUrl.host
                val isImage = request.requestHeaders["Accept"]?.startsWith("image/") == true
                val allowed = host == siteHost ||
                    host.endsWith(".$siteHost") ||
                    host.endsWith(".challenges.cloudflare.com") ||
                    isImage
                if (allowed) null else emptyResponse
            }

            webMessageBridge(bridgeName, setOf(siteOrigin)) { payload ->
                resolve(payload)
            }
            webMessageBridge(errorBridgeName, setOf(siteOrigin)) { message ->
                reject(Exception(message))
            }

            val hookScript = asset("dualeo_hook.js")
                .replace("__DUET_PASS__", passName)
                .replace("__DUET_REJECT__", rejectName)
                .replace("__DUET_BRIDGE__", bridgeName)
                .replace("__DUET_ERROR_BRIDGE__", errorBridgeName)
            injectAtDocumentStart(hookScript, setOf(siteOrigin))

            val captureScript = "((${asset("dualeo_pages.js")})(window.$passName, window.$rejectName));"
            onPageFinished { evaluateJs(captureScript) }

            // Fallback for the http urls the site's decoder emits
            withNativeWebView {
                it.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            loadUrl(chapterUrl)
        }.let { payload ->
            val result = payload.parseAs<CaptureResult>()
            result.pages
        }
    }

    private fun asset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    private fun randomName() = (1..(10..20).random())
        .map { (('a'..'z') + ('A'..'Z')).random() }
        .joinToString("")

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        .select(".main_menu .sub_menu a[href*=/the-loai/]")
        .mapNotNull { element ->
            val name = element.textOrNull() ?: return@mapNotNull null
            val path = element.absUrl("href").toHttpUrl().encodedPath
            GenreOption(name, path)
        }
        .distinctBy { it.uriPart }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")

    private val pageCaptures = LinkedHashMap<String, List<String>>(11, 0.75f, true)
    private val captureMutex = Mutex()

    @Serializable
    private class CaptureResult(
        val pages: List<String>,
    )
}
