package eu.kanade.tachiyomi.extension.en.ragescans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class RageScans :
    MangaThemesia(
        "Rage Scans",
        "https://ragescans.com",
        "en",
    ) {
    override fun chapterListSelector() = "li:has(.chbox .eph-num):not(:has([data-bs-target='#lockedChapterModal']))"
}
