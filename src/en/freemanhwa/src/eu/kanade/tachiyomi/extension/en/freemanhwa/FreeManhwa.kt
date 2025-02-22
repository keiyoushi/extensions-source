package eu.kanade.tachiyomi.extension.en.freemanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FreeManhwa : Madara("Free Manhwa", "https://manhwas.com", "en") {
    override val useNewChapterEndpoint = false
}
