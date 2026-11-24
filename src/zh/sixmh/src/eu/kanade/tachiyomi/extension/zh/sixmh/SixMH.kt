package eu.kanade.tachiyomi.extension.zh.sixmh

import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSWeb
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.textOrNull
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

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val element = document.selectFirst("div.cy_info")!!
        title = element.selectFirst("div.cy_title")!!.text()
        thumbnail_url = element.selectFirst("div.cy_info_cover > a > img.pic")?.absUrl("src")
        description = MarkdownDescription.Builder()
            .description(element.selectFirst("div.cy_desc #comic-description"))
            .build()

        author = element.selectFirst("div.cy_xinxi span:contains(作者) a")?.textOrNull()
        status = parseStatus(element.selectFirst("div.cy_xinxi span:contains(状态)")?.text())
        genre = element.select("div.cy_xinxi span:contains(类别) a").joinToString { it.text() }.ifEmpty { null }
    }

    override fun chapterListSelector(): String = "ul#mh-chapter-list-ol-0 li.chapter__item"
    override fun getDescendingChapters(chapters: List<SChapter>) = chapters

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("连载") -> SManga.ONGOING
        status.contains("完结") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
