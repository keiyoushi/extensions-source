package eu.kanade.tachiyomi.extension.en.mangatxto

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaTxTo : Madara(
    "Manga Tx.to",
    "https://mangatx.to",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val mangaSubString = "manhua"
}
