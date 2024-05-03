package eu.kanade.tachiyomi.extension.en.mangaowlyaoi

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaowlYaoi : Madara(
    "Mangaowl Yaoi",
    "https://mangaowlyaoi.com",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val mangaSubString = "read"
}
