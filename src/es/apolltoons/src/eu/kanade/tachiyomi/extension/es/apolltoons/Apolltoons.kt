package eu.kanade.tachiyomi.extension.es.apolltoons
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Apolltoons : Madara("Apolltoons", "https://apolltoons.xyz", "es", SimpleDateFormat("dd MMMMM, yyyy", Locale("es")))
