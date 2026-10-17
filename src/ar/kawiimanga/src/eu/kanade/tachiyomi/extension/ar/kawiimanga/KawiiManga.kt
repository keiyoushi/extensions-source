package eu.kanade.tachiyomi.extension.ar.kawiimanga

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class KawiiManga : KeiSource() {
    private val apiUrl = "https://manga-api.kawaii-anime.com/api/manga"

    private var token: String? = null
    private var tokenExpiry: Instant? = null

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("x-app-key", "km_2026_live")
    }

    // The API rejects requests that only carry x-app-key; it also wants a short-lived token from /token.
    private suspend fun authHeaders(): Headers {
        val current = token?.takeIf { tokenExpiry?.let { Clock.System.now() < it } == true } ?: fetchToken()
        return headers.newBuilder().set("x-app-token", current).build()
    }

    private suspend fun fetchToken(): String {
        val data = client.get("$apiUrl/token", headers).parseAs<Token>()
        token = data.token
        tokenExpiry = Clock.System.now() + (data.expiresIn - 120).seconds
        return data.token
    }

    private suspend fun apiGet(url: String): Response {
        var response = client.get(url, authHeaders(), ensureSuccess = false)
        if (response.code == 401) {
            response.close()
            token = null
            response = client.get(url, authHeaders(), ensureSuccess = false)
        }
        if (!response.isSuccessful) throw HttpException(response.code)
        return response
    }

    private fun Response.toMangasPage(): MangasPage {
        val data = this.parseAs<MangaList>()
        val entries = data.results.map { it.toSManga() }
        return MangasPage(entries, data.hasMore)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = apiGet("$apiUrl/own?action=browse&page=$page&sort=views")
        return response.toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = apiGet("$apiUrl/own?action=browse&page=$page")
        return response.toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/own".toHttpUrl().newBuilder().apply {
            addQueryParameter("action", "search")
            addQueryParameter("q", query)
        }.build()

        return apiGet(url.toString()).toMangasPage()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return apiGet("$apiUrl/own?action=series&slug=$slug").parseAs<Manga>().toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url
        val response = apiGet("$apiUrl/own?action=series&slug=$slug")
        val entrie = response.parseAs<Manga>()

        return SMangaUpdate(
            entrie.toSManga(),
            entrie.chapters.map { it.toSChapter(slug) },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.memo["id"]?.stringOrNull ?: return emptyList()
        val response = apiGet("$apiUrl/own?action=pages&chapterId=$chapterId")
        return response.parseAs<Pages>().pages.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }
}
