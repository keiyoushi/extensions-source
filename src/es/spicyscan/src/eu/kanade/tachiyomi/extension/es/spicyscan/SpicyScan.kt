package eu.kanade.tachiyomi.extension.es.spicyscan

import eu.kanade.tachiyomi.multisrc.spicytheme.SpicyTheme
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class SpicyScan : SpicyTheme() {

    override val apiBaseUrl = "https://back.spicyseries.com"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)
}
