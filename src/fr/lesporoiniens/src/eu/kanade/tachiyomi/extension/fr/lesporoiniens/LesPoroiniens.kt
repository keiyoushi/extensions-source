package eu.kanade.tachiyomi.extension.fr.lesporoiniens

import eu.kanade.tachiyomi.multisrc.scanr.ConfigResponse
import eu.kanade.tachiyomi.multisrc.scanr.PageData
import eu.kanade.tachiyomi.multisrc.scanr.ScanR
import eu.kanade.tachiyomi.multisrc.scanr.SeriesData
import eu.kanade.tachiyomi.multisrc.scanr.toSManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup

class LesPoroiniens :
    ScanR(
        name = "Les Poroiniens",
        baseUrl = "https://lesporoiniens.org",
        lang = "fr",
    ) {

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

    override fun mangaDetailsParse(response: Response): SManga = super.mangaDetailsParse(transformSeriesDataResponse(response))

    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(transformSeriesDataResponse(response))

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
}
