package eu.kanade.tachiyomi.extension.ja.rawfree

import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

class DiscoveryTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setupJson() {
            Injekt.addSingleton(Json { ignoreUnknownKeys = true })
        }
    }

    private val validHtml = """
        <html><head><link rel="canonical" href="https://rawfree.loan/"></head><body>
        <script>const zing = {"ajax_url":"https://rawfree.loan/wp-admin/admin-ajax.php","nonce":"verified-nonce"}</script>
        <div class="entry-ma"><h2 class="ma-name"><a href="/manga-raw/title/">Title</a></h2></div>
        </body></html>
    """.trimIndent()

    @Test
    fun landingPageReturnsOnlyItsFinalOrigin() {
        assertEquals("https://rawfree.loan", discoveredOrigin(Jsoup.parse(validHtml, "https://rawfree.loan/?from=hub")))
    }

    @Test
    fun aParkedLandingPageIsRejected() {
        assertThrows(Exception::class.java) { discoveredOrigin(Jsoup.parse("<h1>Domain for sale</h1>", "https://parking.example/")) }
    }

    @Test
    fun configurationWithoutRawFreeListingStructureIsRejected() {
        assertThrows(Exception::class.java) { discoveredOrigin(Jsoup.parse(validHtml.substringBefore("<div class="), "https://rawfree.loan/")) }
    }

    @Test
    fun crossOriginConfigurationIsRejected() {
        assertThrows(Exception::class.java) { discoveredOrigin(Jsoup.parse(validHtml, "https://parking.example/")) }
    }

    @Test
    fun insecureLandingPageIsRejected() {
        assertThrows(Exception::class.java) { discoveredOrigin(Jsoup.parse(validHtml.replace("https://", "http://"), "http://rawfree.loan/")) }
    }
}
