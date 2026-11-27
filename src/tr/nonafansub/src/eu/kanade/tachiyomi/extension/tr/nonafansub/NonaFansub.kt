package eu.kanade.tachiyomi.extension.tr.nonafansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.attrOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NonaFansub : Madara() {
    override val seriesTypeSelector = ".post-content_item:has(h5:containsOwn(Tür)) .summary-content"

    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))

    // Guests get the same "members only" placeholder in place of every cover
    override fun imageFromElement(element: Element) = if (element.hasClass("madarwv-img")) null else super.imageFromElement(element)

    override fun processThumbnail(url: String?, fromSearch: Boolean) = url?.withoutWordPressSize()

    override fun parseDetails(document: Document, id: String, preserveUrl: String?) = super.parseDetails(document, id, preserveUrl).apply {
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attrOrNull("content")
        description = MarkdownDescription.Builder()
            .rating(
                document.selectFirst("#averagerate")?.text()?.toDoubleOrNull(),
                max = 5,
                votes = document.selectFirst("#countrate")?.text()?.toIntOrNull(),
            )
            .info(document.selectFirst(".post-content_item:has(h5:containsOwn(Yıl)) .summary-content")?.text())
            .apply { descriptionParagraphs(document).forEach(::markdown) }
            .build()
    }
}
