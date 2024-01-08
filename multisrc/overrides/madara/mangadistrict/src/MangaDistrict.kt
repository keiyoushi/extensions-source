package eu.kanade.tachiyomi.extension.en.mangadistrict

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaDistrict : Madara(
    "Manga District",
    "https://mangadistrict.com",
    "en",
) {
    override fun searchMangaNextPageSelector() = "div[role=navigation] a.last"
}
