package eu.kanade.tachiyomi.extension.tr.ragnarscans

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import keiyoushi.annotation.Source
import keiyoushi.utils.tryParseDateTime
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class RagnarScans : InitManga() {

    override val mangaUrlDirectory = "manga"

    override val popularUrlSlug = "webtoon-siralamasi"

    override fun chapterFromElement(element: Element) = super.chapterFromElement(element).apply {
        val dateStr = element.selectFirst("div.uk-article-meta span[uk-tooltip]")?.attr("uk-tooltip")
            ?.substringAfter("title: ")?.substringBefore(";")
        if (!dateStr.isNullOrBlank()) {
            date_upload = ragnarDateFormat.tryParseDateTime(dateStr)
        }
    }

    companion object {
        private val ragnarDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", Locale.forLanguageTag("tr"))
    }
}
