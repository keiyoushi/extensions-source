package eu.kanade.tachiyomi.extension.id.manhwalandmom

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class ManhwaLandMom : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(4)
}
