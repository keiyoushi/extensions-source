package eu.kanade.tachiyomi.extension.vi.lavibitwindroom

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.HttpException
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class LavibitWindroom : KeiSource() {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private var apiConfig: ApiConfigScraper.Config? = null

    private suspend fun loadApiConfig(refresh: Boolean = false): ApiConfigScraper.Config {
        apiConfig?.takeUnless { refresh }?.let { return it }
        return (
            if (refresh) {
                ApiConfigScraper.rescrape(client, baseUrl, preferences)
            } else {
                ApiConfigScraper.scrape(client, baseUrl, preferences)
            }
            ).also { apiConfig = it }
    }

    private fun apiHeaders(key: String): Headers = Headers.Builder()
        .add("apikey", key)
        .add("Authorization", "Bearer $key")
        .add("Accept-Profile", "public")
        .add("Referer", "$baseUrl/")
        .build()

    private suspend fun apiGet(path: String, refresh: Boolean = false): Response {
        val config = loadApiConfig(refresh)
        return try {
            client.get("${config.apiUrl}$path", apiHeaders(config.apiKey))
        } catch (e: HttpException) {
            if (refresh || e.code !in listOf(400, 401, 403)) throw e
            apiGet(path, refresh = true)
        }
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = apiGet("/series?select=id,slug,title,cover_url&type=eq.comic&order=total_views.desc")
            .parseAs<List<SeriesDto>>()
            .map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = apiGet("/series?select=id,slug,title,cover_url&type=eq.comic&order=updated_at.desc")
            .parseAs<List<SeriesDto>>()
            .map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)

        val mangas = apiGet("/series?select=id,slug,title,cover_url&type=eq.comic&title=ilike.*$query*")
            .parseAs<List<SeriesDto>>()
            .map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    // =============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/truyen/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/truyen/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "truyen") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        val manga = SManga.create().apply { this.url = slug }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url

        val seriesDeferred = async {
            apiGet("/series?select=*&slug=eq.$slug").parseAs<List<SeriesDto>>().first()
        }

        if (fetchDetails && fetchChapters) {
            val series = seriesDeferred.await()
            val chaptersDeferred = async { fetchChapterList(series.id, slug) }
            return@coroutineScope SMangaUpdate(
                manga = series.toSMangaDetails(),
                chapters = chaptersDeferred.await(),
            )
        }

        val series = seriesDeferred.await()
        SMangaUpdate(
            manga = if (fetchDetails) series.toSMangaDetails() else manga,
            chapters = if (fetchChapters) fetchChapterList(series.id, slug) else chapters,
        )
    }

    // ============================== Chapters ==============================

    private suspend fun fetchChapterList(seriesId: Int, slug: String): List<SChapter> = apiGet(
        "/chapters?select=id,chapter_number,title,created_at" +
            "&series_id=eq.$seriesId&order=chapter_number.asc",
    )
        .parseAs<List<ChapterDto>>()
        .map { it.toSChapter(slug) }
        .sortedByDescending { it.chapter_number }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.memo["chapter_id"]?.stringOrNull
            ?: fetchChapterId(chapter)
            ?: return emptyList()

        return apiGet(
            "/chapter_images?select=image_url&chapter_id=eq.$chapterId&order=page_order.asc",
        )
            .parseAs<List<PageDto>>()
            .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
    }

    private suspend fun fetchChapterId(chapter: SChapter): Int? {
        val slug = chapter.url.substringBefore("/")
        val number = chapter.url.substringAfter("/")

        val seriesId = apiGet("/series?select=id&slug=eq.$slug")
            .parseAs<List<IdDto>>()
            .firstOrNull()?.id ?: return null

        return apiGet("/chapters?select=id&series_id=eq.$seriesId&chapter_number=eq.$number")
            .parseAs<List<IdDto>>()
            .firstOrNull()?.id
    }

    @Serializable
    private class IdDto(val id: Int)
}
