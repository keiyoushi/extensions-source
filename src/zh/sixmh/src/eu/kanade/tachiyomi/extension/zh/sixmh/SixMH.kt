package eu.kanade.tachiyomi.extension.zh.sixmh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class SixMH : SimpleParsedHttpSource() {
    private val paramsRegex = Regex("params = '([A-Za-z0-9+/=]+)'")
    override val versionId get() = 3
    override val name: String = "六漫画"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://www.liumanhua.com"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/order/hits/page/$page", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category/order/addtime/page/$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/index.php/search".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .build()
        return GET(url, headers)
    }

    override fun simpleNextPageSelector(): String? = null
    override fun simpleMangaSelector(): String = "div.cy_list_mh ul"
    override fun simpleMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("li.title > a")!!.text()
        setUrlWithoutDomain(element.selectFirst("li.title > a")!!.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val element = document.selectFirst("div.cy_info")!!
        title = element.selectFirst("div.cy_title")!!.text()
        thumbnail_url = element.selectFirst("div.cy_info_cover > a > img.pic")?.absUrl("src")
        description = element.selectFirst("div.cy_desc #comic-description")?.text()

        val infoElements = element.select("div.cy_xinxi")
        author = infoElements[0].selectFirst("span:first-child > a")?.text()
        status = parseStatus(infoElements[0].selectFirst("span:nth-child(2)")?.text())
        genre = infoElements[1].selectFirst("span:first-child > a")?.text()
    }

    // Chapters
    override fun chapterListSelector(): String = "ul#mh-chapter-list-ol-0 li.chapter__item"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        name = element.selectFirst("a > p")!!.text()
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val encodedData = paramsRegex.find(body)?.groupValues?.get(1) ?: ""
        val decodedData = decodeData(encodedData)

        val images = decodedData.parseAs<Data>().images
        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException()

    private fun parseStatus(status: String?): Int {
        return when {
            status == null -> SManga.UNKNOWN
            status.contains("连载") -> SManga.ONGOING
            status.contains("完结") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}
