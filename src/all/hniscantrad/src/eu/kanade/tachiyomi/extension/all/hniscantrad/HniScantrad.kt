package eu.kanade.tachiyomi.extension.all.hniscantrad

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

open class HniScantrad(override val lang: String) : HttpSource() {

    override val versionId = 2

    override val client: OkHttpClient = network.cloudflareClient

    override val baseUrl = "https://hni-scantrad.net"

    override val name = "HNI-Scantrad"

    override val supportsLatest = false

    private val json by injectLazy<Json>()

    private fun JsonElement?.getContent() = this?.jsonPrimitive?.content ?: ""

    private fun JsonElement?.getLastChLang() = this!!.jsonObject["last_chapter"]!!.jsonObject["language"].getContent()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/api/comics", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = json.parseToJsonElement(response.body.string()).jsonObject["comics"]!!.jsonArray
            .filter { jsonElement ->
                jsonElement.getLastChLang() == lang
            }
            .map { jsonElement ->
                jsonElement.jsonObject.let { obj ->
                    SManga.create().apply {
                        title = obj["title"].getContent()
                        thumbnail_url = obj["thumbnail"].getContent()
                        url = obj["slug"].getContent()
                    }
                }
            }
        return MangasPage(mangas, false)
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
        return GET("$baseUrl/api/search/$query", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/api/comics/${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return SManga.create().apply {
            json.parseToJsonElement(response.body.string()).jsonObject["comic"]!!.jsonObject.let { obj ->
                title = obj["title"].getContent()
                thumbnail_url = obj["thumbnail"].getContent()
                author = obj["author"].getContent()
            }
        }
    }

    // Chapters

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val comic = json.parseToJsonElement(response.body.string()).jsonObject["comic"]!!.jsonObject

        return comic["chapters"]!!.jsonArray.map { jsonElement ->
            jsonElement.jsonObject.let { obj ->
                SChapter.create().apply {
                    name = obj["full_title"].getContent()
                    date_upload = dateFormat.parse(obj["published_on"].getContent())?.time ?: 0
                    url = "${comic["slug"].getContent()}/" +
                        "${comic.getLastChLang()}/" +
                        (obj["volume"]?.jsonPrimitive?.content?.toIntOrNull()?.let { "vol/$it/" } ?: "") +
                        "ch/${obj["chapter"].getContent()}"
                }
            }
        }
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl/api/read/${chapter.url}")
    }

    override fun pageListParse(response: Response): List<Page> {
        return json.parseToJsonElement(response.body.string()).jsonObject["chapter"]!!.jsonObject["pages"]!!.jsonArray
            .mapIndexed { i, jsonElement -> Page(i, "", jsonElement.getContent()) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
