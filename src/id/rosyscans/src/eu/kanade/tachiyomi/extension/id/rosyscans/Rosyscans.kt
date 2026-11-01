package eu.kanade.tachiyomi.extension.id.rosyscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class Rosyscans : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val hasProjectPage = true
}
