package eu.kanade.tachiyomi.extension.en.lilymanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LilyManga : Madara("Lily Manga", "https://lilymanga.net", "en", SimpleDateFormat("yyyy-MM-dd", Locale.US)) {
    override val mangaSubString = "ys"
}
