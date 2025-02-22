package eu.kanade.tachiyomi.extension.en.manhwafull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Manhwafull : Madara("Manhwafull", "https://manhwafull.com", "en", SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH))
