package eu.kanade.tachiyomi.extension.all.grabberzone

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class GrabberZone : Madara() {
    override val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ENGLISH)
    override val mangaSubString = "comics"

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        name = element.selectFirst("a + a")!!.text()
    }
}
