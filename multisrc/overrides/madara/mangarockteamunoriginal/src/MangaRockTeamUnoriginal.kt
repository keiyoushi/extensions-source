package eu.kanade.tachiyomi.extension.en.mangarockteamunoriginal

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaRockTeamUnoriginal : Madara("Manga Rock.team (unoriginal)", "https://mangarock.team", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
