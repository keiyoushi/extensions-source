package eu.kanade.tachiyomi.extension.es.richtoscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class RichtoScan : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ROOT)

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2, 1.seconds)
    }

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)"
}
