package eu.kanade.tachiyomi.extension.en.hentaiwebtoon

import eu.kanade.tachiyomi.multisrc.madara.Madara

class HentaiWebtoon : Madara("HentaiWebtoon", "https://hentaiwebtoon.com", "en") {

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
