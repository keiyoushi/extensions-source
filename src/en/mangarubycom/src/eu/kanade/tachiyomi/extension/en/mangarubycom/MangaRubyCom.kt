package eu.kanade.tachiyomi.extension.en.mangarubycom

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaRubyCom : Madara("MangaRuby.com", "https://mangaruby.com", "en") {
    override val useNewChapterEndpoint = true
    override val filterNonMangaItems = false
}
