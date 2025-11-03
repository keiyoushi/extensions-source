package eu.kanade.tachiyomi.extension.es.barmanga

import android.util.Base64
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

    private val newImageUrlRegex = """var u1 = '([A-Za-z0-9+/=]+)';\s*var u2 = '([A-Za-z0-9+/=]+)';""".toRegex()
    override val pageListParseSelector = "div.page-break"

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        return document.select(pageListParseSelector).mapIndexedNotNull { index, element ->
            val scripts = element.select("script")

            val scriptData = scripts.firstNotNullOfOrNull { script ->
                val data = script.data()
                if (data.contains("var u1") && data.contains("var u2")) {
                    data
                } else {
                    null
                }
            } ?: return@mapIndexedNotNull null

            val match = newImageUrlRegex.find(scriptData) ?: return@mapIndexedNotNull null
            val (b64part1, b64part2) = match.destructured

            val part1 = String(Base64.decode(b64part1, Base64.DEFAULT))
            val part2 = String(Base64.decode(b64part2, Base64.DEFAULT))

            Page(index, document.location(), part1 + part2)
        }
    }
}
