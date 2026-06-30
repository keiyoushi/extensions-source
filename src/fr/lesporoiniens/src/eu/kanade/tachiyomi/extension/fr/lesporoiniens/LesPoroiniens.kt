package eu.kanade.tachiyomi.extension.fr.lesporoiniens

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup

@Source
abstract class LesPoroiniens : HttpSource() {

    private val useHighLowQualityCover: Boolean = false

    private val slugSeparator: String = "-"

    companion object {
        private const val SERIES_DATA_SELECTOR = "#series-data-placeholder"
    }

    override val supportsLatest = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 403 && response.request.url.encodedPath.startsWith("/data/series/")) {
                val bodyString = response.peekBody(1024 * 1024).string()
                if (bodyString.contains("Accès refusé", true) || bodyString.contains("Erreur 404", true)) {
                    response.close()
                    return@addInterceptor response.newBuilder()
                        .code(404)
                        .message("Not Found")
                        .build()
                }
            }
            response
        }
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/data/config.json", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

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
        val searchQuery = response.request.url.fragment ?: ""

        for (fileName in config.localSeriesFiles) {
            try {
                val seriesResponse = client.newCall(
                    GET("$baseUrl/data/series/$fileName", headers),
                ).execute()
                if (!seriesResponse.isSuccessful) {
                    seriesResponse.close()
                    continue
                }
                val seriesData = transformChaptersJson(seriesResponse.body.string())
                    .parseAs<SeriesData>()

                if (searchQuery.isBlank() || seriesData.title.contains(searchQuery, ignoreCase = true)) {
                    mangaList.add(seriesData.toSManga(slugSeparator = "-"))
                }
            } catch (_: Exception) {
                continue
            }
        }

        return MangasPage(mangaList, false)
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = transformSeriesDataResponse(response).asJsoup()
        val jsonData = document.selectFirst(SERIES_DATA_SELECTOR)!!.html()

        val seriesData = jsonData.parseAs<SeriesData>()
        return seriesData.toDetailedSManga(useHighLowQualityCover, slugSeparator)
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = transformSeriesDataResponse(response).asJsoup()
        val jsonData = document.selectFirst(SERIES_DATA_SELECTOR)!!.html()

        val seriesData = jsonData.parseAs<SeriesData>()
        return buildChapterList(seriesData)
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

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapterNumber = response.request.url.pathSegments.last { it.isNotEmpty() }
        val jsonData = document.selectFirst("#reader-data-placeholder")!!.html()

        val readerData = jsonData.parseAs<LocalReaderData>()
        val chapterData = readerData.series.chapters?.get(chapterNumber)
            ?: throw NoSuchElementException("Chapter data not found for chapter $chapterNumber")

        val chapterUrl = chapterData.groups?.values?.firstOrNull()
            ?: throw NoSuchElementException("Chapter URL not found for chapter $chapterNumber")

        if (chapterUrl.contains("imgchest")) {
            val chapterId = chapterUrl.substringAfterLast("/")
            val pagesResponse = client.newCall(
                GET("$baseUrl/api/imgchest-chapter-pages?id=$chapterId", headers),
            ).execute()
            val pages = pagesResponse.parseAs<List<PageData>>()
            return pages.mapIndexed { index, pageData ->
                Page(index, imageUrl = pageData.link)
            }
        } else {
            val pagesResponse = client.newCall(GET("$baseUrl$chapterUrl", headers)).execute()
            val images = pagesResponse.parseAs<List<String>>()
            return images.mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
        }
    }

    private fun transformSeriesDataResponse(response: Response): Response {
        val contentType = response.body.contentType()
        val body = response.body.string()
        val document = Jsoup.parse(body, response.request.url.toString())
        val element = document.selectFirst("#series-data-placeholder")

        if (element != null) {
            val original = element.html()
            val transformed = transformChaptersJson(original)
            if (transformed != original) {
                element.html(transformed)
                return response.newBuilder()
                    .body(document.outerHtml().toResponseBody(contentType))
                    .build()
            }
        }

        return response.newBuilder()
            .body(body.toResponseBody(contentType))
            .build()
    }

    private fun transformChaptersJson(jsonString: String): String {
        val element = try {
            jsonInstance.parseToJsonElement(jsonString)
        } catch (_: Exception) {
            return jsonString
        }

        if (element is JsonObject && element["chapters"] is JsonArray) {
            val chapters = element["chapters"] as JsonArray
            val chaptersMap = JsonObject(
                chapters.mapIndexed { index, item ->
                    (index + 1).toString() to item
                }.toMap(),
            )
            return JsonObject(
                element.toMutableMap().also { it["chapters"] = chaptersMap },
            ).toString()
        }

        return jsonString
    }

    // Images
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
