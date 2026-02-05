package eu.kanade.tachiyomi.extension.id.yubikiri

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Kaguya :
    Madara(
        "Kaguya",
        "https://kaguya.id",
        "id",
        dateFormat = SimpleDateFormat("d MMMM", Locale("en")),
    ) {

    override val client: OkHttpClient = super.client.newBuilder()
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    override val id = 1557304490417397104

    override val mangaDetailsSelectorTitle = "h1.post-title"
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div"
    override val mangaDetailsSelectorThumbnail = "head meta[property='og:image']" // Same as browse

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun imageFromElement(element: Element): String? {
        return super.imageFromElement(element)
            ?.takeIf { it.isNotEmpty() }
            ?: element.attr("content") // Thumbnail from <head>
    }
}
