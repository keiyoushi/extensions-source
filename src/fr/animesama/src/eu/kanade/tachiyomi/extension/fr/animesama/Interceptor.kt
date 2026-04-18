package eu.kanade.tachiyomi.extension.fr.animesama

import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

class Interceptor(private val client: OkHttpClient, private val baseUrl: String, private val headers: Headers) : Interceptor {

    @Volatile
    private var _baseUrl: String? = null
    private val lock = Any()

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val host = request.url.host
        if (host.contains("anime-sama.")) {
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
        val requestToFetchDomains = GET(baseUrl, headers)

        val domainsResponse = client.newCall(requestToFetchDomains).execute()
        val domainsBody = domainsResponse.body.string()

        // Find domain list in <script>
        val extractedDomains = DOMAIN_REGEX.findAll(domainsBody)
            .map { it.groups[1]?.value }
            .filterNotNull()
        for (domain in extractedDomains) {
            val res = client.newCall(GET("https://anime-sama.pw/?check=$domain", headers)).execute()
            val data = res.parseAs<CheckResponse>()

            if (data.code == 200) {
                return "https://$domain"
            }
        }

        throw UnsupportedOperationException("Unable to retrieve the most recent URL for anime-sama.")
    }

    fun getBaseUrl(): String? = _baseUrl

    companion object {
        private val DOMAIN_REGEX = Regex("'([^']+\\.[a-z]{2,})'")
    }
}
