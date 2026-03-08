package eu.kanade.tachiyomi.extension.en.qiscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.concurrent.TimeUnit

class QiScans :
    Iken(
        "Qi Scans",
        "en",
        "https://qimanhwa.com",
        "https://api.qimanhwa.com",
    ) {

    override val client = super.client.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/api/query".toHttpUrl().newBuilder().apply {
            // 'query' instead of 'posts'
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", Iken.PER_PAGE.toString())
            addQueryParameter("orderBy", "updatedAt")
        }.build()
        return GET(url, headers)
    }
}
