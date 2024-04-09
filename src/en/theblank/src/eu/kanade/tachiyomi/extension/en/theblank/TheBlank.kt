package eu.kanade.tachiyomi.extension.en.theblank

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class TheBlank : Madara(
    "The Blank Scanlation",
    "https://theblank.net",
    "en",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US),
) {

    override val client = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.vip-permission)"
}
