package eu.kanade.tachiyomi.extension.en.hentaixdickgirl

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import org.jsoup.nodes.Document

class HentaiXDickgirl : Madara("HentaiXDickgirl", "https://hentaixdickgirl.com", "en") {

    override fun searchPage(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }
}
