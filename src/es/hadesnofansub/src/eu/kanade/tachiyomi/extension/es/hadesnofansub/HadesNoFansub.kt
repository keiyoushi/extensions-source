package eu.kanade.tachiyomi.extension.es.hadesnofansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class HadesNoFansub : Madara(
    "Hades no Fansub",
    "https://lectorhades.latamtoon.com",
    "es",
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale("es")),
) {
    override val useNewChapterEndpoint = true

    override val mangaSubString = "tmo"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaDetailsSelectorStatus = "div.summary_content > div.post-content div.post-content_item:has(div.summary-heading:contains(Status)) div.summary-content"

    override val mangaDetailsSelectorTag = "div.tags-content a.notUsed" // Site uses this for the scanlator
}
