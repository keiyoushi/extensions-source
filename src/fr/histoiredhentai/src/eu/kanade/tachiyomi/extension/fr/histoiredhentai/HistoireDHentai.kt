package eu.kanade.tachiyomi.extension.fr.histoiredhentai
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class HistoireDHentai : Madara("HistoireDHentai", "https://hhentai.fr", "fr", SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH))
