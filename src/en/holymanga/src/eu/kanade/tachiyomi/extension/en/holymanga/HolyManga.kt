package eu.kanade.tachiyomi.extension.en.holymanga

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class HolyManga : FMReader(
    "HolyManga",
    "https://w34.holymanga.net",
    "en",
    SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
) {
    override val versionId = 2

    override val chapterUrlSelector = ""

    override fun chapterFromElement(element: Element, mangaTitle: String): SChapter {
        return super.chapterFromElement(element, mangaTitle).apply {
            date_upload = element.select(chapterTimeSelector).text().let { parseAbsoluteDate(it) }
        }
    }
}
