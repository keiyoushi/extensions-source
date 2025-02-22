package eu.kanade.tachiyomi.extension.id.komikges
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.makaru.Makaru

class KomikGes : Makaru("KomikGes", "https://www.komikges.my.id", "id")
