package eu.kanade.tachiyomi.extension.all.comicgrowl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ComicGrowl(
    override val lang: String = "all",
    override val baseUrl: String = "https://comic-growl.com",
    override val name: String = "コミックグロウル",
    override val supportsLatest: Boolean = false,
) : ParsedHttpSource() {

    private val json: Json by injectLazy()

    companion object {
        private const val PUBLISHER = "BUSHIROAD WORKS"

        private val imageUrlRegex by lazy { Regex("^.*?webp") }

        /**
         * Get cover image url from [element]
         */
        private fun getImageFromElement(element: Element): String {
            val match = imageUrlRegex.find(element.selectFirst("source")!!.attr("data-srcset"))
                ?: throw Exception("Cover image url not found")
            return "https:" + match.value
        }

        private val DATE_PARSER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.JAPAN) }

        /**
         * Parse date from string like "3月31日" to UNIX Epoch time.
         */
        private fun String.toDate(): Long {
            return try {
                DATE_PARSER.parse(this)?.time ?: 0L
            } catch (_: ParseException) {
                0L
            }
        }
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/ranking/manga")

    override fun popularMangaNextPageSelector() = null

    override fun popularMangaSelector() = ".ranking-item"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            url = element.selectFirst("a")!!.attr("href")
            title = element.selectFirst(".title-text")!!.text()
            author = element.selectFirst(".author-link")!!.text()
            thumbnail_url = getImageFromElement(element)
        }
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(manga.url)

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst(".series-h-info")!!
        val updateDateElement = infoElement.selectFirst("series-h-tag-label")
        return SManga.create().apply {
            title = infoElement.select("h1 > span")[1]!!.text()
            author = infoElement.selectFirst(".series-h-credit-user-item > .article-text")!!.text() // TODO: get 脚本/漫画
            description = infoElement.selectFirst(".series-h-credit-info-text-text > div > p > span > span")!!.text()
            thumbnail_url = getImageFromElement(document.getElementsByClass("series-h-img").first()!!)
            status = if (updateDateElement != null) SManga.ONGOING else SManga.COMPLETED
        }
    }

    override fun chapterListRequest(manga: SManga) = GET(manga.url) // TODO: get chapters from `/list`

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).mapIndexed { index, element ->
            chapterFromElement(element).apply {
                chapter_number = index.toFloat()
                if (url.isEmpty()) { // Login required
                    throw Exception("Some chapters don't have url, login and refresh again")
                }
            }
        }
    }

    override fun chapterListSelector() = ".article-ep-list-item-img-link"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            url = element.attr("data-href")
            name = element.selectFirst(".series-ep-list-item-h-text")!!.text()
            date_upload = element.selectFirst(".series-ep-list-date-time")!!.attr("datetime").toDate()
            scanlator = PUBLISHER
        }
    }

    override fun pageListRequest(chapter: SChapter) = GET(chapter.url)

    override fun pageListParse(document: Document): List<Page> {
        val pageList = mutableListOf<Page>()

        val viewer = document.selectFirst("#comici-viewer")!!
        val comiciViewerId = viewer.attr("comici-viewer-id")
        val memberJwt = viewer.attr("data-member-jwt")
        val requestUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("book")
            .addPathSegments("contentsInfo")
            .addQueryParameter("comici-viewer-id", comiciViewerId)
            .addQueryParameter("user-id", memberJwt)
            .addQueryParameter("page-from", "0")

        // Initial request to get total pages
        val initialRequest = GET(requestUrl.addQueryParameter("page-to", "1").build())
        client.newCall(initialRequest).execute().use { initialResponse ->
            if (initialResponse.code != 200) {
                throw Exception("Failed to get page list")
            }
            val totalPages =
                json.parseToJsonElement(initialResponse.body.string()).jsonObject["totalPages"]!!.jsonPrimitive.content
            // Get all pages
            val getAllPagesRequest = GET(requestUrl.setQueryParameter("page-to", totalPages).build())
            client.newCall(getAllPagesRequest).execute().use {
                if (it.code != 200) {
                    throw Exception("Failed to get page list")
                }
                val result = json.parseToJsonElement(it.body.string())
                val resultJson = result.jsonObject["result"]!!.jsonArray
                resultJson.forEach { resultJsonElement ->
                    val jsonObject = resultJsonElement.jsonObject
                    pageList.add(
                        Page(
                            index = jsonObject["sort"]!!.jsonPrimitive.int,
                            imageUrl = jsonObject["imageUrl"]!!.jsonPrimitive.content,
                        ),
                    )
                }
            }
        }
        return pageList
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl)
            .build()
        return GET(page.imageUrl!!, newHeaders)
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    override fun searchMangaFromElement(element: Element): SManga {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesNextPageSelector(): String? {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        TODO("Not yet implemented")
    }

    override fun latestUpdatesSelector(): String {
        TODO("Not yet implemented")
    }

    override fun searchMangaNextPageSelector(): String? {
        TODO("Not yet implemented")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("Not yet implemented")
    }

    override fun searchMangaSelector(): String {
        TODO("Not yet implemented")
    }
}

// // TODO: get manga status
// // TODO: filter by status
// // TODO: change cdnUrl as a array(upstream)
// class ComicGrowl : GigaViewer(
//    "コミックグロウル",
//    "https://comic-growl.com",
//    "all",
//    "https://cdn-img.comic-growl.com/public/page",
// ) {
//
//    override val publisher = "BUSHIROAD WORKS"
//
//    override val chapterListMode = CHAPTER_LIST_LOCKED
//
//    override val supportsLatest: Boolean = true
//
//    override val client: OkHttpClient =
//        super.client.newBuilder().addInterceptor(::imageIntercept).build()
//
//    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)
//
//    // Show only ongoing works
//    override fun popularMangaSelector(): String = "ul[class=\"lineup-list ongoing\"] > li > div > a"
//
//    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
//        title = element.select("h5").text()
//        thumbnail_url = element.select("div > img").attr("data-src")
//        setUrlWithoutDomain(element.attr("href"))
//    }
//
//    override fun latestUpdatesSelector() =
//        "div[class=\"update latest\"] > div.card-board > " + "div[class~=card]:not([class~=ad]) > div > a"
//
//    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
//        title = element.select("div.data h3").text()
//        thumbnail_url = element.select("div.thumb-container img").attr("data-src")
//        setUrlWithoutDomain(element.attr("href"))
//    }
//
//    override fun getCollections(): List<Collection> = listOf(
//        Collection("連載作品", ""),
//    )
//
//    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
//        if (query.isNotEmpty()) {
//            val url = "$baseUrl/search".toHttpUrl().newBuilder().addQueryParameter("q", query)
//
//            return GET(url.build(), headers)
//        }
//        return GET(baseUrl, headers) // Currently just get all ongoing works
//    }
// }
