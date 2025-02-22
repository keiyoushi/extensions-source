package eu.kanade.tachiyomi.extension.en.isekaiscanmanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class IsekaiScanManga : Madara("IsekaiScanManga (unoriginal)", "https://isekaiscanmanga.com", "en", SimpleDateFormat("dd MMM، yyyy", Locale.US))
