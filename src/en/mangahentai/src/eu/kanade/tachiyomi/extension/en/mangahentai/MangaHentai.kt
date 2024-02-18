package eu.kanade.tachiyomi.extension.en.mangahentai

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaHentai : Madara("Manga Hentai", "https://mangahentai.me", "en") {
    override val mangaSubString = "manga-hentai"

    override val useNewChapterEndpoint: Boolean = true
}
