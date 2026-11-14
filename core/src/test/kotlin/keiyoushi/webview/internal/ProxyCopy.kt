package keiyoushi.webview.internal

import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Function

/**
 * One extension's copy of core's WebView proxy, loaded as the app loads an extension: core's classes child-first in a
 * classloader of their own, with the JDK, Kotlin, OkHttp and the Android stubs shared, as they are with the app.
 *
 * [classes] is this build's compiled core ([CURRENT]) or a frozen jar of a released build (see
 * [WebViewProxyCompatTest]). [version] lowers this build's protocol version, to stand in for an older build, and
 * [build] sets its build stamp, for another build of the same version. The copy is driven only through what
 * [ProxyProtocol] names as stable: `WebViewProxy.open`, `renew`, `close`, `version`, `build`, `retireGraceMillis` and
 * [WebViewOverride]'s fields.
 */
internal class ProxyCopy(classes: URL, version: Int? = null, webView: FakeWebView, graceMillis: Long = 200, build: Long? = null) {
    private val loader = object : URLClassLoader(arrayOf(classes), ProxyCopy::class.java.classLoader) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
            if (name.startsWith("keiyoushi.") || name.startsWith("org.chromium.")) {
                findLoadedClass(name) ?: findClass(name)
            } else {
                super.loadClass(name, resolve)
            }
        }
    }

    private val proxyClass = loader.loadClass("keiyoushi.webview.internal.WebViewProxy")
    private val proxy = proxyClass.getField("INSTANCE").get(null)
    private val routes = CopyOnWriteArrayList<Any>()

    init {
        loader.loadClass("keiyoushi.webview.internal.WebViewOverride").apply {
            getField("post").set(null, webView.post)
            getField("set").set(null, webView.set)
            getField("clear").set(null, webView.clear)
            // Since the override's mode depends on the device's proxy
            listOf("reverseBypass" to webView.reverseBypass, "deviceProxy" to webView.deviceProxy, "watch" to webView.watch).forEach { (name, value) ->
                runCatching { getField(name) }.getOrNull()?.set(null, value)
            }
        }
        if (version != null) proxyClass.getField("version").setInt(null, version)
        if (build != null) proxyClass.getField("build").setLong(null, build)
        proxyClass.getField("retireGraceMillis").setLong(null, graceMillis)
    }

    val version: Int get() = proxyClass.getField("version").getInt(null)

    val build: Long get() = proxyClass.getField("build").getLong(null)

    var graceMillis: Long
        get() = proxyClass.getField("retireGraceMillis").getLong(null)
        set(value) = proxyClass.getField("retireGraceMillis").setLong(null, value)

    /** Opens a route, as `proxyHosts` does; returns this copy's Route. */
    fun open(hosts: Set<String>, send: (Request) -> Response): Any {
        val onReady: (Boolean) -> Unit = {}
        return method("open").invoke(proxy, hosts, Function(send), onReady).also(routes::add)
    }

    fun renew(route: Any) {
        method("renew").invoke(proxy, route)
    }

    fun close(route: Any) {
        method("close").invoke(proxy, route)
        routes -= route
    }

    fun closeAll() = routes.forEach(::close)

    private fun method(name: String) = proxyClass.methods.single { it.name == name }

    companion object {
        /** This build's compiled core classes. */
        val CURRENT: URL = WebViewProxy::class.java.protectionDomain!!.codeSource.location

        /** The frozen builds in the test resources, by file name. */
        val FROZEN: List<File> = ProxyCopy::class.java.getResource("/webview-proxy")
            ?.let { File(it.toURI()).listFiles { file -> file.extension == "jar" }?.sortedBy { it.name } }
            .orEmpty()
    }
}

/**
 * The app's WebViews, as far as the proxy's override goes: one main thread, and what was set on it in order; and the
 * device's proxy, as far as the override's mode goes.
 */
internal class FakeWebView {
    private val main = Executors.newSingleThreadExecutor { Thread(it, "main").apply { isDaemon = true } }

    /** `set <port>` and `clear`, in the order the main thread ran them. */
    val events = CopyOnWriteArrayList<String>()

    /** The hosts each `set` sent to the proxy, in order; null for every host. */
    val hosts = CopyOnWriteArrayList<List<String>?>()

    /** Whether WebView supports reverse bypass. */
    @Volatile
    var hasReverseBypass = true

    /** Whether the device has a proxy of its own; [changeDeviceProxy] to change it while routes are open. */
    @Volatile
    var hasDeviceProxy = false

    /** The callbacks of the watches on the device's proxy still registered. */
    val watching = CopyOnWriteArrayList<Runnable>()

    val post = Executor(main::execute)

    val set: (Int, List<String>?, Runnable) -> Boolean = { port, hosts, onApplied ->
        events += "set $port"
        this.hosts += hosts
        main.execute(onApplied)
        true
    }

    val clear = Runnable { events += "clear" }

    val reverseBypass: () -> Boolean = { hasReverseBypass }

    val deviceProxy: () -> Boolean = { hasDeviceProxy }

    val watch: (Runnable) -> Runnable = { onChange ->
        watching += onChange
        Runnable { check(watching.remove(onChange)) { "unwatched twice" } }
    }

    /** Sets the device's proxy and broadcasts the change, as Android does: on the main thread, to every watch. */
    fun changeDeviceProxy(on: Boolean) {
        hasDeviceProxy = on
        watching.forEach(main::execute)
    }

    /** Waits for the main thread to run what was posted so far. */
    fun idle() {
        main.submit {}.get(5, TimeUnit.SECONDS)
    }
}
