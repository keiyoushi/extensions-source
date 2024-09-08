package eu.kanade.tachiyomi.extension.en.kewnscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class KewnScans : Keyoapp("Kewn Scans", "https://kewnscans.org", "en") {

    private val cdnUrl = "https://cdn.igniscans.com"

    override fun pageListParse(document: Document): List<Page> {
        return document.select("#pages > img")
            .map { it.attr("uid") }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, img ->
                Page(index, document.location(), "$cdnUrl/uploads/$img")
            }
    }
}
