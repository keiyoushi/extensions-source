package eu.kanade.tachiyomi.extension.zh.sixmh

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class SixMH : SimpleParsedHttpSource() {
    private val paramsRegex = Regex("params = '([A-Za-z0-9+/=]+)'")
    private val json by injectLazy<Json>()
    override val versionId get() = 3
    override val name: String = "六漫画"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://www.liumanhua.com"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/order/hits/page/$page", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category/order/addtime/page/$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
            .appendPath("index.php")
            .appendPath("search")
            .appendQueryParameter("key", query).toString()
        return GET(uri, headers)
    }

    override fun simpleNextPageSelector(): String? = null
    override fun simpleMangaSelector(): String = "div.cy_list_mh ul"
    override fun simpleMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("li.title > a").text()
        url = element.select("li.title > a").attr("href")
        thumbnail_url = element.select("img").attr("src")
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val element = document.select("div.cy_info")
        title = element.select("div.cy_title").text()
        thumbnail_url = element.select("div.cy_info_cover > a > img.pic").attr("src")
        description = element.select("div.cy_desc #comic-description").text()

        val infoElements = element.select("div.cy_xinxi")
        author = infoElements[0]!!.select("span:first-child > a").text()
        artist = author
        status = parseStatus(infoElements[0]!!.select("span:nth-child(2)").text())
        genre = infoElements[1]!!.select("span:first-child > a").text()
        initialized = true
    }

    // Chapters
    override fun chapterListSelector(): String = "ul#mh-chapter-list-ol-0 li.chapter__item"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.select("a").attr("href")
        name = element.select("a > p").text()
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val encodedData = paramsRegex.find(body)?.groupValues?.get(1) ?: ""
        val decodedData = decodeData(encodedData)

        val images = json.decodeFromString<Data>(decodedData).images
        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException()

    private fun parseStatus(status: String): Int {
        return if (status.contains("连载")) {
            SManga.ONGOING
        } else if (status.contains("完结")) {
            SManga.COMPLETED
        } else {
            SManga.UNKNOWN
        }
    }
}
