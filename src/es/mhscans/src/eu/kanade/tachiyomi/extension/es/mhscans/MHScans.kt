package eu.kanade.tachiyomi.extension.es.mhscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class MHScans : Madara(
    "MHScans",
    "https://twobluescans.com",
    "es",
    dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 3.seconds)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
