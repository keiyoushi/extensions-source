package eu.kanade.tachiyomi.extension.en.novelcrow

import eu.kanade.tachiyomi.multisrc.madara.Madara

class NovelCrow : Madara("NovelCrow", "https://novelcrow.com", "en") {
    override val useNewChapterEndpoint = true
}
