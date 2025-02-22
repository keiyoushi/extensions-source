package eu.kanade.tachiyomi.extension.tr.anikiga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Anikiga : Madara("Anikiga", "https://anikiga.com", "tr", SimpleDateFormat("d MMMMM yyyy", Locale("tr")))
