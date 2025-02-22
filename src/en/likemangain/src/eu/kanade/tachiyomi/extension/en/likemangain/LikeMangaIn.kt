package eu.kanade.tachiyomi.extension.en.likemangain
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class LikeMangaIn : Madara(
    "LikeMangaIn",
    "https://likemanga.in",
    "en",
    dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale.US),
) {
    override val useNewChapterEndpoint = true
}
