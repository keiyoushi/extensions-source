package eu.kanade.tachiyomi.extension.en.setsuscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Interceptor
import okhttp3.Response

class SetsuScans : Madara(
    "Setsu Scans",
    "https://setsuscans.com",
    "en",
) {
    override val client = super.client.newBuilder()
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.host == "i0.wp.com") {
                val newUrl = url.newBuilder()
                    .removeAllQueryParameters("fit")
                    .build()

                return@addNetworkInterceptor chain.proceed(
                    request.newBuilder()
                        .url(newUrl)
                        .build(),
                )
            }

            return@addNetworkInterceptor chain.proceed(request)
        }
        .addInterceptor(::handleFailedImages)
        .rateLimit(2)
        .build()

    private fun handleFailedImages(chain: Interceptor.Chain): Response {
        val response: Response = chain.proceed(chain.request())
        val url = response.request.url
        if (url.host == "i0.wp.com" && response.code == 404) {
            val ssl = response.request.url.queryParameter("ssl")
            var newUrl = url.newBuilder()
                .removeAllQueryParameters("ssl")

            if (ssl.isNullOrBlank()) {
                newUrl = newUrl.addQueryParameter("ssl", "0")
            } else if (ssl.toInt() >= 5) {
                return response
            } else if (ssl.toInt() == 0) {
                newUrl = newUrl.addQueryParameter("ssl", "2")
            } else if (ssl.toInt() >= 2) {
                newUrl = newUrl.addQueryParameter("ssl", (ssl.toInt() + 1).toString())
            }

            val newRequest = chain.request().newBuilder()
                .url(newUrl.build())
                .build()
            return client.newCall(newRequest).execute()
        }
        return response
    }

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(status) + div.summary-content"
}
