package eu.kanade.tachiyomi.extension.id.kombatch

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class Kombatch : MangaThemesia() {
    override val seriesAuthorSelector = ".imptdt-author-sub-2 .js-button-custom"
    override val seriesArtistSelector = ".imptdt-artist-sub-2 .js-button-custom"

    override fun mangaDetailsParse(document: Document): SManga {
        // Removes the "Download Batch" section while keeping alternative names
        document.selectFirst(seriesDescriptionSelector)?.apply {
            text(text().substringBefore("Download Batch").trim())
        }
        return super.mangaDetailsParse(document)
    }
}
