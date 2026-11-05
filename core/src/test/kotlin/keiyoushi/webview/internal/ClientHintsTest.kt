package keiyoushi.webview.internal

import androidx.webkit.UserAgentMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientHintsTest {

    private fun brand(name: String, major: String, full: String) = UserAgentMetadata.BrandVersion.Builder()
        .setBrand(name).setMajorVersion(major).setFullVersion(full).build()

    private val engine = UserAgentMetadata.Builder()
        .setBrandVersionList(listOf(brand("Not:A-Brand", "8", "8.0.0.0"), brand("Chromium", "156", "156.0.8078.47"), brand("Android WebView", "156", "156.0.8078.47")))
        .setFullVersion("156.0.8078.47")
        .setPlatform("Android")
        .setPlatformVersion("11.0.0")
        .setArchitecture("")
        .setModel("ONEPLUS A6003")
        .setMobile(true)
        .build()

    @Test
    fun reducedChromeUaKeepsEngineVersions() {
        val hints = clientHintsFor("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Mobile Safari/537.36", engine)!!
        assertEquals(listOf("Not:A-Brand", "Chromium", "Google Chrome"), hints.brandVersionList.map { it.brand })
        assertEquals("156.0.8078.47", hints.fullVersion)
        assertEquals("156.0.8078.47", hints.brandVersionList[2].fullVersion)
        assertEquals("8", hints.brandVersionList[0].majorVersion)
        assertEquals("11.0.0", hints.platformVersion)
        assertEquals("Android", hints.platform)
        assertTrue(hints.isMobile)
        assertEquals("ONEPLUS A6003", hints.model)
    }

    @Test
    fun otherChromeVersionUsesTheUaVersion() {
        val hints = clientHintsFor("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.7000.1 Safari/537.36", engine)!!
        assertEquals("150.0.7000.1", hints.fullVersion)
        assertEquals("150", hints.brandVersionList[1].majorVersion)
        assertEquals("Windows", hints.platform)
        assertEquals("19.0.0", hints.platformVersion)
        assertEquals("x86", hints.architecture)
        assertEquals(false, hints.isMobile)
    }

    @Test
    fun notChrome() {
        assertNull(clientHintsFor("Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0", engine))
    }
}
