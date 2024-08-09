package eu.kanade.tachiyomi.extension.en.instamanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara

class InstaManhwa : Madara(
    "InstaManhwa",
    "https://www.instamanhwa.com",
    "en",
) {
    override val filterNonMangaItems = false
    override val useNewChapterEndpoint = true
}
