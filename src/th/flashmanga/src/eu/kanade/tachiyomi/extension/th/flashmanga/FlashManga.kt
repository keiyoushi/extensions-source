package eu.kanade.tachiyomi.extension.th.flashmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class FlashManga : MangaThemesia() {
    override fun chapterListParse(document: Document): List<SChapter> = super.chapterListParse(document).reversed()
}
