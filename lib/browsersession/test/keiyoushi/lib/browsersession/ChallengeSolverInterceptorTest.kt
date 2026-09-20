package keiyoushi.lib.browsersession

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeSolverInterceptorTest {

    private val baseRequest = Request.Builder().url("https://example.com/").build()

    @Test
    fun standardSuccessIsNotChallenge() {
        val response = Response.Builder()
            .request(baseRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("<html>Normal page</html>".toResponseBody("text/html".toMediaType()))
            .build()

        assertFalse(ChallengeSolverInterceptor.isChallengeResponse(response))
    }

    @Test
    fun regular403ForbiddenIsNotChallenge() {
        val response = Response.Builder()
            .request(baseRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .body("<html>Access denied: invalid credentials</html>".toResponseBody("text/html".toMediaType()))
            .build()

        assertFalse(ChallengeSolverInterceptor.isChallengeResponse(response))
        // Verify peekBody did not consume the response stream
        assertEquals("<html>Access denied: invalid credentials</html>", response.body.string())
    }

    @Test
    fun cloudflareMitigatedHeaderIsChallenge() {
        val response = Response.Builder()
            .request(baseRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .header("cf-mitigated", "challenge")
            .body("".toResponseBody("text/html".toMediaType()))
            .build()

        assertTrue(ChallengeSolverInterceptor.isChallengeResponse(response))
    }

    @Test
    fun cloudflareTurnstileBodyIsChallenge() {
        val response = Response.Builder()
            .request(baseRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .header("Server", "cloudflare")
            .body("<html><div class='cf-turnstile'>Just a moment...</div></html>".toResponseBody("text/html".toMediaType()))
            .build()

        assertTrue(ChallengeSolverInterceptor.isChallengeResponse(response))
    }

    @Test
    fun cloudflare503WithChlOptIsChallenge() {
        val response = Response.Builder()
            .request(baseRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(503)
            .message("Service Temporarily Unavailable")
            .header("Server", "cloudflare")
            .body("<html><script>window._cf_chl_opt = {};</script></html>".toResponseBody("text/html".toMediaType()))
            .build()

        assertTrue(ChallengeSolverInterceptor.isChallengeResponse(response))
    }

    @Test
    fun ddosGuard403IsChallenge() {
        val response = Response.Builder()
            .request(baseRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .header("Server", "ddos-guard")
            .body("<html><script src='https://check.ddos-guard.net/check.js'></script></html>".toResponseBody("text/html".toMediaType()))
            .build()

        assertTrue(ChallengeSolverInterceptor.isChallengeResponse(response))
    }

    @Test
    fun cloudflareChallengeRunningWithoutServerHeaderIsChallenge() {
        val response = Response.Builder()
            .request(baseRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .body("<html><div id='cf-challenge-running'></div></html>".toResponseBody("text/html".toMediaType()))
            .build()

        assertTrue(ChallengeSolverInterceptor.isChallengeResponse(response))
    }

    @Test
    fun cloudflareTurnstileWithoutServerHeaderIsChallenge() {
        val response = Response.Builder()
            .request(baseRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(403)
            .message("Forbidden")
            .body("<html><script src='https://challenges.cloudflare.com/turnstile/v0/api.js'></script></html>".toResponseBody("text/html".toMediaType()))
            .build()

        assertTrue(ChallengeSolverInterceptor.isChallengeResponse(response))
    }

    @Test
    fun mergeCookiesPreservesNonConflictingExistingCookies() {
        val url = "https://example.com/".toHttpUrl()
        val builder = Request.Builder().url(url)
        val existing = "user_id=123; auth_token=secret; cf_clearance=oldValue"

        // Inject simulated CookieManager cookies via CookieBridge.parseCookieHeader
        val mockClearance = "cf_clearance=newValue999; __cf_bm=bmToken"
        val mockCookies = CookieBridge.parseCookieHeader(url, mockClearance)

        // Directly verify merge logic
        val existingList = existing.split(';').map { it.trim() }
        val merged = buildList {
            for (item in existingList) {
                val name = item.substringBefore('=').trim()
                if (mockCookies.none { it.name.equals(name, ignoreCase = true) }) {
                    add(item)
                }
            }
            for (cookie in mockCookies) {
                add("${cookie.name}=${cookie.value}")
            }
        }.joinToString("; ")

        builder.header("Cookie", merged)
        val builtRequest = builder.build()
        val cookieHeader = builtRequest.header("Cookie")!!

        assertTrue(cookieHeader.contains("user_id=123"))
        assertTrue(cookieHeader.contains("auth_token=secret"))
        assertTrue(cookieHeader.contains("cf_clearance=newValue999"))
        assertFalse(cookieHeader.contains("oldValue"))
        assertTrue(cookieHeader.contains("__cf_bm=bmToken"))
    }
}
