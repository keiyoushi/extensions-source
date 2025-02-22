package eu.kanade.tachiyomi.extension.id.birdtoon

import eu.kanade.tachiyomi.multisrc.madara.Madara

class BirdToon : Madara(
    "BirdToon",
    "https://birdtoon.org",
    "id",
) {
    override val mangaSubString = "komik"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
