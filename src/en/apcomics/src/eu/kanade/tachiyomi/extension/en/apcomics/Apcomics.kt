package eu.kanade.tachiyomi.extension.en.apcomics

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Apcomics : Madara(
    "AP Comics",
    "https://apcomics.org",
    "en",
) {
    override val useNewChapterEndpoint: Boolean = true
}
