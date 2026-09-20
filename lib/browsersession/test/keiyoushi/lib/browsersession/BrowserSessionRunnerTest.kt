package keiyoushi.lib.browsersession

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BrowserSessionRunnerTest {

    private val runner = BrowserSessionRunner()

    @Test
    fun domainKeyResolvesSubdomainsToTopPrivateDomain() {
        val mangaUrl = "https://manga.example.com/read/1".toHttpUrl()
        val cdnUrl = "https://cdn.example.com/images/1.jpg".toHttpUrl()
        val apexUrl = "https://example.com/".toHttpUrl()

        val mangaKey = runner.domainKey(mangaUrl)
        val cdnKey = runner.domainKey(cdnUrl)
        val apexKey = runner.domainKey(apexUrl)

        assertEquals("example.com", mangaKey)
        assertEquals("example.com", cdnKey)
        assertEquals("example.com", apexKey)
    }

    @Test
    fun domainKeyHandlesCompoundTlds() {
        val url = "https://reader.mangasite.co.uk/chapter/1".toHttpUrl()
        val key = runner.domainKey(url)
        assertEquals("mangasite.co.uk", key)
    }

    @Test
    fun domainKeyHandlesIpAndLocalhost() {
        val ipUrl = "http://192.168.1.50:8080/".toHttpUrl()
        assertEquals("192.168.1.50", runner.domainKey(ipUrl))

        val localUrl = "http://localhost:3000/".toHttpUrl()
        assertEquals("localhost", runner.domainKey(localUrl))
    }

    @Test
    fun checkInteractiveJsExcludesFalsePositives() {
        val js = BrowserSessionRunner.CHECK_INTERACTIVE_JS

        // Must NOT falsely trigger on standard Turnstile response hidden input
        assertFalse(js.contains("input[name=\"cf-turnstile-response\"]"))

        // Must NOT falsely trigger on any iframe inside challenge-stage
        assertFalse(js.contains("#challenge-stage iframe"))

        // Must check for explicit interactive mode or interactive Turnstile endpoints
        assertTrue(js.contains("/interactive/"))
        assertTrue(js.contains("mode=interactive"))
    }

    @Test
    fun solveResultContract() {
        assertTrue(SolveResult.Success.isSuccess)
        assertTrue(SolveResult.AlreadySolved.isSuccess)

        val failed = SolveResult.Failed(IOException("Network error"))
        assertFalse(failed.isSuccess)
        assertEquals("Network error", failed.error.message)

        val failed2 = SolveResult.Failed(failed.error)
        assertEquals(failed, failed2)
        assertEquals(failed.hashCode(), failed2.hashCode())
    }

    @Test
    fun interactiveChallengeExceptionMessage() {
        val defaultEx = InteractiveChallengeException()
        assertTrue(defaultEx.message!!.contains("Interactive challenge detected"))

        val customEx = InteractiveChallengeException("Custom puzzle required")
        assertEquals("Custom puzzle required", customEx.message)
    }
}
