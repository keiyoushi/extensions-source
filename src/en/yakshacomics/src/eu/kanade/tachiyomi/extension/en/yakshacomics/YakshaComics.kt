package eu.kanade.tachiyomi.extension.en.yakshacomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest

class YakshaComics :
    Madara(
        "YakshaComics",
        "https://yakshacomics.com",
        "en",
    ) {
    override val useNewChapterEndpoint = true

    // Adapted from src/en/yakshascans
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .addInterceptor(::jsChallengeInterceptor)
        .build()

    private fun jsChallengeInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != 403) {
            return response
        }
        response.close()

        Thread.sleep(3000L)
        val token = fetchToken(chain).sha256()
        val body = FormBody.Builder()
            .add("challenge", token)
            .build()

        val validateResponse = chain.proceed(POST("$baseUrl/hcdn-cgi/jschallenge-validate", headers, body))
        validateResponse.use {
            if (!it.isSuccessful) {
                throw IOException("Failed to bypass js challenge!")
            }
        }
        return chain.proceed(chain.request())
    }

    private tailrec fun fetchToken(chain: Interceptor.Chain, attempt: Int = 0): String {
        if (attempt > MAX_ATTEMPT) {
            throw IOException("Failed to fetch challenge token!")
        }

        val response = chain.proceed(GET("$baseUrl/hcdn-cgi/jschallenge", headers))
        val token = TOKEN_REGEX.find(response.body.string())?.groups?.get(1)?.value

        return if (!token.isNullOrEmpty() && token != "nil") {
            token
        } else {
            fetchToken(chain, attempt + 1)
        }
    }

    private fun String.sha256(): String = MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())
        .fold("", { str, it -> str + "%02x".format(it) })

    override val mangaDetailsSelectorDescription: String =
        "div.description-summary div.summary__content h3 + p, div.description-summary div.summary__content:not(:has(h3)), div.summary_content div.post-content_item > h5 + div, div.summary_content div.manga-excerpt"

    companion object {
        private const val MAX_ATTEMPT = 5
        private val TOKEN_REGEX = """cjs[^']+'([^']+)""".toRegex()
    }
}
