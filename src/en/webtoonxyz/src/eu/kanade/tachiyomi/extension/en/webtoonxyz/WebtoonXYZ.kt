package eu.kanade.tachiyomi.extension.en.webtoonxyz
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class WebtoonXYZ : Madara("WebtoonXYZ", "https://www.webtoon.xyz", "en", SimpleDateFormat("dd MMMM yyyy", Locale.US)) {
    override val mangaSubString = "read"
    override val sendViewCount = false
}
