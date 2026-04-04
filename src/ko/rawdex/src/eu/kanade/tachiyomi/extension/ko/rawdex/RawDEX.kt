package eu.kanade.tachiyomi.extension.ko.rawdex

import eu.kanade.tachiyomi.multisrc.madara.Madara

class RawDEX :
    Madara(
        "RawDEX",
        "https://rawdex.net",
        "ko",

    ) {

    override val chapterUrlSuffix = ""
    override fun searchMangaSelector() = "div.page-item-detail.manga"
}
