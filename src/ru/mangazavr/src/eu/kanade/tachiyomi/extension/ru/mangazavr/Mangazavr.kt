package eu.kanade.tachiyomi.extension.ru.mangazavr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Mangazavr : Madara(
    "Mangazavr",
    "https://mangazavr.ru",
    "ru",
    dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT),
) {
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Статус) + div.summary-content"

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
