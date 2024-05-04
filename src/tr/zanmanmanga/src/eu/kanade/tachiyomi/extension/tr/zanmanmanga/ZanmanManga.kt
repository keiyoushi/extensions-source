package eu.kanade.tachiyomi.extension.tr.zanmanmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ZanmanManga : Madara(
    "Zanman Manga",
    "https://zamanmanga.com",
    "tr",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr")),
) {
    override val mangaDetailsSelectorAuthor = "div.manga-authors > a"
    override val mangaDetailsSelectorDescription = "div.manga-summary"
    override val mangaDetailsSelectorThumbnail = "head meta[property='og:image']" // Same as browse

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override fun imageFromElement(element: Element): String? {
        return super.imageFromElement(element)?.takeIf { it.isNotEmpty() }
            ?: element.attr("content") // Thumbnail from <head>
    }
}
