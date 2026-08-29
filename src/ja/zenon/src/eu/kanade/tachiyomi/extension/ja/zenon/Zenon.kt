package eu.kanade.tachiyomi.extension.ja.zenon

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class Zenon : GigaViewer() {
    override val supportsLatest: Boolean = false

    override val popularMangaSelector: String = ".series-item"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst(".item-series-title")!!.text()
        thumbnail_url = element.selectFirst(".img-wrapper img")?.absUrl("data-src")?.ifEmpty {
            element.selectFirst(".img-wrapper img")?.absUrl("src")
        }
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override val searchMangaNextPageSelector = "a.pager-next"

    override fun getCollections(): List<Collection> = listOf(
        Collection("読切作品", "oneshot"),
        Collection("漫画賞", "newcomer"),
    )
}
