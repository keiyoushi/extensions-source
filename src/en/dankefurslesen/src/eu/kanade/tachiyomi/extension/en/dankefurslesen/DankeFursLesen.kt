package eu.kanade.tachiyomi.extension.en.dankefurslesen
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.guya.Guya

class DankeFursLesen : Guya("Danke fürs Lesen", "https://danke.moe", "en")
