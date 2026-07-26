package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Source
abstract class Dilar : KeiSource() {
    override val supportsLatest = false

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/api/series?page=$page")
        val data = response.parseAs<SeriesListDto>()
        val entries = data.series
            .filterNot { it.isNovel() }
            .map { it.toSManga(::createThumbnail) }
        return MangasPage(entries, data.hasNextPage)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val body = SearchRequestDto(query, page).toJsonRequestBody()
        val response = client.post("$baseUrl/api/search/filter", body)
        val data = response.parseAs<SearchListDto>()
        val entries = data.rows.filterNot { it.isNovel() }
            .map { it.toSManga(::createThumbnail) }

        return MangasPage(entries, data.hasNextPage)
    }

    // Details & Chapters

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async {
            if (!fetchDetails) return@async manga
            client.get("$baseUrl/api/series/${manga.getMangaId()}")
                .parseAs<SeriesDto>()
                .toSManga(::createThumbnail)
        }

        val chaptersDeferred = async {
            if (fetchChapters) getChapterList(manga) else chapters
        }

        SMangaUpdate(
            manga = mangaDeferred.await(),
            chapters = chaptersDeferred.await(),
        )
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val response = client.get("$baseUrl/api/series/${manga.getMangaId()}/chapters")
        val data = response.parseAs<ChapterListDto>()
        return data.chapters.flatMap { chapter ->
            chapter.releases.map { it.toSChapter(chapter, manga.url) }
        }
    }

    // Pages

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url.substringBeforeLast("#")}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl/api/chapters/${chapter.url.substringAfterLast("#")}")
        val data = response.parseAs<PageListDto>()
        return data.pages.sortedBy { it.order }
            .mapIndexed { index, page ->
                Page(index, imageUrl = "$baseUrl/uploads/releases/${data.storageKey}/hq/${page.url}")
            }
    }

    // common

    private fun SManga.getMangaId(): String = this.url.substringBeforeLast("/")

    private fun createThumbnail(mangaId: String, cover: String): String {
        val thumbnail = "large_${cover.substringBeforeLast(".")}.webp"

        return "$baseUrl/uploads/manga/cover/$mangaId/$thumbnail"
    }
}
