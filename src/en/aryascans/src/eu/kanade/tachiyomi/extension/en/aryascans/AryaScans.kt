package eu.kanade.tachiyomi.extension.en.aryascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class AryaScans : Madara() {
    override val useNewChapterEndpoint = true

    override val popularMangaUrlSelector = "${super.popularMangaUrlSelector}:not([href=New]):not([target=_self])"

    override val altNameSelector = "noSelector"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        author = null
        artist = null
    }
}
