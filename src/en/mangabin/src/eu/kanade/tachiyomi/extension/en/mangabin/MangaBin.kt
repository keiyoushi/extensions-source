package eu.kanade.tachiyomi.extension.en.mangabin

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaBin : Madara(
    "MangaBin",
    "https://mangabin.com",
    "en",
) {
    override val useNewChapterEndpoint = true
}
