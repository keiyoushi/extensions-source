package eu.kanade.tachiyomi.extension.pt.limitedtimeproject

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class LimitedTimeProject : Madara(
    "Limited Time Project",
    "https://limitedtimeproject.com",
    "pt-BR",
    SimpleDateFormat("dd 'de' MMM 'de' yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val mangaSubString = "manhwa"

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true
}
