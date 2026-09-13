package eu.kanade.tachiyomi.extension.en.ragescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class RageScans : MangaThemesia() {
    override fun chapterListSelector() = "li:has(.chbox .eph-num):not(:has([data-bs-target='#lockedChapterModal']))"

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document.also { it.select("#comments").remove() })
}
