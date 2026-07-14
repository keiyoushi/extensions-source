package eu.kanade.tachiyomi.extension.en.ragescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source

@Source
abstract class RageScans : MangaThemesia() {
    override fun chapterListSelector() = "li:has(.chbox .eph-num):not(:has([data-bs-target='#lockedChapterModal']))"
}
