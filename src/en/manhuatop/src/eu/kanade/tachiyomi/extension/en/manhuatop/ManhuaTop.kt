package eu.kanade.tachiyomi.extension.en.manhuatop
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ManhuaTop : Madara(
    "ManhuaTop",
    "https://manhuatop.org",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val mangaSubString = "manhua"
    override val filterNonMangaItems = false
}
