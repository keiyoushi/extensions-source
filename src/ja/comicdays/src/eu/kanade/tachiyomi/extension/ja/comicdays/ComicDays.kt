package eu.kanade.tachiyomi.extension.ja.comicdays

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewer
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class ComicDays : GigaViewer() {
    override val popularMangaSelector: String = "ul.daily-series li.daily-series-item:has(a.link)"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("h4.daily-series-title")!!.text()
        thumbnail_url = element.selectFirst("div.daily-series-thumb img")?.absUrl("data-src")
        setUrlWithoutDomain(element.selectFirst("a.link")!!.absUrl("href"))
    }

    override val latestUpdatesSelector: String = "section#$dayOfWeek.daily $popularMangaSelector"

    override fun getCollections(): List<Collection> = listOf(
        Collection("連載作品一覧", ""),
    )
}
