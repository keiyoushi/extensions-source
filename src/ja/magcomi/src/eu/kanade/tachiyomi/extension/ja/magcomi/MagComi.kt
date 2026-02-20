package eu.kanade.tachiyomi.extension.ja.magcomi

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element

class MagComi :
    GigaViewer(
        "MAGCOMI",
        "https://magcomi.com",
        "ja",
    ) {
    override val supportsLatest: Boolean = false

    override val popularMangaSelector: String = "ul[class^=\"SeriesSection_series_list\"] > li > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("div.jsx-series-thumb > span > noscript > img")?.absUrl("src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override val searchMangaSelector: String = "li[class^=SearchResultItem_li__]"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = element.selectFirst("p[class^=SearchResultItem_series_title__]")!!.text()
        thumbnail_url = link.selectFirst("img")?.absUrl("src")
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載中", ""),
        Collection("読切", "oneshot"),
        Collection("漫画賞・他", "award_other"),
        Collection("完結・休止", "finished"),
    )
}
