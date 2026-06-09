package eu.kanade.tachiyomi.extension.es.knightnoscanlation

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class KnightNoScanlation :
    Madara(
        "Knight No Scanlation",
        "https://lectorknight.com",
        "es",
        SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ) {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2, 1.seconds) { it.host == baseUrlHost }
        .build()

    override val mangaSubString = "sr"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Status) div.summary-content"

    override fun popularMangaSelector() = "div.manga__item"

    override val popularMangaUrlSelector = "div.post-title a"
}
