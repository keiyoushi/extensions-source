package eu.kanade.tachiyomi.extension.fr.lugnicascans

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
import java.text.DecimalFormat

class LugnicaScans : HttpSource() {
    override val name = "Lugnica Scans"

    override val baseUrl = "https://lugnica-scans.com"

    override val lang = "fr"

    override val supportsLatest = false

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/get/homegrid/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val rawMangas = response.parseAs<List<HomePageManga>>()

        val mangas = rawMangas.map { manga ->
            SManga.create().apply {
                title = manga.mangaTitle
                setUrlWithoutDomain("/api/get/card/${manga.mangaSlug}")

                thumbnail_url = "$baseUrl/upload/min_cover/${manga.mangaImage}"
            }
        }

        return MangasPage(mangas, mangas.size == 10)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val mangaDetails = response.parseAs<MangaDetailsResponse>().manga

        val slug = response.request.url.pathSegments.last()

        return SManga.create().apply {
            url = "/api/get/card/$slug"
            title = mangaDetails.title
            thumbnail_url = "$baseUrl/upload/min_cover/${mangaDetails.image}"
            status = when (mangaDetails.status) {
                "0" -> SManga.ONGOING
                "1" -> SManga.COMPLETED
                "2" -> SManga.LICENSED
                "3" -> SManga.CANCELLED
                "4" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            description = mangaDetails.description

            val genres = mangaDetails.theme + mangaDetails.genre
            if (genres.isNotEmpty()) {
                genre = genres.filter { it.toInt() in GENRES }.joinToString { GENRES[it.toInt()]!! }
            }
            author = mangaDetails.author
            artist = mangaDetails.artist
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = "$baseUrl/${manga.url}".toHttpUrl().pathSegments.last()
        return "$baseUrl/manga/$slug"
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val details = response.parseAs<MangaDetailsResponse>()
        val slug = response.request.url.pathSegments.last()

        return details.chapters.values.flatten().map { chapterDetail ->
            SChapter.create().apply {
                url = "/api/get/chapter/$slug/${chapterDetail.chapter}"
                name = "Chapitre " + DecimalFormat("0.#").format(chapterDetail.chapter)
                date_upload = chapterDetail.date.trim('\r', '\n').toLong() * 1000
                chapter_number = chapterDetail.chapter
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = "$baseUrl/${chapter.url}".toHttpUrl().pathSegments.takeLast(2).joinToString("/")
        return "$baseUrl/manga/$slug"
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val details = response.parseAs<PageList>()
        return details.chapter.files.mapIndexed { i, filename -> Page(i, imageUrl = "$baseUrl/upload/chapters/${details.manga.id}/${details.chapter.chapter}/$filename") }
    }

    // Page
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
