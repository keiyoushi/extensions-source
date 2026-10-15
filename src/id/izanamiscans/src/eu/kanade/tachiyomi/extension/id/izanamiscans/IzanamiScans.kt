package eu.kanade.tachiyomi.extension.id.izanamiscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class IzanamiScans : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val seriesAuthorSelector = ".fmed b:contains(Penulis) + span"
}
