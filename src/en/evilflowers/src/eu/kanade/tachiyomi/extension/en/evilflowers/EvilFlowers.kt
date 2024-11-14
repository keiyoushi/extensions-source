package eu.kanade.tachiyomi.extension.en.evilflowers

import eu.kanade.tachiyomi.multisrc.madara.Madara

class EvilFlowers : Madara(
    "Evil Flowers",
    "https://evilflowers.com",
    "en",
) {
    override val versionId = 2

    override val mangaSubString = "project"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}
