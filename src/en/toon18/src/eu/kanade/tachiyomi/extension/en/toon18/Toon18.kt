package eu.kanade.tachiyomi.extension.en.toon18

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Toon18 : Madara("Toon18", "https://toon18.to", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
