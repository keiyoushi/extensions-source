package eu.kanade.tachiyomi.extension.id.yubikiri

import eu.kanade.tachiyomi.multisrc.madara.Madara
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Yubikiri : Madara(
    "Yubikiri",
    "https://yubikiri.my.id",
    "id",
    dateFormat = SimpleDateFormat("d MMMM", Locale("en")),
) {
    override val mangaDetailsSelectorAuthor = ".manga-authors > a"
    override val mangaDetailsSelectorDescription = ".manga-summary p"
    override val mangaDetailsSelectorThumbnail = "head meta[property='og:image']" // Same as browse

    override val useNewChapterEndpoint = true

    override fun imageFromElement(element: Element): String? {
        return super.imageFromElement(element)
            ?.takeIf { it.isNotEmpty() }
            ?: element.attr("content") // Thumbnail from <head>
    }
}
