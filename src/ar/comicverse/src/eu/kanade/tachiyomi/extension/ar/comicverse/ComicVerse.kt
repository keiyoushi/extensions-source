package eu.kanade.tachiyomi.extension.ar.comicverse

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import org.jsoup.nodes.Document

class ComicVerse : ZeistManga("Comic Verse", "https://arcomixverse.blogspot.com", "ar") {

    override fun getChapterFeedUrl(doc: Document): String {
        val widget = doc.selectFirst("div.manga-widget[data-label]")
            ?: throw Exception("Failed to find chapter feed")

        return apiUrl(chapterCategory)
            .addPathSegment(widget.attr("data-label"))
            .build().toString()
    }
}
