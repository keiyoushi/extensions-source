package keiyoushi.webview.internal

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The override's mode, by the device's proxy and WebView's support for reverse bypass, as copies of core apply it. */
class WebViewOverrideTest {
    private val webView = FakeWebView()
    private val copies = mutableListOf<ProxyCopy>()
    private val routed = listOf("a.test", "*.a.test")

    private fun copy(build: Long? = null) = ProxyCopy(ProxyCopy.CURRENT, webView = webView, build = build).also(copies::add)

    @Before
    fun noProxyRunning() = awaitStopped()

    @After
    fun closeRoutes() {
        copies.forEach(ProxyCopy::closeAll)
        awaitStopped()
    }

    @Test
    fun withoutADeviceProxyOnlyRoutedHostsComeToTheProxy() {
        copy().open(setOf("a.test")) { ok(it, "") }
        webView.idle()
        assertEquals(listOf(routed), webView.hosts)
    }

    @Test
    fun withADeviceProxyEveryHostComesToTheProxy() {
        webView.hasDeviceProxy = true
        copy().open(setOf("a.test")) { ok(it, "") }
        webView.idle()
        assertEquals(listOf(null), webView.hosts)
    }

    @Test
    fun withoutReverseBypassEveryHostComesToTheProxy() {
        webView.hasReverseBypass = false
        copy().open(setOf("a.test")) { ok(it, "") }
        webView.idle()
        assertEquals(listOf(null), webView.hosts)
    }

    // ConnectivityManager's ProxyInfo: kx vpn's global http_proxy "127.0.0.1:<port>", a PAC proxy before and after
    // Android's local PAC proxy is up, and none
    @Test
    fun aPacProxyIsADeviceProxy() {
        assertTrue(isProxy("127.0.0.1", ""))
        assertTrue(isProxy("localhost", "http://wpad.test/proxy.pac"))
        assertTrue(isProxy(null, "http://wpad.test/proxy.pac"))
        assertFalse(isProxy("", ""))
        assertFalse(isProxy(null, null))
    }

    @Test
    fun aDeviceProxyChangeWhileServingReappliesTheOverride() {
        val copy = copy()
        val route = copy.open(setOf("a.test")) { ok(it, "") }
        webView.idle()
        webView.changeDeviceProxy(true)
        webView.idle()
        webView.idle()
        webView.changeDeviceProxy(false)
        webView.idle()
        webView.idle()
        assertEquals(listOf(routed, null, routed), webView.hosts)
        copy.close(route)
        webView.idle()
        assertEquals("clear", webView.events.last())
    }

    @Test
    fun theWatchEndsWhenTheProxyStops() {
        val copy = copy()
        val a = copy.open(setOf("a.test")) { ok(it, "") }
        val b = copy.open(setOf("b.test")) { ok(it, "") }
        assertEquals(1, webView.watching.size)
        copy.close(a)
        assertEquals(1, webView.watching.size)
        copy.close(b)
        assertEquals(0, webView.watching.size)
        // A change broadcast as the proxy stopped sets nothing
        webView.changeDeviceProxy(true)
        webView.idle()
        webView.idle()
        assertEquals("clear", webView.events.last())
    }

    @Test
    fun aLapsedLastRouteEndsTheWatch() {
        copy().open(setOf("a.test")) { ok(it, "") }
        lapse(routes().single())
        awaitStopped()
        assertEquals(0, webView.watching.size)
    }

    @Test
    fun aTakeoverHandsTheWatchToTheNewServer() {
        val older = copy(build = 100)
        older.open(setOf("a.test")) { ok(it, "") }
        val newer = copy(build = 200)
        newer.open(setOf("b.test")) { ok(it, "") }
        assertEquals(200L, (shared[ProxyProtocol.SERVER] as Map<*, *>)[ProxyProtocol.SERVER_BUILD])
        assertEquals(1, webView.watching.size)
        webView.idle()
        val sets = webView.hosts.size
        webView.changeDeviceProxy(true)
        webView.idle()
        webView.idle()
        // Applied once, by the serving copy, for both copies' routes
        assertEquals(sets + 1, webView.hosts.size)
        assertEquals(null, webView.hosts.last())
        webView.changeDeviceProxy(false)
        webView.idle()
        webView.idle()
        assertEquals(routed + listOf("b.test", "*.b.test"), webView.hosts.last())
        older.closeAll()
        newer.closeAll()
        assertEquals(0, webView.watching.size)
    }
}
