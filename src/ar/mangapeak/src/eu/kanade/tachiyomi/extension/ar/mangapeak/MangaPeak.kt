package eu.kanade.tachiyomi.extension.ar.mangapeak

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaPeak : Madara(
    "MangaPeak",
    "https://mangapeak.org",
    "ar",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
