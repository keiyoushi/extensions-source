package eu.kanade.tachiyomi.extension.pt.pirulitorosa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class PirulitoRosa : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyy", Locale("pt", "BR"))

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val popularMangaUrlSelector = "div.post-title a:last-child"
}
