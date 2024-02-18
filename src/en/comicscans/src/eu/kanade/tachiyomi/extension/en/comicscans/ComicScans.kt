package eu.kanade.tachiyomi.extension.en.comicscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ComicScans : Madara("Comic Scans", "https://www.comicscans.org", "en") {
    override val useNewChapterEndpoint = true
}
