package eu.kanade.tachiyomi.extension.fr.bigsolo

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BigSolo : ParsedHttpSource() {

    companion object {
        private const val TAG = "BigSolo"
        private const val CONFIG_ENDPOINT = "/data/config.json"
        private const val SERIES_DATA_ENDPOINT = "/data/series/"
        private const val CHAPTER_PAGES_API = "/api/imgchest-chapter-pages"
        private const val SERIES_DATA_SELECTOR = "#series-data-placeholder"
        private const val READER_DATA_SELECTOR = "#reader-data-placeholder"
    }

    override val name = "BigSolo"
    override val baseUrl = "https://bigsolo.org"
    override val lang = "fr"
    override val supportsLatest = false

    private var currentSearchQuery = ""

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "fr-FR")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36",
        )

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        currentSearchQuery = ""
        return GET("$baseUrl$CONFIG_ENDPOINT", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun popularMangaSelector() = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        currentSearchQuery = query
        return GET("$baseUrl$CONFIG_ENDPOINT", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val config = parseConfigResponse(response.body.string())
        val mangaList = mutableListOf<SManga>()

        for (fileName in config.localSeriesFiles) {
            try {
                val seriesData = fetchSeriesData(fileName)

                // Filter by title if a query is provided
                if (currentSearchQuery.isBlank() || seriesData.title.contains(
                        currentSearchQuery,
                        ignoreCase = true,
                    )
                ) {
                    mangaList.add(seriesData.toSManga())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load series data for $fileName", e)
                continue
            }
        }

        return MangasPage(mangaList, false)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun searchMangaSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun searchMangaNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val jsonData = document.selectFirst(SERIES_DATA_SELECTOR)?.html()
            ?: throw IllegalStateException("Series data not found")

        return parseSeriesData(jsonData).toDetailedSManga()
    }

    override fun pageListParse(document: Document): List<Page> {
        val chapterNumber = document.location().substringAfterLast("/")
        val chapterId = extractChapterId(document, chapterNumber)
        return fetchChapterPages(chapterId)
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    // Chapters
    override fun chapterListSelector() = SERIES_DATA_SELECTOR

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val jsonData = document.selectFirst(SERIES_DATA_SELECTOR)?.html()
            ?: throw IllegalStateException("Series data not found")

        val seriesData = parseSeriesData(jsonData)
        return buildChapterList(seriesData)
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException()
    }

    // Helper methods for better code organization
    private fun parseConfigResponse(json: String): ConfigResponse {
        return try {
            JSONObject(json).toConfigResponse()
        } catch (e: org.json.JSONException) {
            throw IllegalStateException("Failed to parse config JSON: ${e.message}", e)
        }
    }

    private fun fetchSeriesData(fileName: String): SeriesData {
        val fileUrl = "$baseUrl$SERIES_DATA_ENDPOINT$fileName"
        val response = try {
            client.newCall(GET(fileUrl, headers)).execute()
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Failed to fetch series data", e)
        }

        return parseSeriesData(response.body.string())
    }

    private fun parseSeriesData(json: String): SeriesData {
        return try {
            JSONObject(json).toSeriesData()
        } catch (e: org.json.JSONException) {
            throw IllegalStateException("Failed to parse series JSON: ${e.message}", e)
        }
    }

    private fun extractChapterId(document: Document, chapterNumber: String): String {
        val jsonData = document.selectFirst(READER_DATA_SELECTOR)?.html()
            ?: throw IllegalStateException("Reader data not found")

        val seriesJson = try {
            JSONObject(jsonData).optJSONObject("series")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse reader data JSON", e)
        }

        val currentChapter = seriesJson?.optJSONObject("chapters")?.optJSONObject(chapterNumber)
        val groups = currentChapter?.optJSONObject("groups")

        return if (groups != null) {
            val keys = groups.keys()
            if (keys.hasNext()) {
                val firstGroupKey = keys.next()
                val firstGroup = groups.optString(firstGroupKey)
                firstGroup.substringAfterLast("/")
            } else {
                ""
            }
        } else {
            ""
        }
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
                date_upload = (chapterData.lastUpdated ?: 0) * 1000L
            }
            chapterList.add(chapter)
        }

        return chapterList.sortedByDescending { it.chapter_number }
    }

    private fun fetchChapterPages(chapterId: String): List<Page> {
        val pagesResponse = try {
            client.newCall(GET("$baseUrl$CHAPTER_PAGES_API?id=$chapterId", headers)).execute()
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Failed to fetch chapter pages", e)
        }

        val pagesJson = try {
            org.json.JSONArray(pagesResponse.body.string())
        } catch (e: org.json.JSONException) {
            throw IllegalStateException("Failed to parse chapter pages JSON", e)
        }

        return List(pagesJson.length()) { index ->
            Page(
                index,
                imageUrl = pagesJson.getJSONObject(index).optString("link"),
            )
        }
    }
}
