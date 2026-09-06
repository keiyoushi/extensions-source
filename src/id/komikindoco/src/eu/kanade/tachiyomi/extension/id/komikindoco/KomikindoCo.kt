package eu.kanade.tachiyomi.extension.id.komikindoco

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class KomikindoCo : MangaThemesia() {
    override val datePattern = "MMM dd, yyyy"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(4)

    override val hasProjectPage = true

    override val seriesDetailsSelector = ".seriestucon"
}
