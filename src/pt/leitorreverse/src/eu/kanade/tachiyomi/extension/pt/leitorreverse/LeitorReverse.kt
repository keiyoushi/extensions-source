package eu.kanade.tachiyomi.extension.pt.leitorreverse

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class LeitorReverse : ZeistManga() {

    // The front page has no popular list, only the series feed
    override val supportsLatest = false

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("#post-title")!!.text()
        thumbnail_url = document.selectFirst("[data-separator] .separator img")?.absUrl("src")
        description = document.selectFirst("#syn_bod")?.text()
        genre = (document.select("span[type]") + document.select("a[rel=tag]")).joinToString { it.text() }.ifEmpty { null }
    }

    override fun getChapterFeedUrl(doc: Document, mangaTitle: String): String = apiUrl(chapterCategory)
        .addPathSegment(doc.selectFirst(".chapter_get")!!.attr("data-labelchapter"))
        .build()
        .toString()

    override val pageListSelector = ".i_img .separator"
}
