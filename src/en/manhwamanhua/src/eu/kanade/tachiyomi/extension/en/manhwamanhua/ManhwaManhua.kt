package eu.kanade.tachiyomi.extension.en.manhwamanhua

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhwaManhua : Madara("ManhwaManhua", "https://manhwamanhua.com", "en") {
    override val useNewChapterEndpoint = true
    override val filterNonMangaItems = false
}
