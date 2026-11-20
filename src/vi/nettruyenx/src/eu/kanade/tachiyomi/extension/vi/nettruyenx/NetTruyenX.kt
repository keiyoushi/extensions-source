package eu.kanade.tachiyomi.extension.vi.nettruyenx

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class NetTruyenX : WPComics() {
    override val popularPath = "truyen-tranh-hot"
    override val useChapterListApi = true

    // Details
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        description = document.select("div.detail-content div div:nth-child(4)").joinToString("\n") { it.wholeText().trim() }
    }
}
