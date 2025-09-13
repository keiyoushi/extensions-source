package eu.kanade.tachiyomi.extension.en.arvencomics

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ArvenComics : Madara(
    "Arven Scans",
    "https://arvencomics.com",
    "en",
) {
    // migrated from Keyoapp to Madara
    override val versionId = 3

    override val mangaSubString = "comic"
}
