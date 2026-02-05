package eu.kanade.tachiyomi.extension.en.manga18free

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manga18Free :
    Madara(
        "Manga18Free",
        "https://manga18free.com",
        "en",
    ) {
    override fun searchMangaNextPageSelector() = "a.nextpostslink"
}
