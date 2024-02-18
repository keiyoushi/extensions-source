package eu.kanade.tachiyomi.extension.en.manhwa2read

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manhwa2Read : Madara("Manhwa2Read", "https://manhwa2read.com", "en") {
    override val useNewChapterEndpoint = true
}
