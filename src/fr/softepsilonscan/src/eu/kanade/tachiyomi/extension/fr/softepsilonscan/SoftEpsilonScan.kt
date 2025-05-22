package eu.kanade.tachiyomi.extension.fr.softepsilonscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class SoftEpsilonScan : Madara(
    "Soft Epsilon Scan",
    "https://epsilonsoft.to",
    "fr",
    SimpleDateFormat("dd/MM/yy", Locale.FRENCH),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useNewChapterEndpoint = true
}
