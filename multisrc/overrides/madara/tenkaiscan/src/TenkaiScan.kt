package eu.kanade.tachiyomi.extension.es.tenkaiscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.Madara
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
}
