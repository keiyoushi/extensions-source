package eu.kanade.tachiyomi.extension.es.lolivault
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide

class Lolivault : FoolSlide("Lolivault", "https://lector.lolivault.net", "es")
