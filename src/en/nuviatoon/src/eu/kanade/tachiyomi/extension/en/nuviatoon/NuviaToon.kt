package eu.kanade.tachiyomi.extension.en.nuviatoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class NuviaToon : KeiSource() {

    override fun Headers.Builder.configureHeaders() = add("Accept", "application/json")

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangasPage(
        client.get("$baseUrl/nuvia-api/series?per_page=18&page=$page&sort=views&dir=desc"),
    )

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangasPage(
        client.get("$baseUrl/nuvia-api/series?per_page=18&page=$page&sort=created_at&dir=desc"),
    )

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/nuvia-api/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("per_page", "18")
            addQueryParameter("page", page.toString())

            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }

            val statusFilter = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()
            val genreFilter = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
            val sortFilter = filters.firstInstanceOrNull<SortFilter>()

            if (statusFilter != null) {
                addQueryParameter("status", statusFilter)
            }

            if (genreFilter != null) {
                addQueryParameter("genre", genreFilter)
            }

            if (sortFilter != null) {
                addQueryParameter("sort", sortFilter.toUriPart())
                addQueryParameter("dir", sortFilter.toDirPart())
            } else {
                addQueryParameter("sort", "views")
                addQueryParameter("dir", "desc")
            }
        }.build()

        return parseMangasPage(client.get(url))
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val dto = response.parseAs<PaginatedResponse<SeriesDto>>()
        return MangasPage(dto.data.map { it.toSManga() }, dto.hasNextPage)
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        return fetchMangaDetails(SManga.create().apply { this.url = slug }).apply {
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    // ============================= Chapters ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga = if (fetchDetails) fetchMangaDetails(manga) else manga,
        chapters = if (fetchChapters) fetchChapterList(manga) else chapters,
    )

    private suspend fun fetchMangaDetails(manga: SManga): SManga = client.get("$baseUrl/nuvia-api/series/${manga.url}").parseAs<SeriesDto>().toSManga()

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/${chapter.url.substringBefore("?")}"

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> = client.get("$baseUrl/nuvia-api/series/${manga.url}/chapters")
        .parseAs<List<ChapterDto>>()
        .map { it.toSChapter(manga.url) }
        .reversed()

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringAfter("id=")
        return client.get("$baseUrl/nuvia-api/chapters/$id/pages")
            .parseAs<List<PageDto>>()
            .mapIndexed { index, dto -> Page(index, imageUrl = dto.imageUrl) }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        StatusFilter(),
        GenreFilter(),
        SortFilter(),
    )
}
