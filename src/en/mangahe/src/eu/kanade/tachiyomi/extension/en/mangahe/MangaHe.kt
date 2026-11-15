package eu.kanade.tachiyomi.extension.en.mangahe

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class MangaHe : Madara() {
    override fun parsePages(document: Document): List<Page> = super.parsePages(document).filterIndexed { idx, page ->
        !(idx == 0 && page.imageUrl?.endsWith("/1-000001.jpg") == true)
    }
}
