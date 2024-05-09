package eu.kanade.tachiyomi.extension.en.nvmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class NvManga : Madara(
    "NvManga",
    "https://nvmanga.com",
    "en",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val seriesTypeSelector = ".post-content_item:contains(Origination) .summary-content"

    override val useLoadMoreRequest = LoadMoreStrategy.AutoDetect
    override val useNewChapterEndpoint = false

    override val mangaSubString = "webtoon"

    override val fetchGenres = false
}
