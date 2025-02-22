package eu.kanade.tachiyomi.extension.en.manga347
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Manga347 : Madara("Manga347", "https://manga347.com", "en", SimpleDateFormat("d MMM, yyyy", Locale.US))
