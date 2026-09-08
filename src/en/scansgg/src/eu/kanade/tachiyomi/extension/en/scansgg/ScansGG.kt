package eu.kanade.tachiyomi.extension.en.scansgg

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ScansGG : KeiSource() {

    override val supportsLatest = true

    private val apiUrl = "https://api.scans.gg"
    private val cdnUrl = "https://cdn.scans.gg/uploads"

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT).withZone(ZoneId.of("UTC"))

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("limit", POPULAR_LIMIT.toString())
            .addQueryParameter("offset", ((page - 1) * POPULAR_LIMIT).toString())
            .build()
        val response = client.get(url)
        return popularMangaParse(response)
    }

    private fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<ResponseDto<List<SeriesDto>>>()
        val mangas = dto.data.map { it.toSManga(cdnUrl) }

        // The /series endpoint doesn't return the meta pagination object,
        // so we check if the returned items match our limit
        return MangasPage(mangas, mangas.size == POPULAR_LIMIT)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", LATEST_LIMIT.toString())
            .addQueryParameter("chapters", "true")
            .addQueryParameter("series_details", "true")
            .addQueryParameter("group_details", "true")
            .addQueryParameter("sort", "date")
            .build()
        val response = client.get(url)
        return latestUpdatesParse(response)
    }

    private fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<ResponseDto<List<SeriesDto>>>()
        val mangas = dto.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, dto.meta?.hasMore == true)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", POPULAR_LIMIT.toString())
            addQueryParameter("offset", ((page - 1) * POPULAR_LIMIT).toString())
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }

            val types = filters.firstInstanceOrNull<TypeFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()
            val statuses = filters.firstInstanceOrNull<StatusFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()
            val tags = filters.firstInstanceOrNull<TagFilter>()?.state?.filter { it.state }?.map { it.id } ?: emptyList()

            addQueryParameter("q_type", types.joinToString(",", "[", "]"))
            addQueryParameter("q_status", statuses.joinToString(",", "[", "]"))
            addQueryParameter("q_tags", tags.joinToString(",", "[", "]"))
        }.build()
        val response = client.get(url)
        return popularMangaParse(response)
    }

    // ============================== Details + Chapters ===================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate = coroutineScope {
        val mangaDeferred = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) getChapterList(manga) else chapters }
        SMangaUpdate(
            manga = mangaDeferred.await(),
            chapters = chaptersDeferred.await(),
        )
    }

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("id", manga.url)
            .addQueryParameter("trackers", "true")
            .addQueryParameter("sources", "true")
            .build()

        return client.get(url)
            .parseAs<ResponseDto<SeriesDto>>()
            .data.toSManga(cdnUrl, tagsMap)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        // Parse the series_id that we constructed in Dto.kt to safely open the correct webview
        val url = (apiUrl + chapter.url).toHttpUrl()
        val seriesId = url.queryParameter("series_id") ?: return baseUrl
        return "$baseUrl/series/$seriesId"
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        var hasMore = true

        while (hasMore) {
            val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("series_id", manga.url)
                .addQueryParameter("limit", CHAPTER_LIMIT.toString())
                .addQueryParameter("page", page.toString())
                .addQueryParameter("group_details", "true")
                .build()

            val dto = client.get(url)
                .parseAs<ResponseDto<List<ChapterDto>>>()

            chapters += dto.data.map { it.toSChapter(manga.url, dateFormat) }
            hasMore = dto.meta?.hasMore == true
            page++
        }
        return chapters
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(apiUrl + chapter.url)
        val dto = response.parseAs<ResponseDto<PageListDto>>()
        return dto.data.toPages(cdnUrl)
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        StatusFilter(),
        TagFilter(),
    )

    companion object {
        private const val POPULAR_LIMIT = 21
        private const val LATEST_LIMIT = 14
        private const val CHAPTER_LIMIT = 100
    }
}
