package eu.kanade.tachiyomi.extension.it.rama
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide

class Rama : FoolSlide("Rama", "https://www.ramareader.it", "it", "/read")
