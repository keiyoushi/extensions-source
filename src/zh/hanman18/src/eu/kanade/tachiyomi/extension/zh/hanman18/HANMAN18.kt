package eu.kanade.tachiyomi.extension.zh.hanman18
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.manga18.Manga18

class HANMAN18 : Manga18("HANMAN18", "https://hanman18.com", "zh") {
    // tag filter doesn't work on site
    override val getAvailableTags = false
}
