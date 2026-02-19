package eu.kanade.tachiyomi.extension.zh.sixmh

import eu.kanade.tachiyomi.multisrc.mccms.MCCMSWeb
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class SixMH : MCCMSWeb("六漫画", "https://www.liumanhua.com") {
    private val paramsRegex = Regex("params = '([A-Za-z0-9+/=]+)'")
    override val versionId get() = 3

    override fun simpleMangaSelector(): String = "div.cy_list_mh ul"
    override fun simpleMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("li.title > a")!!.text()
        setUrlWithoutDomain(element.selectFirst("li.title > a")!!.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = super.searchMangaRequest(page, query, filters)
        // Use mobile user agent
        return GET(request.url, headers)
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()

        val element = document.selectFirst("div.cy_info")!!
        title = element.selectFirst("div.cy_title")!!.text()
        thumbnail_url = element.selectFirst("div.cy_info_cover > a > img.pic")?.absUrl("src")
        description = element.selectFirst("div.cy_desc #comic-description")?.text()

        val infoElements = element.select("div.cy_xinxi")
        author = infoElements[0].selectFirst("span:first-child > a")?.text()
        status = parseStatus(infoElements[0].selectFirst("span:nth-child(2)")?.text())
        genre = infoElements[1].select("span:first-child > a").joinToString { it.text() }
    }

    // Chapters
    override fun chapterListSelector(): String = "ul#mh-chapter-list-ol-0 li.chapter__item"
    override fun getDescendingChapters(chapters: List<SChapter>) = chapters

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val encodedData = paramsRegex.find(body)?.groupValues?.get(1) ?: ""
        val decodedData = decodeData(encodedData)

        val images = decodedData.parseAs<Data>().images
        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    @Serializable
    private class Data(val images: List<String>)

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("连载") -> SManga.ONGOING
        status.contains("完结") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
