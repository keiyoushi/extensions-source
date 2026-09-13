package eu.kanade.tachiyomi.extension.en.culturedworks

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Source
abstract class CulturedWorks : MangaThemesia() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(acceptHeaderInterceptor())
        rateLimit(2)
    }

    override val seriesDetailsSelector = ".main-info"
    override val seriesStatusSelector = ".info-right .status, ${super.seriesStatusSelector}"
    override val seriesGenreSelector = ".meta .genres .genre-item"

    override fun imageRequest(page: Page): Request {
        val host = page.imageUrl!!.toHttpUrl().host

        val headers = headersBuilder().apply {
            add("Host", host)
            // This doesn't load on the website, but removing referer seems to fix it
            if (host.contains("kumacdn")) {
                removeAll("Referer")
            }
        }.build()

        return GET(page.imageUrl!!, headers)
    }
}
