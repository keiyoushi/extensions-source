package eu.kanade.tachiyomi.extension.es.tenkaiscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
class TenkaiScan : Madara(
    "TenkaiScan",
    "https://tenkaiscan.net",
    "es",
    dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es")),
) {
    override val versionId = 2
    override val useNewChapterEndpoint = true
    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Status) > div.summary-content"
}
