package eu.kanade.tachiyomi.extension.fr.mangahubfr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaHubFr : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.vip-permission)"

    override fun imageFromElement(element: Element): String? = super.imageFromElement(element)?.trim()
}
