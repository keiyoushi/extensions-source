package eu.kanade.tachiyomi.extension.ar.nijitranslations
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class NijiTranslations : Madara("Niji Translations", "https://niji-translations.com", "ar", SimpleDateFormat("MMMM dd, yyyy", Locale("ar")))
