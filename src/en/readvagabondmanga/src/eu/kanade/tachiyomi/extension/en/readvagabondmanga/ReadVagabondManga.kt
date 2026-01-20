package eu.kanade.tachiyomi.extension.en.readvagabondmanga

import eu.kanade.tachiyomi.extension.en.readvagabondmanga.dto.ChapterDto
import eu.kanade.tachiyomi.extension.en.readvagabondmanga.dto.MangaDto
import eu.kanade.tachiyomi.extension.en.readvagabondmanga.dto.MangaStatus
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import java.time.LocalDate
import java.time.ZoneOffset

class ReadVagabondManga : HttpSource() {
    private val json = Json { ignoreUnknownKeys = true }

    override val name = "Read Vagabond Manga"
    override val baseUrl = "https://readbagabondo.com"
    override val lang = "en"
    override val supportsLatest = false

    private fun MangaDto.toSManga(): SManga {
        return SManga.create().apply {
            title = this@toSManga.title
            url = "/api/mihon/mangas/${this@toSManga.id}"
            thumbnail_url = this@toSManga.cover
            author = this@toSManga.author
            artist = this@toSManga.artist
            description = this@toSManga.description
            status = when (this@toSManga.status) {
                MangaStatus.ONGOING -> SManga.ONGOING
                MangaStatus.COMPLETED -> SManga.COMPLETED
                MangaStatus.HIATUS -> SManga.ON_HIATUS
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = json.decodeFromString<List<ChapterDto>>(response.body.string())
        val mangaId = response.request.url
            .pathSegments
            .last { it != "chapters" }

        return chapters.map { chapter ->
            SChapter.create().apply {
                name = chapter.title
                chapter_number = chapter.number.toFloat()
                url = "/api/mihon/mangas/$mangaId/chapters/${chapter.number}"
                date_upload = LocalDate
                    .parse(chapter.releaseDate)
                    .atStartOfDay()
                    .toInstant(ZoneOffset.UTC)
                    .toEpochMilli()
                scanlator = "Read Vagabond Manga"
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url + "/chapters")
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Not used. Image URLs are direct")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException("Latest updates not supported")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException("Latest updates not supported")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.body.string()
        val manga = json.decodeFromString<MangaDto>(body)
        return manga.toSManga()
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapter = json.decodeFromString<ChapterDto>(response.body.string())
        return (1..chapter.pageCount).map { page ->
            Page(
                index = page - 1,
                imageUrl =
                    "https://manga.readbagabondo.com/volume-${chapter.volume}/chapter-${chapter.number}/page-$page",
            )
        }
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET(baseUrl + chapter.url)

    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body.string()
        val mangas = json.decodeFromString<List<MangaDto>>(body)
        return MangasPage(mangas.map { manga -> manga.toSManga() }, false)
    }

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/api/mihon/mangas")

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body.string()
        val mangas = json.decodeFromString<List<MangaDto>>(body)
        return MangasPage(mangas.map { manga -> manga.toSManga() }, false)
    }

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request = GET("$baseUrl/api/mihon/mangas?q=$query&page=$page")
}
