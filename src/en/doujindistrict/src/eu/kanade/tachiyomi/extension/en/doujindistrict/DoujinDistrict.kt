package eu.kanade.tachiyomi.extension.en.doujindistrict

import eu.kanade.tachiyomi.multisrc.madara.Madara

class DoujinDistrict : Madara(
    "Doujin District",
    "https://doujindistrict.com",
    "en",
) {
    override fun searchMangaNextPageSelector() = "div[role=navigation] span.current + a.page"
}
