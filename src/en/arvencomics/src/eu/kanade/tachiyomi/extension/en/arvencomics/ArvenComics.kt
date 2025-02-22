package eu.kanade.tachiyomi.extension.en.arvencomics
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class ArvenComics : Keyoapp(
    "Arven Scans",
    "https://arvencomics.com",
    "en",
) {
    // migrated from Mangathemesia to Keyoapp
    override val versionId = 2
}
