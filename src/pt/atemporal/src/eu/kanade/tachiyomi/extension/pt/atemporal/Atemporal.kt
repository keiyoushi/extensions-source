package eu.kanade.tachiyomi.extension.pt.atemporal

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class Atemporal : MangaThemesia() {
    override val datePattern = "MMM dd, yyyy"
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)
}
