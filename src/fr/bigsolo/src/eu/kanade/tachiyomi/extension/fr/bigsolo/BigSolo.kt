package eu.kanade.tachiyomi.extension.fr.bigsolo

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response
import java.net.URI

class BigSolo : HttpSource() {

    override val name = "BigSolo"
    override val baseUrl = "https://bigsolo.org"
    override val lang = "fr"
    override val supportsLatest = true
    override val id = 4410528266393104437

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/data/series", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val series = response.parseAs<SeriesResponse>()
        val mangaList = mutableListOf<SManga>()

        for (serie in series.reco) {
            mangaList.add(serie.toDetailedSManga())
        }

        return MangasPage(mangaList, false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/data/series", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/data/series#$query"
        } else {
            "$baseUrl/data/series"
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val series = response.parseAs<SeriesResponse>()
        val allSeries = (series.series + series.os).sortedByDescending { it.lastChapter?.timestamp }
        val mangaList = mutableListOf<SManga>()

        val fragment = response.request.url.fragment
        val searchQuery = fragment ?: ""

        if (searchQuery.startsWith("SLUG:")) {
            val serie = allSeries.find { it.slug == searchQuery.removePrefix("SLUG:") }
            if (serie != null) {
                mangaList.add(serie.toDetailedSManga())
            }
            return MangasPage(mangaList, false)
        }

        for (serie in allSeries) {
            if (searchQuery.isBlank() ||
                serie.title.contains(searchQuery, ignoreCase = true) ||
                serie.alternativeTitles.any { it.contains(searchQuery, ignoreCase = true) } ||
                serie.jaTitle.contains(searchQuery, ignoreCase = true)
            ) {
                mangaList.add(serie.toDetailedSManga())
            }
        }

        return MangasPage(mangaList, false)
    }

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val splitedPath = URI(manga.url).path.split("/")
        val slug = splitedPath[1]
        return GET("$baseUrl/data/series/$slug", headers)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val serie = response.parseAs<Serie>()
        return serie.toDetailedSManga()
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val splitedPath = URI(chapter.url).path.split("/")
        val slug = splitedPath[1]
        val chapterId = splitedPath[2]
        return GET("$baseUrl/data/series/$slug/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterDetails = response.parseAs<ChapterDetails>()
        return chapterDetails.images.mapIndexed { index, pageData ->
            Page(index, imageUrl = pageData)
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val slug = URI(manga.url).path.split("/")[1]
        return GET("$baseUrl/data/series/$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesData = response.parseAs<Serie>()
        return buildChapterList(seriesData)
    }
    private fun buildChapterList(serie: Serie): List<SChapter> {
        val chapters = serie.chapters
        val chapterList = mutableListOf<SChapter>()
        val multipleChapters = chapters.size > 1

        for ((chapterNumber, chapterData) in chapters) {
            if (chapterData.licencied) continue

            val title = chapterData.title
            val volumeNumber = chapterData.volume

            val baseName = if (multipleChapters) {
                buildString {
                    if (volumeNumber.isNotBlank()) append("Vol. $volumeNumber ")
                    append("Ch. $chapterNumber")
                    if (title.isNotBlank()) append(" – $title")
                }
            } else {
                if (title.isNotBlank()) "One Shot – $title" else "One Shot"
            }

            val chapter = SChapter.create().apply {
                name = baseName
                setUrlWithoutDomain("$baseUrl/${serie.slug}/$chapterNumber")
                chapter_number = chapterNumber.toFloatOrNull() ?: -1f
                scanlator = chapterData.teams.joinToString(" & ")
                date_upload = chapterData.timestamp * 1000L
            }
            chapterList.add(chapter)
        }

        return chapterList.sortedByDescending { it.chapter_number }
    }

    // Unsupported Stuff
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
