package eu.kanade.tachiyomi.extension.zh.baozimanhua

import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.SerializationException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

// Port of the site's /__gatekeeper_challenge/ browser check (challenge.js + worker.js)
object GatekeeperInterceptor : Interceptor {

    private val lastSolve = HashMap<String, Long>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code != 403) return response

        // Navigations get the challenge page itself, other requests a JSON pointer to it
        val isPage = response.header("x-gatekeeper-challenge") == "required"
        val challengeUrl = if (isPage) null else challengeUrl(response) ?: return response
        response.close()

        synchronized(this) {
            val host = request.url.host
            // Requests challenged while another one solved, or right after a failed solve, retry once
            if (System.currentTimeMillis() - (lastSolve[host] ?: 0L) > SOLVE_INTERVAL_MS) {
                val page = if (challengeUrl != null) {
                    chain.proceed(Request.Builder().url(challengeUrl).headers(request.headers).build()).body.string()
                } else {
                    // Fresh page, bound to the browser cookie the jar holds now
                    val retry = chain.proceed(request)
                    if (retry.header("x-gatekeeper-challenge") != "required") return retry
                    retry.body.string()
                }
                try {
                    solve(chain, request, page)
                } finally {
                    lastSolve[host] = System.currentTimeMillis()
                }
            }
        }
        return chain.proceed(request)
    }

    private fun challengeUrl(response: Response) = try {
        response.takeIf { it.body.contentType()?.subtype == "json" }
            ?.peekBody(Long.MAX_VALUE)?.string()?.parseAs<ChallengeRequired>()
            ?.takeIf { it.error == "challenge_required" }
            ?.challengeUrl?.let(response.request.url::resolve)
    } catch (_: SerializationException) {
        null
    }

    private fun solve(chain: Interceptor.Chain, request: Request, page: String) {
        fun field(regex: Regex) = regex.find(page)?.groupValues?.get(1)
            ?: throw IOException("Unsupported gatekeeper challenge")

        val challengeId = field(CHALLENGE_ID_REGEX)
        val bits = field(DIFFICULTY_BITS_REGEX).toInt()
        // The site's own give-up time for the search
        val computeMs = field(COMPUTE_MS_REGEX).toLong()
        val deadline = if (computeMs > 0) System.currentTimeMillis() + computeMs else Long.MAX_VALUE
        val nonce = generateSequence(0L) { it + 1 }.first {
            if (System.currentTimeMillis() > deadline) throw IOException("Gatekeeper challenge timed out")
            "$PREFIX$challengeId:$it".sha256().leadingZeroBits() >= bits
        }

        // The challenge page is served with no-referrer
        val verifyRequest = Request.Builder()
            .url(request.url.resolve(field(VERIFY_URL_REGEX)) ?: throw IOException("Unsupported gatekeeper challenge"))
            .headers(request.headers)
            .removeHeader("Referer")
            .post(VerifyRequest(challengeId, field(TICKET_REGEX), nonce.toString()).toJsonRequestBody())
            .build()

        chain.proceed(verifyRequest).use { response ->
            val status = try {
                response.takeIf { it.isSuccessful }?.parseAs<VerifyResponse>()?.status
            } catch (_: SerializationException) {
                null
            }
            if (status != "passed") throw IOException("Gatekeeper verification failed")
        }
    }

    private fun ByteArray.leadingZeroBits(): Int {
        val index = indexOfFirst { it != 0.toByte() }
        if (index == -1) return size * 8
        return index * 8 + Integer.numberOfLeadingZeros(this[index].toInt() and 0xFF) - 24
    }

    private const val PREFIX = "gatekeeper-pow-v1:"
    private const val SOLVE_INTERVAL_MS = 30_000L
    private val CHALLENGE_ID_REGEX = Regex("""challengeId:"(\w+)"""")
    private val TICKET_REGEX = Regex("""ticket:"([^"]+)"""")
    private val VERIFY_URL_REGEX = Regex("""verifyUrl:"([^"]+)"""")
    private val DIFFICULTY_BITS_REGEX = Regex("""difficultyBits:(\d+)""")
    private val COMPUTE_MS_REGEX = Regex("""computeMs:(\d+)""")
}
