package eu.kanade.tachiyomi.extension.all.deviantart

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class DeviantArt : HttpSource() {
    override val name = "DeviantArt"
    override val baseUrl = "https://deviantart.com"
    override val lang = "all"
    override val supportsLatest = false

    private val backendBaseUrl = "https://backend.deviantart.com"
    private fun backendBuilder() = backendBaseUrl.toHttpUrl().newBuilder()

    private val dateFormat by lazy {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
    }

    private fun parseDate(dateStr: String): Long {
        return try {
            dateFormat.parse(dateStr)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    override fun popularMangaRequest(page: Int): Request {
        throw UnsupportedOperationException(SEARCH_ERROR_MSG)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        throw UnsupportedOperationException(SEARCH_ERROR_MSG)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = backendBuilder()
            .addPathSegment("rss.xml")
            .addQueryParameter("q", query)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoupXml()

        val firstItem = document.selectFirst("item")
        requireNotNull(firstItem) { SEARCH_ERROR_MSG }

        val (username, folderId) = response.request.url.queryParameter("q")!!.split(" ")
            .find { it.startsWith("gallery:") }
            ?.substringAfter(":")
            ?.split("/")
            ?: throw IllegalArgumentException(SEARCH_ERROR_MSG)

        val newRequest = GET("$baseUrl/$username/gallery/$folderId", headers)
        val newResponse = client.newCall(newRequest).execute()
        val manga = mangaDetailsParse(newResponse)

        return MangasPage(listOf(manga), false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw UnsupportedOperationException()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = SManga.create().apply {
            // If manga is sub-gallery then use sub-gallery name, else use gallery name
            title = document.selectFirst("._2vMZg + ._2vMZg")?.text()?.substringBeforeLast(" ")
                ?: document.selectFirst(".kyUNb")!!.ownText()
            author = document.title().substringBefore(" ")
            description = document.selectFirst(".py0Gw._3urCH")?.wholeText()
            thumbnail_url = document.selectFirst("._1xcj5._1QdgI img")!!.attr("src")
        }
        manga.setUrlWithoutDomain(response.request.url.toString())
        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        val pathSegments = getMangaUrl(manga).toHttpUrl().pathSegments
        val username = pathSegments[0]
        val folderId = pathSegments[2]

        val url = backendBuilder()
            .addPathSegment("rss.xml")
            .addQueryParameter("q", "gallery:$username/$folderId")
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoupXml()
        val chapterList = parseToChapterList(document).toMutableList()
        var nextUrl = document.selectFirst("[rel=next]")?.attr("href")

        while (nextUrl != null) {
            val newRequest = GET(nextUrl, headers)
            val newResponse = client.newCall(newRequest).execute()
            val newDocument = newResponse.asJsoupXml()
            val newChapterList = parseToChapterList(newDocument)
            chapterList.addAll(newChapterList)

            nextUrl = newDocument.selectFirst("[rel=next]")?.attr("href")
        }

        return indexChapterList(chapterList.toList())
    }

    private fun parseToChapterList(document: Document): List<SChapter> {
        val items = document.select("item")
        return items.map {
            val chapter = SChapter.create()
            chapter.setUrlWithoutDomain(it.selectFirst("link")!!.text())
            chapter.apply {
                name = it.selectFirst("title")!!.text()
                date_upload = parseDate(it.selectFirst("pubDate")!!.text())
                scanlator = it.selectFirst("media|credit")!!.text()
            }
        }
    }

    private fun indexChapterList(chapterList: List<SChapter>): List<SChapter> {
        // DeviantArt allows users to arrange galleries arbitrarily so we will
        // primitively index the list by checking the first and last dates
        return if (chapterList.first().date_upload > chapterList.last().date_upload) {
            chapterList.mapIndexed { i, chapter ->
                chapter.apply { chapter_number = chapterList.size - i.toFloat() }
            }
        } else {
            chapterList.mapIndexed { i, chapter ->
                chapter.apply { chapter_number = i.toFloat() + 1 }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = backendBuilder()
            .addPathSegment("oembed")
            .addQueryParameter("url", baseUrl + chapter.url)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val imageUrl = Json.parseToJsonElement(response.body.string())
            .jsonObject["url"]!!
            .jsonPrimitive
            .content
        return listOf(Page(0, imageUrl = imageUrl))
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    private fun Response.asJsoupXml(): Document {
        return Jsoup.parse(body.string(), request.url.toString(), Parser.xmlParser())
    }

    companion object {
        const val SEARCH_ERROR_MSG = "No results found. Is your query in the format of gallery:{username}/{folderId}?"
    }
}
