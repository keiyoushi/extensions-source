package eu.kanade.tachiyomi.extension.en.readvagabondmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class ReadVagabondManga : HttpSource() {
    override val name = "Read Vagabond Manga"
    override val baseUrl = "https://readbagabondo.com"
    override val lang = "en"
    override val supportsLatest = false

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.parseAs<List<ChapterDto>>()
        return chapters.map { chapter ->
            chapter.toSChapter()
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = "$baseUrl${manga.url}".toHttpUrl().fragment
        return GET("$baseUrl/api/mihon/mangas/$mangaId/chapters", headers)
    }

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga =
        response.parseAs<MangaDto>().toSManga()

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = "$baseUrl${manga.url}".toHttpUrl().fragment
        return GET("$baseUrl/api/mihon/mangas/$mangaId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapter = response.parseAs<ChapterDto>()
        return (1..chapter.pageCount).map { page ->
            Page(
                index = page - 1,
                imageUrl = "https://bucket.readbagabondo.com/volume-%02d/chapter-%03d/page-%03d.png".format(
                    chapter.volume,
                    chapter.number,
                    page,
                ),
            )
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaId = "$baseUrl${chapter.url}".toHttpUrl().fragment
        return GET(
            "$baseUrl/api/mihon/mangas/$mangaId/chapters/${chapter.chapter_number.toInt()}",
            headers,
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<MangaDto>>()
        return MangasPage(mangas.map { manga -> manga.toSManga() }, false)
    }

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/mihon/mangas", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<MangaDto>>()
        return MangasPage(mangas.map { manga -> manga.toSManga() }, false)
    }

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        val url = "$baseUrl/api/mihon/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}"
}
