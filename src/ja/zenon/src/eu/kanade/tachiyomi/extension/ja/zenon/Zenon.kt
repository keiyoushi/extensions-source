package eu.kanade.tachiyomi.extension.ja.zenon

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Zenon : GigaViewer(
    "Zenon",
    "https://comic-zenon.com",
    "ja",
    "https://cdn-img.comic-zenon.com/public/page",
    isPaginated = false,
) {

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::imageIntercept)
        .build()

    override val publisher: String = "ゼノン編集部"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div.kyujosho-series > a, ul.panels li.panel a:has(h4)"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h4")?.text() ?: element.selectFirst("p")!!.text()
        thumbnail_url = element.selectFirst("img")?.attr("data-src")
        setUrlWithoutDomain(getCanonicalUrl(element.attr("href"), thumbnail_url))
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangasPage = super.popularMangaParse(response)
        val distinctMangas = mangasPage.mangas.distinctBy { it.url }
        return MangasPage(distinctMangas, mangasPage.hasNextPage)
    }

    override fun searchMangaSelector(): String = "ul.series-list > li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".series-title")!!.text()
        thumbnail_url = element.selectFirst("img")?.attr("src")
        setUrlWithoutDomain(getCanonicalUrl(element.selectFirst("a")!!.attr("href"), thumbnail_url))
    }

    private fun getCanonicalUrl(href: String, thumbnailUrl: String?): String {
        return thumbnailUrl?.let { Regex("""series-thumbnail/(\d+)""").find(it) }
            ?.let { "/series/${it.groupValues[1]}" }
            ?: href
    }

    private fun getSeriesId(url: String): String? = Regex("""/series/(\d+)""").find(url)?.groupValues?.get(1)

    override fun mangaDetailsRequest(manga: SManga): Request {
        return getSeriesId(manga.url)?.let { id ->
            GET("$baseUrl/api/viewer/pagination_readable_products?type=episode&aggregate_id=$id&offset=0&limit=1&sort_order=desc&is_guest=1", headers)
        } ?: super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return if (response.request.url.toString().contains("/api/")) {
            SManga.create().apply { initialized = true }
        } else {
            super.mangaDetailsParse(response)
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return getSeriesId(manga.url)?.let { id ->
            GET("$baseUrl/api/viewer/pagination_readable_products?type=episode&aggregate_id=$id&offset=0&limit=50&sort_order=desc&is_guest=1", headers)
        } ?: super.chapterListRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val requestUrl = response.request.url.toString()

        if (!requestUrl.contains("/api/")) {
            val html = response.body.string()
            val seriesId = Regex("""data-aggregate-id="(\d+)"""").find(html)?.groupValues?.get(1)
                ?: throw Exception("Series ID not found")

            val apiUrl = "$baseUrl/api/viewer/pagination_readable_products?type=episode&aggregate_id=$seriesId&offset=0&limit=50&sort_order=desc&is_guest=1"
            val newRequest = GET(apiUrl, headers.newBuilder().add("Referer", "$baseUrl/").build())
            return chapterListParse(client.newCall(newRequest).execute())
        }

        val jsonElement = Json.parseToJsonElement(response.body.string())
        if (jsonElement !is JsonArray) return emptyList()

        val chapters = mutableListOf<SChapter>()
        chapters.addAll(parseChapters(jsonElement))

        if (jsonElement.size >= 50) {
            val seriesId = response.request.url.queryParameter("aggregate_id") ?: ""
            var offset = 50
            val limit = 50

            while (true) {
                val apiUrl = "$baseUrl/api/viewer/pagination_readable_products?type=episode&aggregate_id=$seriesId&offset=$offset&limit=$limit&sort_order=desc&is_guest=1"
                val apiResponse = client.newCall(GET(apiUrl, headers)).execute()
                val nextJson = Json.parseToJsonElement(apiResponse.body.string())

                if (nextJson !is JsonArray || nextJson.isEmpty()) break

                chapters.addAll(parseChapters(nextJson))

                if (nextJson.size < limit) break
                offset += limit
            }
        }

        return chapters
    }

    private fun parseChapters(jsonArray: JsonArray): List<SChapter> {
        return jsonArray.map { element ->
            val obj = element.jsonObject
            SChapter.create().apply {
                url = "/episode/" + obj["readable_product_id"]!!.jsonPrimitive.content
                name = obj["title"]!!.jsonPrimitive.content
                date_upload = try {
                    dateFormat.parse(obj["display_open_at"]?.jsonPrimitive?.content ?: "")?.time ?: 0L
                } catch (_: Exception) { 0L }

                val label = obj["status"]?.jsonObject?.get("label")?.jsonPrimitive?.content
                if (label != "is_free") {
                    name = "🔒 $name"
                }
            }
        }
    }

    override val chapterListMode = CHAPTER_LIST_LOCKED

    override fun getFilterList(): FilterList = FilterList()
}
