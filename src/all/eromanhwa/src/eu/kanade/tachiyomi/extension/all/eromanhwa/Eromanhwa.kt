package eu.kanade.tachiyomi.extension.all.eromanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Eromanhwa : Madara(
    "Eromanhwa",
    "https://eromanhwa.org",
    "en",
) {
    override val useNewChapterEndpoint = true
}
