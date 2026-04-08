package eu.kanade.tachiyomi.extension.id.astralscans

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element

class AstralScans : MangaThemesia("Astral Scans", "https://astralscans.top", "id") {

    override val hasProjectPage = true

    // Site uses a custom anti-scraper layout; chapter items are inside #kumpulan-bab-area
    override fun chapterListSelector() = "div#kumpulan-bab-area .astral-item"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        // Chapter URLs are Base64-encoded in data-u instead of plain <a href> links
        val dataU = element.selectFirst(".js-link")?.attr("data-u") ?: ""
        val decoded = if (dataU.isNotEmpty()) String(Base64.decode(dataU, Base64.DEFAULT)) else ""
        setUrlWithoutDomain(decoded)
        name = element.selectFirst(".ch-title")!!.text()
        date_upload = element.selectFirst(".ch-date")?.text().parseChapterDate()
    }
}
