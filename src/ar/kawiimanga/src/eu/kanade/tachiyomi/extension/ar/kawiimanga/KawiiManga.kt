package eu.kanade.tachiyomi.extension.ar.kawiimanga

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class KawiiManga : KeiSource() {
    private val apiUrl = "https://manga-api.kawaii-anime.com/api/manga".toHttpUrl()

    private val apiHeaders: Headers get() = headers.newBuilder().set("x-app-key", APP_KEY).build()

    private var token: String? = null
    private var tokenExpiry: Instant? = null
    private val tokenMutex = Mutex()

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, "views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = ownUrl("search").addQueryParameter("q", query).build()
        return apiGet(url).toMangasPage()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return fetchSeries(slug).toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val entry = fetchSeries(manga.url)

        return SMangaUpdate(
            entry.toSManga(),
            entry.chapters.map { it.toSChapter(manga.url) },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.memo["id"]?.stringOrNull ?: return emptyList()
        val response = apiGet(ownUrl("pages").addQueryParameter("chapterId", chapterId).build())
        val pageToken = response.request.header("x-app-token")!!
        return response.parseAs<PagesBox>().box.decrypt(pageToken).mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    private suspend fun browse(page: Int, sort: String? = null): MangasPage {
        val url = ownUrl("browse").apply {
            addQueryParameter("page", page.toString())
            sort?.let { addQueryParameter("sort", it) }
        }.build()
        return apiGet(url).toMangasPage()
    }

    private suspend fun fetchSeries(slug: String): Manga = apiGet(ownUrl("series").addQueryParameter("slug", slug).build()).parseAs<Manga>()

    private fun Response.toMangasPage(): MangasPage {
        val data = parseAs<MangaList>()
        return MangasPage(data.results.map { it.toSManga() }, data.hasMore)
    }

    private fun ownUrl(action: String): HttpUrl.Builder = apiUrl.newBuilder()
        .addPathSegment("own")
        .addQueryParameter("action", action)

    private suspend fun authHeaders(): Headers {
        val current = tokenMutex.withLock {
            token?.takeIf { Clock.System.now() < tokenExpiry!! } ?: fetchToken()
        }
        return apiHeaders.newBuilder().set("x-app-token", current).build()
    }

    private suspend fun fetchToken(turnstile: String? = null): String {
        val tokenHeaders = apiHeaders.newBuilder().apply { turnstile?.let { set("x-turnstile", it) } }.build()
        val url = apiUrl.newBuilder().addPathSegment("token").build()
        val data = client.get(url, tokenHeaders, Fetch.XHR).parseAs<Token>()
        token = data.token
        tokenExpiry = Clock.System.now() + (data.expiresIn - 120).seconds
        return data.token
    }

    private suspend fun apiGet(url: HttpUrl): Response {
        var response = client.get(url, authHeaders(), Fetch.XHR, ensureSuccess = false)
        if (response.code == 401) {
            val sentToken = response.request.header("x-app-token")
            val turnstileRequired = response.body.contentType()?.subtype == "json" &&
                response.parseAs<ApiError>().error == "turnstile_required"
            response.close()
            tokenMutex.withLock {
                if (token == sentToken) {
                    fetchToken(if (turnstileRequired) getTurnstileToken(baseUrl, TURNSTILE_SITE_KEY, action = "mint") else null)
                }
            }
            response = client.get(url, authHeaders(), Fetch.XHR, ensureSuccess = false)
        }
        if (!response.isSuccessful) throw HttpException(response.code)
        return response
    }

    companion object {
        private const val APP_KEY = "km_2026_live"
        private const val TURNSTILE_SITE_KEY = "0x4AAAAAAF9JEsXEe0GOy47z"
    }
}
