package eu.kanade.tachiyomi.extension.en.mangaclick

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaGeek : Madara(
    "MangaGeek",
    "https://mangageek.org",
    "en",
) {
    override val id = 1234573178818746503
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
