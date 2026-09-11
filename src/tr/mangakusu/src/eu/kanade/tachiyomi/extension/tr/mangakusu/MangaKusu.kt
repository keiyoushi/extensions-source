package eu.kanade.tachiyomi.extension.tr.mangakusu

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class MangaKusu : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)
}
