package eu.kanade.tachiyomi.extension.en.manhuanext

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manhuanext : Madara(
    "Manhuanext",
    "https://manhuanext.com",
    "en",
) {
    override val useNewChapterEndpoint = true
}
