package eu.kanade.tachiyomi.extension.en.nikatoons

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class NikaToons :
    MangaThemesia(
        "Nika Toons",
        "https://nikatoons.com",
        "en",
    ) {
    override fun chapterListSelector() = "#chapterlist li.chapter-item:not(.premium)"
}
