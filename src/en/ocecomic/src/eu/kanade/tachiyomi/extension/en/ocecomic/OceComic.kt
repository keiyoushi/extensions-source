package eu.kanade.tachiyomi.extension.en.ocecomic

import android.webkit.CookieManager
import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.runWebViewBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Source
abstract class OceComic : KeiSource() {

    private val botCheck = Interceptor { passBotCheck(it) }

    // The bot check's own requests go back to its WebView, not through the interceptor running it
    private val proxyClient by lazy { client.newBuilder().apply { interceptors().remove(botCheck) }.build() }

    private val botCheckLock = Any()

    @Volatile
    private var botCheckPassedAt = Instant.DISTANT_PAST

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(botCheck)

    override suspend fun getPopularManga(page: Int) = getComics(listUrl("comic", "all", "popular", page))

    override suspend fun getLatestUpdates(page: Int) = getComics(listUrl("comic", "all", "latest", page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: "latest"
        if (query.isBlank()) {
            val genre = filters.firstInstanceOrNull<GenreFilter>()?.value ?: "all"
            return getComics(listUrl("comic", genre, sort, page))
        }
        // The site's search form sends the words joined by hyphens
        val words = query.lowercase().replace(NON_ALPHANUMERIC, "-").trim('-')
        if (words.isEmpty()) return MangasPage(emptyList(), false)
        return getComics(listUrl("search", words, sort, page))
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val key = segments.getOrNull(1)
        return when (segments.firstOrNull()) {
            // A genre's list; a comic's own page has its number there
            "comic" -> if (key == null || !key.all(Char::isDigit)) {
                getComics(listUrl("comic", key ?: "all", segments.getOrElse(2) { "latest" }, page))
            } else {
                super.getMangasByUrl(url, page)
            }
            "search" -> if (key == null) {
                MangasPage(emptyList(), false)
            } else {
                getComics(listUrl("search", key, segments.getOrElse(2) { "latest" }, page))
            }
            "series", "writer" -> {
                val comics = client.get(url, Fetch.DOCUMENT).asJsoup().select(".items .item").map { it.toSManga() }
                MangasPage(comics, false)
            }
            "publisher" -> getPublisherComics(url, page)
            else -> MangasPage(emptyList(), false)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        // Comic and issue links both start with the comic's number
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val document = client.get("$baseUrl/comic/$id", Fetch.DOCUMENT).asJsoup()
        // An unknown number gets the latest comics
        if (document.selectFirst(".page.home") == null) return null
        return mangaDetailsParse(document).apply {
            this.url = id
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comic/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        // The site lists the issues first to last
        val issues = document.select(".issues a.issue-link").map { link ->
            SChapter.create().apply {
                url = link.absUrl("href").toHttpUrl().pathSegments.take(3).drop(1).joinToString("/")
                name = link.text()
            }
        }
        return SMangaUpdate(mangaDetailsParse(document).apply { url = manga.url }, issues.reversed())
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val home = document.selectFirst(".page.home")!!
        title = home.selectFirst("h1")!!.text()
        thumbnail_url = home.selectFirst("figure img")?.absUrl("src")
        val info = home.select(".info ul").mapNotNull { row ->
            val label = row.selectFirst("span")?.text() ?: return@mapNotNull null
            label to (row.select("li").last() ?: return@mapNotNull null)
        }.toMap()
        author = info["Writer"]?.text()
        genre = listOfNotNull(info["Theme"], info["Genre"]).flatMap { it.select("a") }.map { it.text() }.distinct().joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(home.selectFirst(".about"))
            .field("Publisher", info["Publisher"]?.text())
            .build()
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/comic/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".page.issue figure img").mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("src")) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        Filter.Header("The genre is ignored when searching"),
        GenreFilter(),
    )

    private fun listUrl(section: String, key: String, sort: String, page: Int) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(section)
        .addPathSegment(key)
        .addPathSegment(sort)
        .addPathSegment("page")
        .addPathSegment(page.toString())
        .build()

    private suspend fun getComics(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val comics = document.select(".items .item").map { it.toSManga() }
        // The last page's "next" link goes back to the first
        val lastPage = document.select("select[name=page] option").lastOrNull()?.attr("value")?.toIntOrNull() ?: 0
        return MangasPage(comics, url.pathSegments.last().toInt() < lastPage)
    }

    // A publisher's comics come 50 at a time from the site's script after the first
    private suspend fun getPublisherComics(url: HttpUrl, page: Int): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val pages = document.select(".comic-pagin li").size
        val rows = if (page == 1) {
            document.select(".comic-list ul.item")
        } else {
            val rowsUrl = "$baseUrl/assets/php/ajax.php".toHttpUrl().newBuilder()
                .addQueryParameter("publisher", "comic-pagin")
                .addQueryParameter("publisid", url.pathSegments[1])
                .addQueryParameter("row", page.toString())
                .build()
            client.get(rowsUrl, Fetch.XHR.from(url.toString())).asJsoup().select("ul.item")
        }
        return MangasPage(rows.map { it.toSManga() }, page < pages)
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst("a:has(img)")!!
        url = link.absUrl("href").toHttpUrl().pathSegments[1]
        val cover = link.selectFirst("img")!!
        title = cover.attr("alt")
        thumbnail_url = cover.absUrl("src").replace("/cover-s.", "/cover.")
    }

    /**
     * The host's bot check answers pages and covers with its "One moment, please..." page until a browser has run
     * its script, which a WebView does on its own; its cookie is then shared with the app.
     */
    private fun passBotCheck(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestedAt = Clock.System.now()
        val response = chain.proceed(request)
        if (!response.isBotCheck()) return response
        response.close()
        synchronized(botCheckLock) {
            // Several requests can hit the check at once: pass it once, for all of them
            if (botCheckPassedAt < requestedAt) {
                val host = baseUrl.toHttpUrl().host
                val previous = passCookie()
                try {
                    runWebViewBlocking<Unit>(chain.call()) {
                        userAgent = request.header("User-Agent")!!
                        blockImages = true
                        interceptRequest { webRequest ->
                            when (webRequest.url.host) {
                                null -> null
                                // Without the X-Requested-With that WebView adds to what it sends itself; styles aren't needed
                                host -> proxyRequest(webRequest, proxyClient) { removeHeader("X-Requested-With") } ?: blocked()
                                // The site's ads
                                else -> blocked()
                            }
                        }
                        poll(POLL_INTERVAL) {
                            if (passCookie().let { it != null && it != previous }) resolve(Unit)
                        }
                        loadUrl(baseUrl)
                    }
                } catch (e: WebViewTimeoutException) {
                    throw IOException("The site's bot check did not pass, try again later", e)
                }
                botCheckPassedAt = Clock.System.now()
            }
        }
        return chain.proceed(request)
    }

    private fun blocked() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    private fun passCookie() = CookieManager.getInstance().getCookie(baseUrl)
        ?.split("; ")
        ?.firstOrNull { it.startsWith("$PASS_COOKIE=") }

    private fun Response.isBotCheck() = header("Content-Type")?.startsWith("text/html") == true &&
        peekBody(BOT_CHECK_PEEK).string().contains(BOT_CHECK_MARK)

    companion object {
        private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
        private const val PASS_COOKIE = "wssplashchk"
        private const val BOT_CHECK_MARK = "wsidchk"
        private const val BOT_CHECK_PEEK = 64L * 1024
        private val POLL_INTERVAL = 250.milliseconds
    }
}
