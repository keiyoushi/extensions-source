package eu.kanade.tachiyomi.extension.es.menudofansub
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide

class MenudoFansub : FoolSlide("Menudo-Fansub", "https://www.menudo-fansub.com", "es", "/slide")
