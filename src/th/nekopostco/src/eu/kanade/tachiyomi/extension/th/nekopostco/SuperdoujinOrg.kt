package eu.kanade.tachiyomi.extension.th.nekopostco

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class SuperdoujinOrg : Madara(
    "Superdoujin.org",
    "https://www.superdoujin.org",
    "th",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val mangaSubString = "doujin"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
