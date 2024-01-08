package eu.kanade.tachiyomi.extension.en.mangaonlineteamunoriginal

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaOnlineTeamUnoriginal : Madara("MangaOnline.team (unoriginal)", "https://mangaonline.team", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
