package eu.kanade.tachiyomi.extension.en.theinterpretationofshadows

import android.webkit.CookieManager
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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebViewBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.time.Clock
import kotlin.time.Instant

@Source
abstract class TheInterpretationOfShadows : KeiSource() {

    override val supportsLatest = false

    private val botCheckLock = Any()

    private var botCheckPassedAt = Instant.DISTANT_PAST

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::passBotCheck)

    override suspend fun getPopularManga(page: Int) = MangasPage(listOf(createManga()), false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val manga = createManga().takeIf { it.title.contains(query.trim(), ignoreCase = true) }
        return MangasPage(listOfNotNull(manga), false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = createManga().takeIf { url.host == baseUrl.toHttpUrl().host }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = SMangaUpdate(
        if (fetchDetails) createManga() else manga,
        if (fetchChapters) fetchChapterList() else chapters,
    )

    private fun createManga() = SManga.create().apply {
        url = "/"
        title = name
        author = AUTHOR
        artist = AUTHOR
        status = SManga.ONGOING
        description = DESCRIPTION
        thumbnail_url = "$baseUrl/wp-content/uploads/2023/10/cropped-TIOS_Cover_Thumbnail-1.jpg"
    }

    private suspend fun fetchChapterList(): List<SChapter> {
        val comics = buildList {
            var page = 1
            do {
                val url = "$baseUrl/wp-json/wp/v2/comic".toHttpUrl().newBuilder()
                    .addQueryParameter("per_page", PER_PAGE.toString())
                    .addQueryParameter("page", (page++).toString())
                    .addQueryParameter("_fields", "date_gmt,link,title")
                    .build()
                val batch = client.get(url, Fetch.XHR).parseAs<List<ComicDto>>()
                addAll(batch)
            } while (batch.size == PER_PAGE)
        }
        // The site numbers its episodes from the first one, like the "#52." of its episode list
        return comics.mapIndexed { index, comic -> comic.toSChapter(comics.size - index) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("#comic .default-lang img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    /**
     * The site's host answers its "One moment, please..." page (a 415 for images) instead of the site until a
     * browser has run that page's script, then lets it through with a cookie the app shares with the WebView.
     * The site itself is served by LiteSpeed, the check by openresty.
     */
    private fun passBotCheck(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestedAt = Clock.System.now()
        val response = chain.proceed(request)
        if (response.header("Server")?.startsWith("openresty") != true) return response
        response.close()
        synchronized(botCheckLock) {
            // Several requests can hit the check at once: pass it once for all of them
            if (botCheckPassedAt < requestedAt) {
                val appUserAgent = request.header("User-Agent")!!
                val staleCookie = botCheckCookie()
                runWebViewBlocking<Unit>(chain.call()) {
                    // The cookie only holds for the user agent that passed the check
                    userAgent = appUserAgent
                    // The check sends the WebView back to the page with a new cookie
                    onPageStarted { if (botCheckCookie().let { it != null && it != staleCookie }) resolve(Unit) }
                    loadUrl(baseUrl)
                }
                botCheckPassedAt = Clock.System.now()
            }
        }
        return chain.proceed(request)
    }

    private fun botCheckCookie() = CookieManager.getInstance().getCookie(baseUrl)
        ?.split("; ")
        ?.firstOrNull { it.startsWith("$BOT_CHECK_COOKIE=") }

    companion object {
        private const val PER_PAGE = 100
        private const val BOT_CHECK_COOKIE = "wssplashchk"
        private const val AUTHOR = "nyoomian"
        private const val DESCRIPTION = "Everyone has a shadow. These strange creatures carry the unspoken thoughts and " +
            "darkest secrets of the mind, yet they are invisible to the naked eye. Daphnis, however, can see and hear " +
            "these shadows. With this ability, he becomes a self-proclaimed private investigator, offering his " +
            "services for a cheap price."
    }
}
