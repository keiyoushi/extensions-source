package eu.kanade.tachiyomi.extension.pt.sussyscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class SussyScan : Madara(
    "Sussy Scan",
    "https://oldi.sussytoons.com",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorTitle = "${super.mangaDetailsSelectorTitle}, span.rate-title, title"
    override val mangaDetailsSelectorThumbnail = "head meta[property='og:image']"

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        title = title.substringBeforeLast("–")
    }

    override fun imageFromElement(element: Element): String? {
        return super.imageFromElement(element)?.takeIf { it.isNotEmpty() }
            ?: element.attr("content") // Thumbnail from <head>
    }
}
