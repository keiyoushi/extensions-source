package eu.kanade.tachiyomi.extension.id.manhwalistid

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class ManhwaList : MangaThemesia() {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
