package eu.kanade.tachiyomi.extension.en.madaradex
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MadaraDex : Madara(
    "MadaraDex",
    "https://madaradex.org",
    "en",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
) {
    override fun headersBuilder() = super.headersBuilder()
        .set("sec-fetch-site", "same-site")
    override val mangaSubString = "title"
}
