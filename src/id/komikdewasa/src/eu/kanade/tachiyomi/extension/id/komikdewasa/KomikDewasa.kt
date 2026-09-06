package eu.kanade.tachiyomi.extension.id.komikdewasa

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import okhttp3.OkHttpClient

@Source
abstract class KomikDewasa : MangaThemesia() {
    override val mangaUrlDirectory = "/komik"
    override val datePattern = "d MMMM yyyy"
    override val hasProjectPage = true

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(acceptHeaderInterceptor())
}
