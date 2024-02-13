package eu.kanade.tachiyomi.extension.fr.frdashscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class FRScan : Madara("FR-Scan", "https://fr-scan.com", "fr", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRANCE)) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    override val chapterUrlSuffix = ""
}
