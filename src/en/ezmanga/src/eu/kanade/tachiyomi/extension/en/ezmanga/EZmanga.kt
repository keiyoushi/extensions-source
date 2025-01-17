package eu.kanade.tachiyomi.extension.en.ezmanga

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class EZmanga :
    Keyoapp(
        "EZmanga",
        "https://ezmanga.org",
        "en",
    ) {
    // Migrated from Madara to Keyoapp
    override val versionId = 2
}
