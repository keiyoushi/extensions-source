package eu.kanade.tachiyomi.extension.pt.leitordemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class LeitorDeManga : Madara(
    "Leitor de Mangá",
    "https://leitordemanga.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {
    override val mangaSubString = "ler-manga"

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 2, TimeUnit.SECONDS)
        .addInterceptor(::jsChallengeInterceptor)
        .build()

    override fun imageFromElement(element: Element): String? {
        return super.imageFromElement(element)?.replace("http://", "https://")
    }

    // Linked to src/en/yakshascans
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

        chain.proceed(POST("$baseUrl/hcdn-cgi/jschallenge-validate", headers, body))
            .apply(Response::close)
            .run {
                if (!isSuccessful) {
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

        return if (token != null && token != "nil") {
            token
        } else {
            fetchToken(chain, attempt + 1)
        }
    }

    private fun String.sha256(): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray())
            .fold("", { str, it -> str + "%02x".format(it) })
    }

    companion object {
        private const val MAX_ATTEMPT = 5
        private val TOKEN_REGEX = """cjs[^']+'([^']+)""".toRegex()
    }
}
