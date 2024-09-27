package eu.kanade.tachiyomi.extension.en.mangagalaxy

import eu.kanade.tachiyomi.multisrc.iken.Iken

class MangaGalaxy : Iken(
    "Manga Galaxy",
    "en",
    "https://mangagalaxy.net",
) {
    // moved from Madara to MangaThemesia to Iken
    override val versionId = 3
}
