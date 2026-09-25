package eu.kanade.tachiyomi.extension.tr.summertoon

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class SummerToon : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.forLanguageTag("tr"))

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 1.seconds)

    override val chapterUrlSelector = "div + a"
}
