package eu.kanade.tachiyomi.extension.pt.pinkrosa

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class PinkRosa : ZeistManga() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val supportsLatest = false

    override val mangaDetailsSelector = "#main"
    override val mangaDetailsSelectorAuthor = "#tauther"
    override val mangaDetailsSelectorDescription = "#syn_bod"
    override val mangaDetailsSelectorGenres = "a[href*=label][rel]"
    override val mangaDetailsSelectorStatus = "div[class*=bg-green] span"
    override val mangaDetailsSelectorThumbnail = ".hidden img"

    override fun getChapterFeedUrl(doc: Document, mangaTitle: String): String {
        val label = doc.selectFirst(".chapter_get")!!.attr("data-labelchapter")
        return super.getChapterFeedUrl(doc, label)
    }

    override val pageListSelector = "div.separator"

    companion object {
        val THUMBNAIL_REGEX = """url."([^"]+)""".toRegex()
    }
}
