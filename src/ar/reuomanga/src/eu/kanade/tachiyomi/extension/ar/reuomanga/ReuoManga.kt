package eu.kanade.tachiyomi.extension.ar.reuomanga

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.MarkdownDescription
import org.jsoup.nodes.Document

@Source
abstract class ReuoManga : ZeistManga() {

    // Besides its three trending series, the home page has no popular list
    override val supportsLatest = false

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("#post-title")!!.text()
        thumbnail_url = document.selectFirst("[data-separator] img")?.absUrl("src")
        author = document.selectFirst("#tauther")?.text()
        genre = document.select("a[rel=tag]").eachText().joinToString().ifEmpty { null }
        // Only finished series have a status badge
        status = if (document.select("span.capitalize").any { it.text() == "Completed" }) SManga.COMPLETED else SManga.UNKNOWN
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("#syn_bod"))
            .field("سنة الإصدار", document.selectFirst("#trelease")?.text())
            .build()
    }

    // The series page names the label its chapters are posted under
    override fun getChapterFeedUrl(doc: Document, mangaTitle: String): String = apiUrl(chapterCategory)
        .addPathSegment(doc.selectFirst(".chapter_get")!!.attr("data-labelchapter"))
        .build()
        .toString()

    override val pageListSelector = "div.i_img"
}
