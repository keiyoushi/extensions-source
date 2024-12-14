package eu.kanade.tachiyomi.extension.pt.lunarscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class LunarScan : Madara(
    "Lunar Scan",
    "https://lunarrscan.com",
    "pt-BR",
    SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
) {
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override val mangaSubString = "obras"

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
