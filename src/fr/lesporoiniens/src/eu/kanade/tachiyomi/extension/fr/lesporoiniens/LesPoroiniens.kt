package eu.kanade.tachiyomi.extension.fr.lesporoiniens

import eu.kanade.tachiyomi.multisrc.scanr.PageData
import eu.kanade.tachiyomi.multisrc.scanr.ScanR
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class LesPoroiniens :
    ScanR(
        name = "Les Poroiniens",
        baseUrl = "https://lesporoiniens.org",
        lang = "fr",
    ) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())

            val encodedPath = response.request.url.encodedPath
            val isSeriesJson = encodedPath.startsWith("/data/series/") && (encodedPath.endsWith(".json") || response.request.url.fragment?.endsWith(".json") == true || encodedPath == "/data/series/")

            if ((response.code == 403 || response.code == 404) && isSeriesJson) {
                val bodyString = response.peekBody(1024 * 1024).string()

                if (bodyString.contains("Accès refusé", true) || bodyString.contains("Erreur 404", true)) {
                    response.close()
                    val dummyJson = """{"title":"DUMMY_ERROR_403","description":null,"artist":null,"author":null,"cover":null,"cover_low":null,"cover_hq":null,"tags":null,"release_status":null,"alternative_titles":null,"chapters":null}"""
                    return@addInterceptor response.newBuilder()
                        .code(200)
                        .message("OK")
                        .body(dummyJson.toResponseBody("application/json".toMediaType()))
                        .build()
                }
            }

            if (response.isSuccessful && isSeriesJson) {
                val bodyString = response.body.string()
                try {
                    val element = jsonInstance.parseToJsonElement(bodyString)
                    if (element is JsonObject) {
                        val chaptersElement = element["chapters"]
                        if (chaptersElement is JsonArray) {
                            val newChaptersMap = mutableMapOf<String, JsonElement>()
                            chaptersElement.forEachIndexed { index, chapter ->
                                newChaptersMap[(index + 1).toString()] = chapter
                            }
                            val newChaptersObject = JsonObject(newChaptersMap)
                            val newRootMap = element.toMutableMap()
                            newRootMap["chapters"] = newChaptersObject
                            val newRoot = JsonObject(newRootMap)

                            return@addInterceptor response.newBuilder()
                                .body(newRoot.toString().toResponseBody(response.body.contentType()))
                                .build()
                        }
                    }
                } catch (e: Exception) {
                    // Ignore parse errors, fallback to original body
                }
                return@addInterceptor response.newBuilder()
                    .body(bodyString.toResponseBody(response.body.contentType()))
                    .build()
            }

            response
        }
        .build()

    override fun searchMangaParse(response: Response): MangasPage {
        val page = super.searchMangaParse(response)
        return MangasPage(page.mangas.filter { it.title != "DUMMY_ERROR_403" }, page.hasNextPage)
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
            val pagesResponse = client.newCall(GET("$baseUrl/api/imgchest-chapter-pages?id=$chapterId", headers)).execute()
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
}
