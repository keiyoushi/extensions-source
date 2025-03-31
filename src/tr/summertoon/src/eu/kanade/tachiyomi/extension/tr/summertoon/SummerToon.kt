package eu.kanade.tachiyomi.extension.tr.summertoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class SummerToon : Madara(
    "SummerToon",
    "https://summertoons.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(1, 1)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val chapterUrlSelector = "div + a"
}
