package eu.kanade.tachiyomi.extension.en.kewnscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class KewnScans : Keyoapp("Kewn Scans", "https://kewnscans.org", "en") {
    override fun pageListParse(document: Document): List<Page> {
        return document.select("#pages > img:not(.hidden)").map {
            val index = it.attr("count").toInt()
            Page(index, document.location(), it.imgAttr("150"))
        }
    }
}
