package eu.kanade.tachiyomi.extension.th.moodtoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class Moodtoon : MangaThemesia() {
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        // Add 'color' badge as a genre
        if (document.selectFirst(".thumb .colored") != null) {
            genre = genre?.plus(", Color")
        }
    }
}
