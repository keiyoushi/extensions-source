package eu.kanade.tachiyomi.extension.ja.saikomangaraw

import eu.kanade.tachiyomi.multisrc.madara.Madara

class SaikoMangaRaw : Madara(
    "SaikoMangaRaw",
    "https://saikomangaraw.com",
    "ja",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
