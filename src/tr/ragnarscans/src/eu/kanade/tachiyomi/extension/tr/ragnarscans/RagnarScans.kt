package eu.kanade.tachiyomi.extension.tr.ragnarscans

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.utils.tryParse
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class RagnarScans : InitManga() {

    override val mangaUrlDirectory = "manga"

    override val popularUrlSlug = "en-cok-takip-edilenler"

    private val ragnarDateFormat = SimpleDateFormat("d MMMM yyyy HH:mm", Locale("tr"))

    override fun chapterListSelector() = "div.chapter-list > div.chapter-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))

        name = element.selectFirst("h3.uk-link-heading")!!.text()
            .substringAfterLast("–")
            .trim()

        val dateStr = element.selectFirst("div.uk-article-meta span[uk-tooltip]")?.attr("uk-tooltip")
            ?.substringAfter("title: ")?.substringBefore(";")
        date_upload = ragnarDateFormat.tryParse(dateStr)
    }
}
