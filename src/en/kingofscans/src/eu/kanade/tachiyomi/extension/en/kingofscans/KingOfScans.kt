package eu.kanade.tachiyomi.extension.en.kingofscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class KingOfScans : MangaThemesia(
    "King of Scans",
    "https://kingofscans.com",
    "en",
    mangaUrlDirectory = "/comics",
) {
    // Strip out position- and name-dependant ads. "Read first at ..."
    override fun pageListParse(document: Document): List<Page> {
        val pages = super.pageListParse(document)
        return pages.filterIndexed { index, page ->
            when (index) {
                0 -> !page.imageUrl!!.endsWith("/START-KS.jpg")
                pages.lastIndex -> !page.imageUrl!!.endsWith("/END-KS.jpg")
                else -> true
            }
        }
    }
}
