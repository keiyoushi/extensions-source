package eu.kanade.tachiyomi.extension.vi.lavibitwindroom

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class LavibitWindroom : KeiSource() {

    private val apiUrl = "https://wiuimsuuxantojfphywk.supabase.co/rest/v1"
    private val apiKey = "sb_publishable_pHSi-JBvC02r0ktHnpcaMg_5Czd3jI9"

    private fun apiHeaders(key: String): Headers = headersBuilder()
        .set("apikey", key)
        .set("Authorization", "Bearer $key")
        .set("Accept-Profile", "public")
        .set("Referer", "$baseUrl/")
        .build()

    private suspend fun apiGet(path: String): Response = client.get("$apiUrl$path", apiHeaders(apiKey))

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

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]?.stringOrNull ?: chapter.url.substringBefore("/")
        val number = chapter.memo["chapter_number"]?.stringOrNull
            ?: chapter.url.substringAfter("/", "")
        return "$baseUrl/truyen/$slug/$number"
    }

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
    ): SMangaUpdate {
        val slug = manga.url
        val series = apiGet("/series?select=*,chapters(id,chapter_number,title,created_at)&slug=eq.$slug")
            .parseAs<List<SeriesDto>>()
            .first()
        val chapterList = if (fetchChapters) series.toChapterList() else chapters
        return SMangaUpdate(
            manga = if (fetchDetails) series.toSMangaDetails() else manga,
            chapters = chapterList,
        )
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.toInt()

        return apiGet(
            "/chapter_images?select=image_url&chapter_id=eq.$chapterId&order=page_order.asc",
        )
            .parseAs<List<PageDto>>()
            .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
    }
}
