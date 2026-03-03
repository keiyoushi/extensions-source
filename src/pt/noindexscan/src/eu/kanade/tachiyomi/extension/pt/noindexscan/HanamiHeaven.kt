package eu.kanade.tachiyomi.extension.pt.noindexscan

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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class HanamiHeaven :
    Madara(
        "Hanami Heaven",
        "https://hanamiheaven.org",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {
    // NoIndexScan (pt-BR) -> HanamiHeaven (pt-BR)
    override val id = 987786689720213769L

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 2, TimeUnit.SECONDS)
        .addInterceptor(::jsChallengeInterceptor)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorTitle = "div[class*='post-title'] h1"
    override val mangaDetailsSelectorStatus = "div.summary-heading:has(h5:contains(Status)) + div.summary-content"

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
                    throw IOException("Falha ao bypassar o desafio JS!")
                }
            }
        return chain.proceed(chain.request())
    }

    private tailrec fun fetchToken(chain: Interceptor.Chain, attempt: Int = 0): String {
        if (attempt > MAX_ATTEMPT) {
            throw IOException("Falha ao buscar o token do desafio JS!")
        }

        val response = chain.proceed(GET("$baseUrl/hcdn-cgi/jschallenge", headers))
        val token = TOKEN_REGEX.find(response.body.string())?.groups?.get(1)?.value

        return if (token != null && token != "nil") {
            token
        } else {
            fetchToken(chain, attempt + 1)
        }
    }

    private fun String.sha256(): String = MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray())
        .fold("", { str, it -> str + "%02x".format(it) })

    companion object {
        private const val MAX_ATTEMPT = 5
        private val TOKEN_REGEX = """cjs[^']+'([^']+)""".toRegex()
    }
}
