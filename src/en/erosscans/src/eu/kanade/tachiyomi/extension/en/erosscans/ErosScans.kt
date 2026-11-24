package eu.kanade.tachiyomi.extension.en.erosscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class ErosScans : MangaThemesia() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)
}
