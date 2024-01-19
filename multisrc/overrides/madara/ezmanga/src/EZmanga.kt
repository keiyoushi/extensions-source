package eu.kanade.tachiyomi.extension.en.ezmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class EZmanga : Madara(
    "EZmanga",
    "https://ezmanga.net",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH),
) {
    override val useNewChapterEndpoint = true

    override val client = super.client.newBuilder()
        .rateLimit(1)
        .build()
}
