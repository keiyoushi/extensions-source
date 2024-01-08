package eu.kanade.tachiyomi.extension.en.flextapescans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class FlexTapeScans : Madara(
    "Flex Tape Scans",
    "https://flextapescans.com",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yyy", Locale.ROOT),
) {
    override val useNewChapterEndpoint = false

    override fun pageListParse(document: Document): List<Page> {
        val blocked = document.select(".content-blocked").first()
        if (blocked != null) {
            throw Exception(blocked.text()) // You need to be contributor to read this chapter
        }

        return super.pageListParse(document)
    }
}
