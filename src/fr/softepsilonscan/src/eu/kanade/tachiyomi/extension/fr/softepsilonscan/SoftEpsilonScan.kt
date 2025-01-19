package eu.kanade.tachiyomi.extension.fr.softepsilonscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class SoftEpsilonScan : Madara(
    "Soft Epsilon Scan",
    "https://epsilonsoft.to",
    "fr",
    SimpleDateFormat("dd/MM/yy", Locale.FRENCH),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true
}
