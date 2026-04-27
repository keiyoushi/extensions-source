package eu.kanade.tachiyomi.extension.it.digitalteam

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class DigitalTeam : HttpSource() {

    override val name = "DigitalTeam"

    override val baseUrl = "https://dgtread.com"

    override val lang = "it"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/reader/series", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select("ul li.manga_block").map { element ->
            SManga.create().apply {
                title = element.select(".manga_title a").text()
                thumbnail_url = element.select("img").attr("abs:src")
                setUrlWithoutDomain(element.select(".manga_title a").first()!!.absUrl("href"))
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("La ricerca è momentaneamente disabilitata.")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.select("#manga_left")
        return SManga.create().apply {
            author = infoElement.select(".info_name:contains(Autore)").next().text()
            artist = infoElement.select(".info_name:contains(Artista)").next().text()
            genre = infoElement.select(".info_name:contains(Genere)").next().text()
            status = parseStatus(infoElement.select(".info_name:contains(Status)").next().text())
            description = document.select("div.plot").text()
            thumbnail_url = infoElement.select(".cover img").attr("abs:src")
        }
    }

    private fun parseStatus(element: String): Int = when {
        element.lowercase(Locale.ROOT).contains("in corso") -> SManga.ONGOING
        element.lowercase(Locale.ROOT).contains("completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select(".chapter_list ul li").map { element ->
        SChapter.create().apply {
            val urlElement = element.select("a").first()!!
            setUrlWithoutDomain(urlElement.absUrl("href"))
            name = urlElement.text()
            date_upload = element.select(".ch_bottom").first()?.text()
                ?.replace("Pubblicato il ", "")
                ?.let { dateStr ->
                    DATE_FORMAT_FULL.tryParse(dateStr).takeIf { it != 0L }
                        ?: DATE_FORMAT_SIMPLE.tryParse(dateStr)
                } ?: 0L
        }
    }

    private fun getXhrPages(scriptContent: String, title: String, external: Boolean): JsonArray {
        val infoManga = scriptContent.substringAfter("m='").substringBefore("'")
        val infoChapter = scriptContent.substringAfter("ch='").substringBefore("'")
        val infoChSub = scriptContent.substringAfter("chs='").substringBefore("'")

        val formBodyBuilder = FormBody.Builder()
            .add("info[manga]", infoManga)
            .add("info[chapter]", infoChapter)
            .add("info[ch_sub]", infoChSub)
            .add("info[title]", title)

        if (external) {
            formBodyBuilder.add("info[external]", "1")
        }

        val formBody = formBodyBuilder.build()

        val xhrHeaders = headersBuilder()
            .add("Content-Length", formBody.contentLength().toString())
            .add("Content-Type", formBody.contentType().toString())
            .build()

        val responseText = client.newCall(POST("$baseUrl/reader/c_i", xhrHeaders, formBody))
            .execute()
            .asJsoup()
            .select("body")
            .text()
            .replace("\\", "")
            .removeSurrounding("\"")

        return responseText.parseAs()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val scriptContent = document.body().toString()
            .substringAfter("current_page=")
            .substringBefore(";")
        val title = document.select("title").first()!!.text()
        val external = document.select("script[src*='jq_rext.js']").isNotEmpty()

        val jsonResult = getXhrPages(scriptContent, title, external)
        val imageDatas = jsonResult[0].jsonArray

        if (external) {
            val imageBases = jsonResult[1].jsonArray
            return imageDatas.zip(imageBases).mapIndexed { i, (imageData, imageBase) ->
                val imageUrl = imageBase.jsonPrimitive.content +
                    imageData.jsonObject["name"]!!.jsonPrimitive.content +
                    imageData.jsonObject["ex"]!!.jsonPrimitive.content
                Page(i, imageUrl = imageUrl)
            }
        }

        val imageSuffixes = jsonResult[1].jsonArray
        val imageBase = jsonResult[2].jsonPrimitive.content

        return imageDatas.zip(imageSuffixes).mapIndexed { i, (imageData, imageSuffix) ->
            val imageUrl = "$baseUrl/reader$imageBase" +
                imageData.jsonObject["name"]!!.jsonPrimitive.content +
                imageSuffix.jsonPrimitive.content +
                imageData.jsonObject["ex"]!!.jsonPrimitive.content
            Page(i, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = Headers.Builder()
            .add("Referer", baseUrl)
            .build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    companion object {
        private val DATE_FORMAT_FULL = SimpleDateFormat("dd-MM-yyyy", Locale.ITALY)
        private val DATE_FORMAT_SIMPLE = SimpleDateFormat("H", Locale.ITALY)
    }
}
