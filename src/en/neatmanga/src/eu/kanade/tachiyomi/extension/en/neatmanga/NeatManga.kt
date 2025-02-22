package eu.kanade.tachiyomi.extension.en.neatmanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class NeatManga : Madara("NeatManga", "https://neatmanga.com", "en", SimpleDateFormat("dd MMM yyyy", Locale.US)) {
    override val useNewChapterEndpoint = true
}
