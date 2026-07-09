package eu.kanade.tachiyomi.extension.all.seraphicdeviltry

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class SeraphicDeviltry : Madara() {
    override val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 1.seconds)
        .build()
}
