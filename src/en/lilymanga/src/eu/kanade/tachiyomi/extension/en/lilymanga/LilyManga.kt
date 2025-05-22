package eu.kanade.tachiyomi.extension.en.lilymanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class LilyManga : Madara(
    "Lily Manga",
    "https://lilymanga.net",
    "en",
    dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US),
) {
    override val client = super.client.newBuilder()
        .rateLimit(baseUrl.toHttpUrl(), 1, 2.seconds)
        .build()

    override val mangaSubString = "ys"

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
