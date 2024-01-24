package eu.kanade.tachiyomi.extension.en.ascalonscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest

class AscalonScans : MangaThemesia("AscalonScans", "https://ascalonscans.com", "en") {
    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .addInterceptor(::jsChallengeInterceptor)
        .build()

    private fun jsChallengeInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val origRes = chain.proceed(request)
        if (origRes.code != 403) return origRes
        origRes.close()

        // Same delay as the source
        Thread.sleep(3000L)
        val token = fetchToken(chain).sha256()

        val body = FormBody.Builder().add("challenge", token).build()
        val challengeReq = POST("$baseUrl/hcdn-cgi/jschallenge-validate", headers, body = body)

        val challengeResponse = chain.proceed(challengeReq)
        challengeResponse.close()
        if (challengeResponse.code != 200) throw IOException("Failed to bypass js challenge!")

        return chain.proceed(request)
    }

    private tailrec fun fetchToken(chain: Interceptor.Chain, attempt: Int = 0): String {
        if (attempt > 5) throw IOException("Failed to fetch challenge token!")
        val request = GET("$baseUrl/hcdn-cgi/jschallenge", headers)
        val res = chain.proceed(request).body.string()

        return res.substringAfter("cjs = '").substringBefore("'")
            .takeUnless { it == "nil" } ?: fetchToken(chain, attempt + 1)
    }

    private fun String.sha256(): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray())
            .fold("", { str, it -> str + "%02x".format(it) })
    }
}
