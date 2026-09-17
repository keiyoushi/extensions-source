package eu.kanade.tachiyomi.extension.es.asialotus

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class AsiaLotus : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)
}
