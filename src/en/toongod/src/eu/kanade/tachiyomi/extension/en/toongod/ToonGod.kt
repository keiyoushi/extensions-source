package eu.kanade.tachiyomi.extension.en.toongod
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ToonGod : Madara("ToonGod", "https://www.toongod.org", "en", SimpleDateFormat("d MMM yyyy", Locale.US)) {
    override val mangaSubString = "webtoons"
    override val useNewChapterEndpoint = false
}
