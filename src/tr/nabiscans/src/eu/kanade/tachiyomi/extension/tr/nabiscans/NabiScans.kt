package eu.kanade.tachiyomi.extension.tr.nabiscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class NabiScans : Madara(
    "Nabi Scans",
    "https://nabiscans.com",
    "tr",
    SimpleDateFormat("d MMMM yyyy", Locale("tr")),
) {
    override val popularMangaUrlSelector = "div.chap-title a"
    override val useNewChapterEndpoint = true
}
