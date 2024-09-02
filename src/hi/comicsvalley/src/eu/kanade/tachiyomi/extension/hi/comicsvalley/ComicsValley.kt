package eu.kanade.tachiyomi.extension.hi.comicsvalley

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ComicsValley : Madara(
    "Comics Valley",
    "https://comicsvalley.com",
    "hi",
) {
    override val mangaSubString = "comics-new"
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
