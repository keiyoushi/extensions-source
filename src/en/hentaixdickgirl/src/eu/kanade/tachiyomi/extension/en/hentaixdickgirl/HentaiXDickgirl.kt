package eu.kanade.tachiyomi.extension.en.hentaixdickgirl

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class HentaiXDickgirl : Madara() {
    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }
}
