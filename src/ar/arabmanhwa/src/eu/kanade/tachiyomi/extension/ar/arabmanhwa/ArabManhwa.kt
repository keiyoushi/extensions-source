package eu.kanade.tachiyomi.extension.ar.arabmanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ArabManhwa : Madara() {
    override val name = "ArabManhwa"
    override val baseUrl = "https://arabmanhwa.com"
    override val lang = "ar"

    override val useNewChapterEndpoint = true
}
