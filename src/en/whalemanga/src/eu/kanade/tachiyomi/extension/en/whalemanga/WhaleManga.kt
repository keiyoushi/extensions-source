package eu.kanade.tachiyomi.extension.en.whalemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class WhaleManga : Madara("WhaleManga", "https://whalemanga.com", "en") {
    override val useNewChapterEndpoint = true
}
