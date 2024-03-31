package eu.kanade.tachiyomi.extension.tr.mangawt

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaWT : Madara(
    "MangaWT",
    "https://mangawt.net",
    "tr",
) {
    override val useNewChapterEndpoint = true
}
