package eu.kanade.tachiyomi.extension.en.razure

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class Razure :
    MangaThemesia(
        "Razure",
        "https://razure.org",
        "en",
        "/series",
    ) {
    override fun chapterListSelector() = "#chapterlist li:not([data-num*='🔒'])"

    override fun searchMangaSelector() = ".listupd .bs .bsx:not(:has(.novelabel))"
}
