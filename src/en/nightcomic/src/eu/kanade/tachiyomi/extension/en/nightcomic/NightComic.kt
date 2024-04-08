package eu.kanade.tachiyomi.extension.en.nightcomic

import eu.kanade.tachiyomi.multisrc.madara.Madara

class NightComic : Madara("Night Comic", "https://nightcomic.com", "en") {
    override val useNewChapterEndpoint = true
    override val mangaDetailsSelectorAuthor = "div.manga-authors > a"
}
