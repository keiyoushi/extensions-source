package eu.kanade.tachiyomi.extension.en.mangatellers
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide

class Mangatellers : FoolSlide("Mangatellers", "https://reader.mangatellers.gr", "en")
