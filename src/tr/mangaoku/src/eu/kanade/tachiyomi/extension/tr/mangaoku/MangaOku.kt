package eu.kanade.tachiyomi.extension.tr.mangaoku

import eu.kanade.tachiyomi.multisrc.madara.Madara
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaOku : Madara(
    "Manga Oku",
    "https://mangaoku.info",
    "tr",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr")),
) {
    override val mangaDetailsSelectorAuthor = ".manga-authors > a"
    override val mangaDetailsSelectorDescription = ".manga-summary p"
    override val mangaDetailsSelectorThumbnail = "head meta[property='og:image']" // Same as browse

    override val useNewChapterEndpoint = true

    override val mangaSubString = "seri"

    override fun imageFromElement(element: Element): String? {
        return super.imageFromElement(element)?.takeIf { it.isNotEmpty() }
            ?: element.attr("content") // Thumbnail from <head>
    }
}
