package eu.kanade.tachiyomi.extension.id.komikav

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class Apkomik : MangaThemesia() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(acceptHeaderInterceptor())
        rateLimit(4)
    }

    override val hasProjectPage = true
}
