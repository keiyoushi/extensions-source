package eu.kanade.tachiyomi.extension.es.barmanga

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class BarManga :
    Madara(
        "BarManga",
        "https://libribar.com",
        "es",
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaDetailsSelectorDescription = "div.flamesummary > div.manga-excerpt"

    override val pageListParseSelector = "div.page-break"

    private val imageSegmentsRegex = """var\s+imageSegments\s*=\s*\[\s*(['"][A-Za-z0-9+/=]+['"](?:\s*,\s*['"][A-Za-z0-9+/=]+['"])*)\s*];""".toRegex()
    private val base64ItemRegex = """['"]([A-Za-z0-9+/=]+)['"]""".toRegex()

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        return document.select(pageListParseSelector).mapIndexedNotNull { index, element ->
            val scriptData = element.select("script").firstNotNullOfOrNull { script ->
                val data = script.data()
                if (data.contains("var imageSegments")) data else null
            } ?: return@mapIndexedNotNull null

            val match = imageSegmentsRegex.find(scriptData) ?: return@mapIndexedNotNull null
            val arrayContent = match.groupValues[1]

            val segments = base64ItemRegex.findAll(arrayContent).map { it.groupValues[1] }.toList()
            if (segments.isEmpty()) return@mapIndexedNotNull null

            val joinedBase64 = segments.joinToString("")
            val imageUrl = String(Base64.decode(joinedBase64, Base64.DEFAULT))

            Page(index, document.location(), imageUrl)
        }
    }
}
