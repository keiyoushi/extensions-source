package eu.kanade.tachiyomi.extension.en.yakshacomics

import eu.kanade.tachiyomi.multisrc.madara.Madara

class YakshaComics : Madara(
    "YakshaComics",
    "https://yakshacomics.com",
    "en",
) {
    override val useNewChapterEndpoint = true
}
