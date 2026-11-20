package eu.kanade.tachiyomi.extension.vi.nhattruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class NhatTruyen : WPComics() {

    override val popularPath = "truyen-tranh-hot"

    override val useChapterListApi = true

    /**
     * NetTruyen/NhatTruyen redirect back to catalog page if searching query is not found.
     * That makes both sites always return un-relevant results when searching should return empty.
     */
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        description = document.select("div.detail-content div.shortened").flatMap { it.children() }
            .joinToString("\n\n") { it.wholeText().trim() }
    }

    override fun getSortList() = netTruyenSortList()
}
