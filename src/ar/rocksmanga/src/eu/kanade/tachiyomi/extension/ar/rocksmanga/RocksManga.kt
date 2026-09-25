package eu.kanade.tachiyomi.extension.ar.rocksmanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale
@Source
abstract class RocksManga : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("ar"))
    override val supportsLatest = false

    override fun archiveSelector() = ".unit"
    override val archiveUrlSelector = ".info > a"
    override fun Element.postId() = classNames().firstOrNull { it.startsWith("item-") }
        ?.removePrefix("item-")
    override fun nextPageSelector() = "li.page-item:not(.disabled) > a.page-link[rel=next]"

    override val mangaDetailsSelectorTitle = ".info h1"
    override val mangaDetailsSelectorAuthor = "div.meta span:contains(المؤلف:) + a"
    override val mangaDetailsSelectorArtist = "div.meta span:contains(الرسام:) + a"
    override val mangaDetailsSelectorStatus = ".info p"
    override val mangaDetailsSelectorDescription = "div.description"
    override val mangaDetailsSelectorThumbnail = ".manga-poster img"
    override val mangaDetailsSelectorGenre = "div.meta span:contains(التصنيفات:) ~ a"
    override val altNameSelector = ".info h6"
    override fun chapterListSelector() = "div.list-body-hh ul li"
    override val chapterNameSelector = "zebi"
    override val chapterDateSelector = "span.time"
    override val pageListParseSelector = "#ch-images .img"

    override fun chapterFromElement(element: Element, mangaPath: String) = super.chapterFromElement(element, mangaPath)?.apply {
        scanlator = element.selectFirst(".username span")?.text()
    }

    override val filterGenresSelector = "#nav-menu"
}
