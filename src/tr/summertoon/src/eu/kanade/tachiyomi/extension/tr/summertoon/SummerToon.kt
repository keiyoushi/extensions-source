package eu.kanade.tachiyomi.extension.tr.summertoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class SummerToon : Madara(
    "SummerToon",
    "https://summertoons.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(1, 1.seconds)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val chapterUrlSelector = "div + a"
}
