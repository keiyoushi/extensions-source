package keiyoushi.lib.browsersession

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieBridgeTest {

    @Test
    fun scopedDomainsForApexDomain() {
        val url = "https://example.com/".toHttpUrl()
        val domains = CookieBridge.getScopedDomains(url)

        assertEquals(listOf("example.com", ".example.com"), domains)
    }

    @Test
    fun scopedDomainsForSingleSubdomain() {
        val url = "https://sub.example.com/manga".toHttpUrl()
        val domains = CookieBridge.getScopedDomains(url)

        val expected = listOf(
            "sub.example.com",
            ".sub.example.com",
            "example.com",
            ".example.com",
        )
        assertEquals(expected, domains)
    }

    @Test
    fun scopedDomainsForMultiLevelSubdomain() {
        val url = "https://manga.cdn.example.com/chapter/1".toHttpUrl()
        val domains = CookieBridge.getScopedDomains(url)

        val expected = listOf(
            "manga.cdn.example.com",
            ".manga.cdn.example.com",
            "cdn.example.com",
            ".cdn.example.com",
            "example.com",
            ".example.com",
        )
        assertEquals(expected, domains)
    }

    @Test
    fun scopedDomainsForMultiPartPublicSuffix() {
        val url = "https://reader.mangasite.co.uk/".toHttpUrl()
        val domains = CookieBridge.getScopedDomains(url)

        assertTrue(domains.contains("reader.mangasite.co.uk"))
        assertTrue(domains.contains(".reader.mangasite.co.uk"))
        assertTrue(domains.contains("mangasite.co.uk"))
        assertTrue(domains.contains(".mangasite.co.uk"))
    }

    @Test
    fun scopedDomainsForIpAddress() {
        val url = "http://192.168.1.100:8080/".toHttpUrl()
        val domains = CookieBridge.getScopedDomains(url)

        assertEquals(listOf("192.168.1.100", ".192.168.1.100"), domains)
    }

    @Test
    fun scopedDomainsForLocalhost() {
        val url = "http://localhost:3000/".toHttpUrl()
        val domains = CookieBridge.getScopedDomains(url)

        assertEquals(listOf("localhost", ".localhost"), domains)
    }

    @Test
    fun parseCookieHeaderSetsRootPathAndMatchesSubsequentPaths() {
        val url = "https://example.com/manga/chapter/1".toHttpUrl()
        val rawHeader = "cf_clearance=secretValue123; __cf_bm=token456"
        val cookies = CookieBridge.parseCookieHeader(url, rawHeader)

        assertEquals(2, cookies.size)

        val clearance = cookies.first { it.name == "cf_clearance" }
        assertEquals("secretValue123", clearance.value)
        assertEquals("/", clearance.path)
        assertEquals("example.com", clearance.domain)

        // Must match other paths on the site
        assertTrue(clearance.matches("https://example.com/".toHttpUrl()))
        assertTrue(clearance.matches("https://example.com/images/page1.jpg".toHttpUrl()))
        assertTrue(clearance.matches("https://example.com/manga/chapter/2".toHttpUrl()))
    }

    @Test
    fun parseCookieHeaderHandlesIpAddressAndLocalhost() {
        val ipUrl = "http://192.168.1.100:8080/path".toHttpUrl()
        val ipCookies = CookieBridge.parseCookieHeader(ipUrl, "session=ipUser")
        assertEquals(1, ipCookies.size)
        assertEquals("session", ipCookies[0].name)
        assertEquals("ipUser", ipCookies[0].value)
        assertEquals("192.168.1.100", ipCookies[0].domain)
        assertTrue(ipCookies[0].hostOnly)

        val localhostUrl = "http://localhost:3000/".toHttpUrl()
        val localCookies = CookieBridge.parseCookieHeader(localhostUrl, "local_token=123")
        assertEquals(1, localCookies.size)
        assertEquals("local_token", localCookies[0].name)
        assertEquals("localhost", localCookies[0].domain)
    }

    @Test
    fun parseCookieHeaderIgnoresMalformedTokens() {
        val url = "https://example.com/".toHttpUrl()
        val rawHeader = "valid=1; ; =missingName; noEquals; valid2=2;"
        val cookies = CookieBridge.parseCookieHeader(url, rawHeader)

        assertEquals(2, cookies.size)
        assertEquals("valid", cookies[0].name)
        assertEquals("1", cookies[0].value)
        assertEquals("valid2", cookies[1].name)
        assertEquals("2", cookies[1].value)
    }

    @Test
    fun fallbackTopPrivateDomainForExpandedCompoundTlds() {
        val domain = CookieBridge.computeFallbackTopPrivateDomain("manga.site.co.id")
        assertEquals("site.co.id", domain)

        val arDomain = CookieBridge.computeFallbackTopPrivateDomain("reader.comics.com.ar")
        assertEquals("comics.com.ar", arDomain)
    }
}
