package eu.kanade.tachiyomi.extension.id.astralscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element

class AstralScans : MangaThemesia("Astral Scans", "https://astralscans.top", "id") {

    override val hasProjectPage = true

    override fun chapterListSelector() = "${super.chapterListSelector()}, .daftar-bab-area .baris-bab"

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        element.selectFirst(".nomor-bab")?.text()?.takeIf { it.isNotBlank() }?.let { name = it }
        if (date_upload == 0L) {
            date_upload = element.selectFirst(".tanggal-bab")?.text().parseChapterDate()
        }
    }
}
