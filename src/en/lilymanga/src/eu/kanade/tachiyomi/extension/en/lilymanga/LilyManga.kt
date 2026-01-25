package eu.kanade.tachiyomi.extension.en.lilymanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class LilyManga : Madara(
    "Lily Manga",
    "https://lilymanga.net",
    "en",
    dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US),
) {
    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 2)
        .build()

    override val mangaSubString = "ys"

    override fun searchMangaSelector() = popularMangaSelector()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
