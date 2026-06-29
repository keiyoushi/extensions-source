package eu.kanade.tachiyomi.extension.en.wuxiaworld

import eu.kanade.tachiyomi.multisrc.madara.Madara

class WuxiaWorld : Madara("WuxiaWorld", "https://wuxiaworld.site", "en") {
    override val mangaSubString = "novel"
    override val useNewChapterEndpoint = true
}
