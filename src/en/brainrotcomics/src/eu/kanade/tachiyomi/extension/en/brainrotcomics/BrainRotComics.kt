package eu.kanade.tachiyomi.extension.en.brainrotcomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class BrainRotComics : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val altNameSelector = "noSelector"

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        author = null
        artist = null
    }
}
