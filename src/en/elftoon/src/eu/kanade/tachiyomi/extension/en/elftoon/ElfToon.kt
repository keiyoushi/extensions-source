package eu.kanade.tachiyomi.extension.en.elftoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class ElfToon : MangaThemesia("Elf Toon", "https://elftoon.com", "en") {

    override fun chapterListSelector() = "#chapterlist li:not(:has(.gem-price-icon))"
}
