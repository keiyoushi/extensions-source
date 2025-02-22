package eu.kanade.tachiyomi.extension.en.mangaowlio

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaOwlIo : Madara("MangaOwl.io (unoriginal)", "https://mangaowl.io", "en") {
    override val useNewChapterEndpoint = true
}
