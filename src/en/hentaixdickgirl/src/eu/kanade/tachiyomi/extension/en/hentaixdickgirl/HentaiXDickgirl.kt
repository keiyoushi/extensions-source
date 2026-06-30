package eu.kanade.tachiyomi.extension.en.hentaixdickgirl

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class HentaiXDickgirl : Madara() {

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }
}
