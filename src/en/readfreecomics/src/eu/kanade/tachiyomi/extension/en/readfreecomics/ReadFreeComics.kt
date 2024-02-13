package eu.kanade.tachiyomi.extension.en.readfreecomics

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ReadFreeComics : Madara("ReadFreeComics", "https://readfreecomics.com", "en") {
    override val useNewChapterEndpoint = true
}
