package eu.kanade.tachiyomi.extension.en.arvencomics

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class ArvenComics : Keyoapp(
    "Arven Scans",
    "https://arvencomics.com",
    "en",
) {
    // migrated from Mangathemesia to Keyoapp
    override val versionId = 2
}
