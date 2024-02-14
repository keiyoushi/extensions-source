package eu.kanade.tachiyomi.extension.en.mangakitsu

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaKitsu : Madara("Manga Kitsu", "https://mangakitsu.com", "en") {
    override val useNewChapterEndpoint = false
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"
}
