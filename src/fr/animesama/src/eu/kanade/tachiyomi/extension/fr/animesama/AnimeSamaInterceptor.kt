package eu.kanade.tachiyomi.extension.fr.animesama

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

class AnimeSamaInterceptor(private val client: OkHttpClient, private val baseUrl: String, private val headers: Headers) : Interceptor {

    @Volatile
    private var _baseUrl: String? = null
    private val lock = Any()

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if ("${request.url.scheme}://${request.url.host}" == baseUrl) {
            if (_baseUrl == null) {
                synchronized(lock) {
                    if (_baseUrl == null) {
                        val url = fetchAnimeSamaURL()
                        _baseUrl = url
                    }
                }
            }
            val oldUrl = request.url
            val newUrl = oldUrl.newBuilder()
                .scheme("https")
                .host(_baseUrl!!.toHttpUrl().host)
                .build()
            request = request.newBuilder()
                .url(newUrl)
                .build()
        }

        return chain.proceed(request)
    }

    private fun fetchAnimeSamaURL(): String {
        val domainListUrl = baseUrl.toHttpUrl()
        val requestToFetchDomains = GET(domainListUrl, headers)

        val domainsResponse = client.newCall(requestToFetchDomains).execute()
        val domainsDocument = domainsResponse.asJsoup()
        val button = domainsDocument.getElementsByClass("btn-primary").first()

        if (button == null) throw UnsupportedOperationException("Unable to retrieve the most recent URL for anime-sama.")

        val url = button.absUrl("href")
        return url
    }
}
