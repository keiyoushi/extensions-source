package eu.kanade.tachiyomi.extension.es.darknebulus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class DarkNebulus : Madara(
    "Dark Nebulus",
    "https://www.darknebulus.com",
    "es",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorAuthor = "strong:contains(Autor) + span a"
    override val mangaDetailsSelectorArtist = "strong:contains(Artista) + span a"
    override val mangaDetailsSelectorDescription = ".manga-summary"
    override val mangaDetailsSelectorThumbnail = "head meta[property=og:image]"

    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("content") -> element.attr("abs:content")
            else -> super.imageFromElement(element)
        }
    }
}
