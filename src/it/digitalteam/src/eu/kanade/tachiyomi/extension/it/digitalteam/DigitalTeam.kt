package eu.kanade.tachiyomi.extension.it.digitalteam

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class DigitalTeam : ParsedHttpSource() {

    override val name = "DigitalTeam"

    override val baseUrl = "https://dgtread.com"

    override val lang = "it"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/reader/series", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw Exception("La ricerca è momentaneamente disabilitata.")

    // LIST SELECTOR
    override fun popularMangaSelector() = "ul li.manga_block"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    // ELEMENT
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select(".manga_title a").text()
        thumbnail_url = element.select("img").attr("src")
        setUrlWithoutDomain(element.select(".manga_title a").first()!!.attr("href"))
    }

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not Used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not Used")

    //  NEXT SELECTOR
    //  Not needed
    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("#manga_left")

        author = infoElement.select(".info_name:contains(Autore)").next().text()
        artist = infoElement.select(".info_name:contains(Artista)").next().text()
        genre = infoElement.select(".info_name:contains(Genere)").next().text()
        status = parseStatus(infoElement.select(".info_name:contains(Status)").next().text())
        description = document.select("div.plot").text()
        thumbnail_url = infoElement.select(".cover img").attr("src")
    }

    private fun parseStatus(element: String): Int = when {
        element.lowercase(Locale.ROOT).contains("in corso") -> SManga.ONGOING
        element.lowercase(Locale.ROOT).contains("completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = ".chapter_list ul li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val urlElement = element.select("a").first()!!

        setUrlWithoutDomain(urlElement.attr("href"))
        name = urlElement.text()
        date_upload = element.select(".ch_bottom").first()?.text()
            ?.replace("Pubblicato il ", "")
            ?.let {
                try {
                    DATE_FORMAT_FULL.parse(it)?.time
                } catch (e: ParseException) {
                    DATE_FORMAT_SIMPLE.parse(it)?.time
                }
            } ?: 0
    }

    private fun getXhrPages(script_content: String, title: String, external: Boolean): String {
        val infoManga = script_content.substringAfter("m='").substringBefore("'")
        val infoChapter = script_content.substringAfter("ch='").substringBefore("'")
        val infoChSub = script_content.substringAfter("chs='").substringBefore("'")

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

        val request = POST("$baseUrl/reader/c_i", xhrHeaders, formBody)
        val response = client.newCall(request).execute()

        return response.asJsoup()
            .select("body")
            .text()
            .replace("\\", "")
            .removeSurrounding("\"")
    }

    override fun pageListParse(document: Document): List<Page> {
        val scriptContent = document.body().toString()
            .substringAfter("current_page=")
            .substringBefore(";")
        val title = document.select("title").first()!!.text()
        val external = document.select("script[src*='jq_rext.js']").isNotEmpty()

        val xhrPages = getXhrPages(scriptContent, title, external)
        val jsonResult = json.parseToJsonElement(xhrPages).jsonArray

        val imageDatas = jsonResult[0].jsonArray
        if (external) {
            val imageBases = jsonResult[1].jsonArray

            return imageDatas.zip(imageBases).mapIndexed { i, (imageData, imageBase) ->
                val imageUrl = imageBase.jsonPrimitive.content +
                    imageData.jsonObject["name"]!!.jsonPrimitive.content +
                    imageData.jsonObject["ex"]!!.jsonPrimitive.content

                Page(i, "", imageUrl)
            }
        }

        val imageSuffixes = jsonResult[1].jsonArray
        val imageBase = jsonResult[2].jsonPrimitive.content

        return imageDatas.zip(imageSuffixes).mapIndexed { i, (imageData, imageSuffix) ->
            val imageUrl = "$baseUrl/reader$imageBase" +
                imageData.jsonObject["name"]!!.jsonPrimitive.content +
                imageSuffix.jsonPrimitive.content +
                imageData.jsonObject["ex"]!!.jsonPrimitive.content

            Page(i, "", imageUrl)
        }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .add("Referer", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeader)
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; U; Android 4.1.1; en-gb; Build/KLP) " +
            "AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30"

        private val DATE_FORMAT_FULL = SimpleDateFormat("dd-MM-yyyy", Locale.ITALY)
        private val DATE_FORMAT_SIMPLE = SimpleDateFormat("H", Locale.ITALY)
    }
}
