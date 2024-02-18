package eu.kanade.tachiyomi.extension.en.manganerds

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaNerds : Madara("Manga Nerds", "https://manganerds.com", "en") {
    override val useNewChapterEndpoint = true
}
