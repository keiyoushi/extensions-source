package eu.kanade.tachiyomi.extension.ja.sundaywebevery

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class SundayWebEvery : GigaViewer() {
    override val popularMangaSelector: String = "ul.webry-series-list li a.webry-series-item-link"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h4.series-title")!!.text()
        thumbnail_url = element.selectFirst("div.thumb-wrapper img")?.absUrl("data-src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override val latestUpdatesSelector: String = "h3#series-$dayOfWeek + section $popularMangaSelector"

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載作品", ""),
        Collection("読切", "oneshot"),
        Collection("夜サンデー", "yoru-sunday"),
    )
}
