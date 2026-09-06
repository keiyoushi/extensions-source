package eu.kanade.tachiyomi.extension.pt.origamiorpheans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class OrigamiOrpheans : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)

    override val altNamePrefix = "Nomes alternativos: "
}
