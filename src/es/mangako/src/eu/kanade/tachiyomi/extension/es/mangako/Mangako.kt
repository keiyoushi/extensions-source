package eu.kanade.tachiyomi.extension.es.mangako

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody

@Source
abstract class Mangako : KeiSource() {

    private val apiUrl = "https://api.mangako.xyz"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder =
        rateLimit(3) { it.host == "api.mangako.xyz" }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Authorization", "Bearer null")
        set("Accept", "application/json, text/plain, */*")
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage =
        searcher(type = "popular", page = page)

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        searcher(type = "chapters", page = page)

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage =
        searcher(type = "popular", page = page, query = query)

    private suspend fun searcher(type: String, page: Int, query: String = ""): MangasPage {
        val body = multipartBody(
            "inputSearch" to query,
            "numberPage" to page.toString(),
            "optionSelected" to "Nombre",
            "type" to type,
        )
        val response = client.post("$apiUrl/api/v1/searcher/type/search", headers, body)
            .parseAs<SearchResponse>()

        // type=chapters can list the same title more than once; keep first occurrence
        val mangas = response.datas.map { it.toSManga() }.distinctBy { it.url }
        val hasNextPage = response.pages.any { it.number > page }
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // One endpoint returns both details and the full chapter list
        val body = multipartBody("dataId" to manga.url)
        val response = client.post("$apiUrl/api/v1/title", headers, body)
            .parseAs<TitleResponse>()

        val updatedManga = response.datas.infoTitle.toSManga(manga.url)
        val chapterList = response.chapters.chapters.volume
            .flatMap { it.chapters }
            .map { it.toSChapter() }

        return SMangaUpdate(updatedManga, chapterList)
    }

    override fun getMangaUrl(manga: SManga): String =
        "$baseUrl/title/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String =
        "$baseUrl/chapter/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val id = when (url.pathSegments.firstOrNull()) {
            "title" -> url.pathSegments.getOrNull(1)
            "chapter" -> {
                val chapterId = url.pathSegments.getOrNull(1) ?: return null
                val chapterResp = client.post(
                    "$apiUrl/api/v1/chapter",
                    headers,
                    multipartBody("dataId" to chapterId),
                ).parseAs<ChapterResponse>()
                // titleLink example: "/title/35/onryo-biyori"
                chapterResp.title?.titleLink
                    ?.toHttpUrlOrNull()
                    ?.pathSegments
                    ?.getOrNull(1)
                    ?: chapterResp.title?.titleLink
                        ?.trimStart('/')
                        ?.split('/')
                        ?.getOrNull(1)
            }
            else -> null
        } ?: return null

        val response = client.post(
            "$apiUrl/api/v1/title",
            headers,
            multipartBody("dataId" to id),
        ).parseAs<TitleResponse>()
        return response.datas.infoTitle.toSManga(id)
    }

    // ============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.post(
            "$apiUrl/api/v1/chapter",
            headers,
            multipartBody("dataId" to chapter.url),
        ).parseAs<ChapterResponse>()

        return response.images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Helpers ==============================

    private fun multipartBody(vararg parts: Pair<String, String>): RequestBody =
        MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                parts.forEach { (name, value) ->
                    addFormDataPart(name, value)
                }
            }
            .build()

    private fun String.toHttpUrlOrNull(): HttpUrl? =
        runCatching { "https://www.mangako.xyz${if (startsWith("/")) this else "/$this"}".toHttpUrl() }.getOrNull()
}
