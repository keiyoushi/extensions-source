package eu.kanade.tachiyomi.extension.zh.sixmh

import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSWeb
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class SixMH : MCCMSWeb() {
    override val config = MCCMSConfig(encryptedPageList = true)

    override fun simpleMangaSelector(): String = "div.cy_list_mh ul"
    override fun simpleMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("li.title > a")!!.text()
        setUrlWithoutDomain(element.selectFirst("li.title > a")!!.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // Use mobile user agent
    override val searchHeaders get() = headers

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
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

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("连载") -> SManga.ONGOING
        status.contains("完结") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
