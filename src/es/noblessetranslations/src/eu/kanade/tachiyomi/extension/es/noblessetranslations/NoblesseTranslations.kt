package eu.kanade.tachiyomi.extension.es.noblessetranslations

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class NoblesseTranslations : Madara(
    "Noblesse Translations",
    "https://www.noblessev1.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("es")),
) {
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val mangaSubString = "manga"

    override val mangaDetailsSelectorDescription = "div.summary_content > div.post-content div.manga-summary"
    override val mangaDetailsSelectorStatus = "div.summary_content > div.post-content div.post-content_item:has(div.summary-heading:contains(Status)) div.summary-content"
    override val mangaDetailsSelectorTag = "div.tags-content a.notUsed" // Site uses this for the scanlator
}
