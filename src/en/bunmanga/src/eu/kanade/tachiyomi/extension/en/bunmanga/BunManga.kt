package eu.kanade.tachiyomi.extension.en.bunmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class BunManga : Madara("Bun Manga", "https://bunmanga.com", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
