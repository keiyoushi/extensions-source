package eu.kanade.tachiyomi.extension.pt.yuriverso

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class YuriOnAir : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
