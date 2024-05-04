package eu.kanade.tachiyomi.extension.en.mahuauss

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MahuaUSS : Madara(
    "Mahua USS",
    "https://manhuauss.com",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val fetchGenres = false // Exist, but don't filter anything
}
