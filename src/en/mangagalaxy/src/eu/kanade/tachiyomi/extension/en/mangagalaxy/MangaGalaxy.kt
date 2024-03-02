package eu.kanade.tachiyomi.extension.en.mangagalaxy

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class MangaGalaxy : MangaThemesia(
    "Manga Galaxy",
    "https://mangagalaxy.me",
    "en",
    mangaUrlDirectory = "/series",
) {
    // moved from Madara to MangaThemesia
    override val versionId = 2
}
