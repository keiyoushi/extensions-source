package eu.kanade.tachiyomi.extension.hi.comicsvalley

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ComicsValley : Madara(
    "Comics Valley",
    "https://comicsvalley.com",
    "all",
) {
    override val mangaSubString = "comics-new"
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val id = 1103204227230640533
}
