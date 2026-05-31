package eu.kanade.tachiyomi.extension.en.mangaka

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaKa : Madara("MangaKa", "https://mangaka.cc", "en") {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
