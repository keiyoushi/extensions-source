package eu.kanade.tachiyomi.extension.tr.paradoxscans

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element

class ParadoxScans :
    InitManga(
        "Paradox Scans",
        "https://paradoxscans.com",
        "tr",
        latestUrlSlug = "recently-updated",
        versionId = 1,
    ) {

    override fun popularMangaSelector() = "div.manga-item-grid > div.uk-panel"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val linkElement = element.selectFirst("div.uk-overflow-hidden a")
            ?: element.selectFirst("h2 a, h3 a, a.uk-link-heading")
            ?: element.selectFirst("a")

        title = element.selectFirst("h2 a, h3 a")?.text()
            ?: element.select("h2").text()
            ?: element.select("h3").text()

        setUrlWithoutDomain(linkElement!!.attr("href"))

        thumbnail_url = element.selectFirst("img")?.let { img ->
            img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
        }
    }
}
