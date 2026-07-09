package eu.kanade.tachiyomi.extension.ar.comicverse

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class ComicVerse : ZeistManga() {

    override fun getChapterFeedUrl(doc: Document): String {
        val widget = doc.selectFirst("div.manga-widget[data-label]")
            ?: throw Exception("Failed to find chapter feed")

        return apiUrl(chapterCategory)
            .addPathSegment(widget.attr("data-label"))
            .build().toString()
    }
}
