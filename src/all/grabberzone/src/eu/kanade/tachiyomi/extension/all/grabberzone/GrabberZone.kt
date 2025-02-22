package eu.kanade.tachiyomi.extension.all.grabberzone

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class GrabberZone : Madara(
    "Grabber Zone",
    "https://grabber.zone",
    "all",
    SimpleDateFormat("dd.MM.yyyy", Locale.ENGLISH),
) {
    override val mangaSubString = "comics"

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            name = element.selectFirst("a + a")!!.text()
        }
    }
}
