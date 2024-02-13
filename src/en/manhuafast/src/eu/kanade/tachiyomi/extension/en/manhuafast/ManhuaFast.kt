package eu.kanade.tachiyomi.extension.en.manhuafast

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ManhuaFast : Madara("ManhuaFast", "https://manhuafast.com", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4, TimeUnit.SECONDS)
        .build()

    // The website does not flag the content.
    override val filterNonMangaItems = false

    override fun popularMangaRequest(page: Int): Request {
        return GET(
            url = "$baseUrl/${searchPage(page)}?s&post_type=wp-manga&m_orderby=views",
            headers = headers,
            cache = CacheControl.FORCE_NETWORK,
        )
    }

    override fun popularMangaSelector() = searchMangaSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(
            url = "$baseUrl/${searchPage(page)}?s&post_type=wp-manga&m_orderby=latest",
            headers = headers,
            cache = CacheControl.FORCE_NETWORK,
        )
    }
}
