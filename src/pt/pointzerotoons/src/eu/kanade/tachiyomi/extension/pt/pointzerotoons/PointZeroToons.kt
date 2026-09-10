package eu.kanade.tachiyomi.extension.pt.pointzerotoons

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class PointZeroToons : MangaThemesia() {
    override val datePattern = "dd.MM.yyyy"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val seriesThumbnailSelector = ".tx-hero-cover > img.wp-post-image"
}
