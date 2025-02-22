package eu.kanade.tachiyomi.extension.en.mangapuma
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madtheme.MadTheme

class MangaPuma :
    MadTheme(
        "MangaPuma",
        "https://mangapuma.com",
        "en",
    ) {
    override val id = 7893483872024027913
}
