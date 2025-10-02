package eu.kanade.tachiyomi.extension.es.barmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class BarManga : Madara(
    "BarManga",
    "https://libribar.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaDetailsSelectorDescription = "div.flamesummary > div.manga-excerpt"

    private val imageUrlRegex = """fetch\(['"](.*?)['"]\)""".toRegex()

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        return document.select(pageListParseSelector).mapIndexed { index, element ->
            val script = element.selectFirst("script")?.data()
            val imageUrl = script?.let { imageUrlRegex.find(it)?.groupValues?.get(1) }
                ?: element.selectFirst("img")?.let { imageFromElement(it) }
            Page(index, document.location(), imageUrl)
        }
    }
}
