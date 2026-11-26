package eu.kanade.tachiyomi.multisrc.inkstory

import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource

/** Answers the site's Anubis check with its proof of work, as the check page's own script does. */
internal class AnubisInterceptor(
    private val baseUrl: HttpUrl,
    private val sourceHeaders: () -> Headers,
) : Interceptor {

    private var passedAt = Instant.DISTANT_PAST

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestedAt = Clock.System.now()
        val response = chain.proceed(request)
        if (request.url.host != baseUrl.host || !response.isAnubis()) return response
        response.close()
        synchronized(this) {
            // Several requests can hit the check at once: pass it once, for all of them.
            if (passedAt < requestedAt) {
                pass(chain)
                passedAt = Clock.System.now()
            }
        }
        val retry = chain.proceed(request)
        if (!retry.isAnubis()) return retry
        retry.close()
        throw IOException(WEBVIEW_MESSAGE)
    }

    // The check is issued to page loads; API calls without its cookie only get refused.
    private fun pass(chain: Interceptor.Chain) {
        val challenge = chain.proceed(document(baseUrl)).asJsoup()
            .selectFirst("script#anubis_challenge")?.data()
            ?.parseAs<AnubisPageDto?>()?.challenge
            ?.takeIf { it.method in PROOF_OF_WORK_METHODS }
            ?: throw IOException(WEBVIEW_MESSAGE)

        val started = TimeSource.Monotonic.markNow()
        val prefix = "0".repeat(challenge.difficulty)
        val (nonce, hash) = generateSequence(0) { it + 1 }
            .map { it to "${challenge.randomData}$it".sha256().toHexString() }
            .first { it.second.startsWith(prefix) }

        val passUrl = baseUrl.newBuilder()
            .encodedPath("/.within.website/x/cmd/anubis/api/pass-challenge")
            .addQueryParameter("id", challenge.id)
            .addQueryParameter("response", hash)
            .addQueryParameter("nonce", nonce.toString())
            .addQueryParameter("redir", baseUrl.toString())
            .addQueryParameter("elapsedTime", started.elapsedNow().inWholeMilliseconds.toString())
            .build()
        chain.proceed(document(passUrl)).close()
    }

    private fun document(url: HttpUrl) = Request.Builder()
        .url(url)
        .headers(sourceHeaders())
        .fetch(Fetch.DOCUMENT)
        .build()

    private fun Response.isAnubis() = header("Content-Type")?.startsWith("text/html") == true &&
        "anubis_version" in peekBody(64 * 1024).string()

    private companion object {
        const val WEBVIEW_MESSAGE = "Пройдите проверку в WebView"

        // The same SHA-256 proof of work, done in the browser by different scripts
        val PROOF_OF_WORK_METHODS = setOf("fast", "slow")
    }
}
