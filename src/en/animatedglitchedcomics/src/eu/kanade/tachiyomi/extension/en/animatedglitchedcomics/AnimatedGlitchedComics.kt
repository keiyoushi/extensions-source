package eu.kanade.tachiyomi.extension.en.animatedglitchedcomics
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class AnimatedGlitchedComics : Keyoapp(
    "Animated Glitched Comics",
    "https://agrcomics.com",
    "en",
) {
    override val versionId = 2
}
