package eu.kanade.tachiyomi.multisrc.scanr

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
import org.jsoup.nodes.Document
import kotlin.collections.iterator

abstract class ScanR(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
    private val useHighLowQualityCover: Boolean = false,
    private val slugSeparator: String = "-",
) : HttpSource() {

    companion object {
        private const val SERIES_DATA_SELECTOR = "#series-data-placeholder"
    }

    override val supportsLatest = false

    private val seriesDataCache = mutableMapOf<String, SeriesData>()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/data/config.json", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/data/config.json#$query"
        } else {
            "$baseUrl/data/config.json"
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val config = response.parseAs<ConfigResponse>()
        val mangaList = mutableListOf<SManga>()

        val fragment = response.request.url.fragment
        val searchQuery = fragment ?: ""

        for (fileName in config.localSeriesFiles) {
            val seriesData = fetchSeriesData(fileName)

            if (searchQuery.isBlank() || seriesData.title.contains(
                    searchQuery,
                    ignoreCase = true,
                )
            ) {
                mangaList.add(seriesData.toSManga(useHighLowQualityCover, slugSeparator))
            }
        }

        return MangasPage(mangaList, false)
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val jsonData = document.selectFirst(SERIES_DATA_SELECTOR)!!.html()

        val seriesData = jsonData.parseAs<SeriesData>()
        return seriesData.toDetailedSManga(useHighLowQualityCover, slugSeparator)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapterNumber = document.location().substringAfterLast("/")
        val chapterId = extractChapterId(document, chapterNumber)
        return fetchChapterPages(chapterId)
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val jsonData = document.selectFirst(SERIES_DATA_SELECTOR)!!.html()

        val seriesData = jsonData.parseAs<SeriesData>()
        return buildChapterList(seriesData)
    }

    private fun fetchSeriesData(fileName: String): SeriesData {
        val cachedData = seriesDataCache[fileName]
        if (cachedData != null) {
            return cachedData
        }

        val fileUrl = "$baseUrl/data/series/$fileName"
        val response = client.newCall(GET(fileUrl, headers)).execute()
        val seriesData = response.parseAs<SeriesData>()

        seriesDataCache[fileName] = seriesData

        return seriesData
    }

    private fun extractChapterId(document: Document, chapterNumber: String): String {
        val jsonData = document.selectFirst("#reader-data-placeholder")!!.html()

        val readerData = jsonData.parseAs<ReaderData>()
        return readerData.series.chapters
            ?.get(chapterNumber)
            ?.groups
            ?.values
            ?.firstOrNull()
            ?.substringAfterLast("/")
            ?: throw NoSuchElementException("Chapter data not found for chapter $chapterNumber")
    }

    private fun buildChapterList(seriesData: SeriesData): List<SChapter> {
        val chapters = seriesData.chapters ?: return emptyList()
        val chapterList = mutableListOf<SChapter>()
        val multipleChapters = chapters.size > 1

        for ((chapterNumber, chapterData) in chapters) {
            if (chapterData.licencied) continue

            val title = chapterData.title ?: ""
            val volumeNumber = chapterData.volume ?: ""

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
                url = "/${toSlug(seriesData.title)}/$chapterNumber"
                chapter_number = chapterNumber.toFloatOrNull() ?: -1f
                date_upload = chapterData.lastUpdated * 1000L
            }
            chapterList.add(chapter)
        }

        return chapterList.sortedByDescending { it.chapter_number }
    }

    private fun fetchChapterPages(chapterId: String): List<Page> {
        val pagesResponse =
            client.newCall(GET("$baseUrl/api/imgchest-chapter-pages?id=$chapterId", headers))
                .execute()
        val pages = pagesResponse.parseAs<List<PageData>>()
        return pages.mapIndexed { index, pageData ->
            Page(index, imageUrl = pageData.link)
        }
    }
}
