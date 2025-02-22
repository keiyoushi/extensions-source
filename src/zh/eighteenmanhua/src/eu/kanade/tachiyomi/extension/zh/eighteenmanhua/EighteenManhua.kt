package eu.kanade.tachiyomi.extension.zh.eighteenmanhua
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.goda.GoDa

class EighteenManhua : GoDa("18漫画", "https://18mh.org", "zh")
