package eu.kanade.tachiyomi.extension.en.kirascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class KiraScans : MangaThemesia(
    "Kira Scans",
    "https://kirascans.com",
    "en",
) {
    override fun pageListParse(document: Document): List<Page> {
        document.selectFirst("p.fw-semibold:contains(This chapter is locked)")?.let {
            throw Exception("Chapter is locked")
        }
        return super.pageListParse(document)
    }
}
