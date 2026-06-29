package eu.kanade.tachiyomi.extension.en.cucumbermanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class CucumberManga : Madara("Cucumber Manga", "https://cucumbermanga.com", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
