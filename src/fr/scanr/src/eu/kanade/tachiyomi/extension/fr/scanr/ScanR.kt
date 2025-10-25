package eu.kanade.tachiyomi.extension.fr.scanr

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

class ScanR : HttpSource() {

    override val name = "ScanR"
    override val baseUrl = "https://teamscanr.fr"
    val cdnUrl = "https://cdn.teamscanr.fr"
    override val lang = "fr"
    override val supportsLatest = false
    private val seriesDataCache = mutableMapOf<String, Serie>()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$cdnUrl/index.json", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$cdnUrl/index.json#$query"
        } else {
            "$cdnUrl/index.json"
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val series = response.parseAs<Map<String, String>>()
        val mangaList = mutableListOf<SManga>()

        val fragment = response.request.url.fragment
        val searchQuery = fragment ?: ""

        for ((slug, filename) in series) {
            val serie = fetchSeriesData(filename)

            if (searchQuery.startsWith("SLUG:") && slug == searchQuery.removePrefix("SLUG:")) {
                mangaList.add(serie.toDetailedSManga())
                continue
            }

            if (searchQuery.isBlank() || serie.title.contains(searchQuery, ignoreCase = true)) {
                mangaList.add(serie.toDetailedSManga())
            }
        }

        return MangasPage(mangaList, false)
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val splitedPath = URI(document.baseUri()).path.split("/")
        val slug = splitedPath[1]
        val serie = getSerieFromSlug(slug)
        return serie.toDetailedSManga()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val splitedPath = URI(chapter.url).path.split("/")
        val slug = splitedPath[1]
        val chapterId = splitedPath[2]
        val serie = getSerieFromSlug(slug)
        val chapterDetails = serie.chapters[chapterId.replace("-", ".")]
        val cubariProxy = chapterDetails!!.groups.getValue(chapterDetails.groups.keys.first())
        return GET("https://cubari.moe$cubariProxy", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val images = response.parseAs<List<String>>()
        return images.mapIndexed { index, pageData ->
            Page(index, imageUrl = pageData)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = URI(response.asJsoup().baseUri()).path.split("/")[1]
        val seriesData = getSerieFromSlug(slug, true)
        return buildChapterList(seriesData)
    }

    private fun buildChapterList(serie: Serie): List<SChapter> {
        val chapters = serie.chapters
        val chapterList = mutableListOf<SChapter>()

        for ((chapterNumber, chapterData) in chapters) {
            val title = chapterData.title
            val volumeNumber = chapterData.volume

            val baseName = if (!serie.os) {
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
                setUrlWithoutDomain("$baseUrl/${serie.slug}/${chapterNumber.replace(".","-")}")
                chapter_number = chapterNumber.toFloatOrNull() ?: -1f
                scanlator = chapterData.groups.keys.first()
                date_upload = chapterData.lastUpdated.toLong() * 1000L
            }
            chapterList.add(chapter)
        }

        return chapterList.sortedByDescending { it.chapter_number }
    }

    private fun fetchSeriesData(filename: String, forceReload: Boolean = false): Serie {
        val cachedSerie = seriesDataCache[filename]
        if (!forceReload && cachedSerie != null) {
            return cachedSerie
        }

        val response = client.newCall(GET("$cdnUrl/$filename", headers)).execute()
        val seriesData = response.parseAs<Serie>()

        seriesDataCache[filename] = seriesData
        return seriesData
    }

    private fun getSerieFromSlug(slug: String, forceReload: Boolean = false): Serie {
        val serieList =
            client.newCall(GET("$cdnUrl/index.json", headers))
                .execute().parseAs<Map<String, String>>()

        return fetchSeriesData(serieList[slug] ?: "", forceReload)
    }
}
