package eu.kanade.tachiyomi.extension.id.klmanhua
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class KLManhua : ZeistManga("KLManhua", "https://klmanhua.blogspot.com", "id") {

    override val hasFilters = true
    override val hasLanguageFilter = false
}
