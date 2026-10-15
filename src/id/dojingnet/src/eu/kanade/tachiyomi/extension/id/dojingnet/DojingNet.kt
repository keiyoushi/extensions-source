package eu.kanade.tachiyomi.extension.id.dojingnet

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class DojingNet : MangaThemesia() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(4)

    override val hasProjectPage = true
}
