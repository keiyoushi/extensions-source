package eu.kanade.tachiyomi.extension.es.uchuujinprojects

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class UchuujinProjects : MangaThemesia() {
    override val datePattern = "dd 'de' MMMM 'de' yyyy"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds) { it.host == baseUrl.toHttpUrl().host }

    override val hasProjectPage = true
}
