package eu.kanade.tachiyomi.extension.pt.apenasumafa

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class ApenasUmaFa : ZeistManga() {
    override val supportsLatest = false

    override val mangaDetailsSelector = "#main"
    override val mangaDetailsSelectorArtist = "span#tartist"
    override val mangaDetailsSelectorAuthor = "span#tauther"
    override val mangaDetailsSelectorDescription = "#syn_bod"
    override val mangaDetailsSelectorGenres = "a[href*='search/label'].leading-none"
    override val mangaDetailsSelectorStatus = "div[class*=bg-green] span"
    override val mangaDetailsSelectorThumbnail = ".hidden img"

    override fun getChapterFeedUrl(doc: Document, mangaTitle: String): String {
        val feed = doc.selectFirst(".chapter_get")!!.attr("data-labelchapter")
        return super.getChapterFeedUrl(doc, feed)
    }

    override val pageListSelector = "#reader div.separator"
}
