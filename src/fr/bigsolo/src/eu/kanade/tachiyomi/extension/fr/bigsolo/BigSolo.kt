package eu.kanade.tachiyomi.extension.fr.bigsolo

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response
import java.net.URI

class BigSolo : HttpSource() {

    companion object {
        private const val SERIES_DATA_SELECTOR = "#series-data-placeholder"
    }

    override val name = "Big Solo"
    override val baseUrl = "https://bigsolo.org"
    override val lang = "fr"
    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/data/series", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val series = response.parseAs<SeriesResponse>()
        val mangaList = mutableListOf<SManga>()

        for (serie in series.reco) {
            mangaList.add(serie.toDetailedSManga())
        }

        return MangasPage(mangaList, false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/data/series", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }

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

        for (serie in allSeries) {
            if (searchQuery.startsWith("SLUG:") && serie.slug == searchQuery.removePrefix("SLUG:")) {
                mangaList.add(serie.toDetailedSManga())
                break
            }

            if ((
                searchQuery.isBlank() ||
                    serie.title.contains(searchQuery, ignoreCase = true) ||
                    serie.alternativeTitles.any { it.contains(searchQuery, ignoreCase = true) } ||
                    serie.jaTitle.contains(searchQuery, ignoreCase = true)
                ) && !searchQuery.startsWith("SLUG:")
            ) {
                mangaList.add(serie.toDetailedSManga())
            }
        }

        return MangasPage(mangaList, false)
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val jsonData = document.selectFirst(SERIES_DATA_SELECTOR)!!.html()

        val serie = jsonData.parseAs<Serie>()
        return serie.toDetailedSManga()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val splitedPath = URI(document.baseUri()).path.split("/")
        val slug = splitedPath[1]
        val chapterId = splitedPath[2]
        return fetchChapterPages(slug, chapterId)
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val jsonData = document.selectFirst(SERIES_DATA_SELECTOR)!!.html()
        val seriesData = jsonData.parseAs<Serie>()
        val slug = URI(document.baseUri()).path.split("/")[1]
        return buildChapterList(seriesData, slug)
    }

    private fun buildChapterList(serie: Serie, slug: String): List<SChapter> {
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
                setUrlWithoutDomain("$baseUrl/$slug/$chapterNumber")
                chapter_number = chapterNumber.toFloatOrNull() ?: -1f
                scanlator = chapterData.teams.joinToString(" & ")
                date_upload = chapterData.timestamp * 1000L
            }
            chapterList.add(chapter)
        }

        return chapterList.sortedByDescending { it.chapter_number }
    }

    private fun fetchChapterPages(slug: String, chapterId: String): List<Page> {
        val pagesResponse =
            client.newCall(GET("$baseUrl/data/series/$slug/$chapterId", headers))
                .execute()
        val chapterDetails = pagesResponse.parseAs<ChapterDetails>()
        return chapterDetails.images.mapIndexed { index, pageData ->
            Page(index, imageUrl = pageData)
        }
    }
}
