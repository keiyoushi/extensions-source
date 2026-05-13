package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.Request
import okhttp3.Response

class Dilar : HttpSource() {
    override val name = "Dilar"
    override val baseUrl = "https://dilar.tube"
    override val lang = "ar"
    override val supportsLatest = false

    // Moved from Gmanga
    override val versionId = 2

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/series?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SeriesListDto>()
        val mangas = data.series
            .filterNot { it.isNovel() }
            .map { it.toSManga(::createThumbnail) }
        return MangasPage(mangas, data.hasNextPage)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = SearchRequestDto(query, page).toJsonRequestBody()
        return POST("$baseUrl/api/search/filter", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchListDto>()
        val mangas = data.rows.filterNot { it.isNovel() }
            .map { it.toSManga(::createThumbnail) }

        return MangasPage(mangas, data.hasNextPage)
    }

    // Details

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga) = GET("$baseUrl/api/series/${manga.getMangaId()}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<SeriesDto>().toSManga(::createThumbnail)

    // Chapters

    override fun chapterListRequest(manga: SManga) = GET("$baseUrl/api/series/${manga.getMangaId()}/chapters", headers)

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<ChapterListDto>().chapters.flatMap { chapter ->
        chapter.releases.map { it.toSChapter(chapter) }
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        chapter.url = "${manga.url}/${chapter.url}"
    }

    // Pages

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url.substringBeforeLast("#")}"

    override fun pageListRequest(chapter: SChapter) = GET("$baseUrl/api/chapters/${chapter.url.substringAfterLast("#")}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PageListDto>()
        return data.pages.sortedBy { it.order }
            .mapIndexed { index, page ->
                Page(index, imageUrl = "$baseUrl/uploads/releases/${data.storageKey}/hq/${page.url}")
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // common

    private fun SManga.getMangaId(): String = this.url.substringBeforeLast("/")

    private fun createThumbnail(mangaId: String, cover: String): String {
        val thumbnail = "large_${cover.substringBeforeLast(".")}.webp"

        return "$baseUrl/uploads/manga/cover/$mangaId/$thumbnail"
    }
}
