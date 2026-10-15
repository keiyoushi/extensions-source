package eu.kanade.tachiyomi.extension.ar.despairmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import okhttp3.OkHttpClient

@Source
abstract class DespairManga : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = addInterceptor(acceptHeaderInterceptor())
}
