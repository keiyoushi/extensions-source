package eu.kanade.tachiyomi.extension.ar.scans4u
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class Scans4u : Keyoapp("Scans 4u", "https://4uscans.com", "ar") {

    override fun chapterListSelector(): String {
        if (!preferences.showPaidChapters) {
            return "#chapters > a:not(:has(.text-sm span:matches(قادم))):not(:has(img[src*=Coin.svg]))"
        }
        return "#chapters > a:not(:has(.text-sm span:matches(قادم)))"
    }
}
