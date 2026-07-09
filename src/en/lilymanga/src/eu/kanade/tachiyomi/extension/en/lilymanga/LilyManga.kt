package eu.kanade.tachiyomi.extension.en.lilymanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LilyManga : Madara() {
    override val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client = super.client.newBuilder()
        .rateLimit(1, 2.seconds) { it.host == baseUrlHost }
        .build()

    override val mangaSubString = "ys"

    override fun searchMangaSelector() = popularMangaSelector()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
