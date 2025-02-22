package eu.kanade.tachiyomi.extension.en.nvmanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class OneManhwa : Madara(
    "1Manhwa",
    "https://1manhwa.com",
    "en",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val seriesTypeSelector = ".post-content_item:contains(Origination) .summary-content"

    override val useLoadMoreRequest = LoadMoreStrategy.AutoDetect
    override val useNewChapterEndpoint = true

    override val mangaSubString = "webtoon"

    override val fetchGenres = false
}
