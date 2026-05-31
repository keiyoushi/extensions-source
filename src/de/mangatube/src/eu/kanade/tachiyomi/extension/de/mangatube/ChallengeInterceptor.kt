package eu.kanade.tachiyomi.extension.de.mangatube

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import keiyoushi.utils.parseAs
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

class ChallengeInterceptor(
    private val baseUrl: String,
    private val headers: Headers,
    private val client: OkHttpClient,
    private val cookieJar: CookieJar,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.header(CHALLENGE_BYPASSED_HEADER) == "1") {
            return chain.proceed(originalRequest)
        }

        if (originalRequest.url.encodedPath.startsWith("/api/") && needsBootstrap(originalRequest.url)) {
            bootstrapSession()
        }

        val response = chain.proceed(originalRequest)
        val bodyString = response.peekBody(Long.MAX_VALUE).string()
        if (!isChallengePage(bodyString)) {
            return response
        }

        response.close()
        solveChallenge(bodyString)

        return chain.proceed(
            originalRequest.newBuilder()
                .header(CHALLENGE_BYPASSED_HEADER, "1")
                .build(),
        )
    }

    private fun isChallengePage(body: String): Boolean = body.contains("window.__challange") || body.contains("_challange =")

    private fun needsBootstrap(url: HttpUrl): Boolean {
        val cookieNames = cookieJar.loadForRequest(url).map { it.name }.toSet()
        return REQUIRED_API_COOKIES.any { it !in cookieNames }
    }

    private fun bootstrapSession() {
        val response = client.newCall(GET(baseUrl, headers)).execute()
        val bodyString = response.peekBody(Long.MAX_VALUE).string()
        if (isChallengePage(bodyString)) {
            response.close()
            solveChallenge(bodyString)
            client.newCall(GET(baseUrl, headers)).execute().close()
            return
        }
        response.close()
    }

    private fun solveChallenge(body: String) {
        val challengeStr = CHALLENGE_REGEX.find(body)?.groupValues?.getOrNull(1)
            ?: throw IOException("Challenge payload not found")
        val challenge = challengeStr.parseAs<Challenge>()
        val solution = solve(challenge.arg1, challenge.arg2, challenge.arg3)
        val challengeHeaders = headers.newBuilder()
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("x-challange-token", challenge.tk)
            .add("x-challange-arg1", challenge.arg1)
            .add("x-challange-arg2", challenge.arg2)
            .add("x-challange-arg3", challenge.arg3)
            .add("x-challange-arg4", solution)
            .build()

        Thread.sleep(CHALLENGE_DELAY_MS)
        client.newCall(POST("$baseUrl/", challengeHeaders))
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Challenge validation failed: ${response.code}")
                }
            }
    }

    private fun solve(arg1: String, arg2: String, op: String): String {
        val left = arg1.toLong(16).toDouble()
        val right = arg2.toLong(16).toDouble()

        val result = when (op) {
            "a" -> left / right
            "b" -> left * right
            "c" -> left - right
            "d" -> left + right
            else -> throw IOException("Unknown challenge op: $op")
        }

        return if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            result.toString()
        }
    }

    companion object {
        private val CHALLENGE_REGEX = """_challange = (.+?);""".toRegex()
        private val REQUIRED_API_COOKIES = setOf("XSRF-TOKEN", "manga_tube_beta_session", "__mtbpass")
        private const val CHALLENGE_BYPASSED_HEADER = "X-MangaTube-Challenge-Bypassed"
        private const val CHALLENGE_DELAY_MS = 1_000L
    }
}
