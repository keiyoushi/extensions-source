package eu.kanade.tachiyomi.extension.ja.tonarinoyoungjump

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element

class TonariNoYoungJump :
    GigaViewer(
        "Tonari no Young Jump",
        "https://tonarinoyj.jp",
        "ja",
    ) {

    override val supportsLatest: Boolean = false

    override val popularMangaSelector: String = "ul.series-table-list li.subpage-table-list-item > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h4.title")!!.text()
        thumbnail_url = element.selectFirst("div.subpage-image-wrapper img")?.absUrl("data-src")
            ?.replace("{width}", "528")
            ?.replace("{height}", "528")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載中", ""),
        Collection("読切", "oneshot"),
        Collection("出張作品", "trial"),
    )
}
