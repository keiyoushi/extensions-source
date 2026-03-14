package eu.kanade.tachiyomi.extension.en.orchisasia

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Orchisasia : Madara("Orchisasia", "https://www.orchisasia.org", "en") {
    override val mangaSubString = "comic"
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
