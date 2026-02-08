package eu.kanade.tachiyomi.extension.fr.animesama

import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
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

    @Serializable
    class CheckResponse(
        val code: Int,
    )

    private fun fetchAnimeSamaURL(): String {
        val requestToFetchDomains = GET(baseUrl, headers)

        val domainsResponse = client.newCall(requestToFetchDomains).execute()
        val domainsBody = domainsResponse.body.string()
        val domainRegex = Regex("'([^']+\\.[a-z]{2,})'")

        // Find domain list in <script>
        val extractedDomains = domainRegex.findAll(domainsBody)
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
}
