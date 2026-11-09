package keiyoushi.webview.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Proxy
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import keiyoushi.utils.applicationContext
import java.util.concurrent.Executor

/**
 * WebView's process-wide proxy override, set and cleared on the main thread, where [WebViewProxy] posts the calls in
 * the order its routes change. Tests swap in a fake WebView and device.
 */
internal object WebViewOverride {
    private const val TAG = "WebViewOverride"

    @JvmField
    @Volatile
    var post: Executor = Executor(Handler(Looper.getMainLooper())::post)

    /**
     * Points the app's WebViews' requests to the hosts (bypass-rule patterns such as `example.com` and `*.example.com`),
     * or all of their requests where they're null, at `127.0.0.1:port`; runs the callback once they use it, false if
     * refused.
     */
    @JvmField
    @Volatile
    var set: (port: Int, hosts: List<String>?, onApplied: Runnable) -> Boolean = { port, hosts, onApplied ->
        WebViewGlueBridge.setProxyOverride(port, hosts, onApplied::run)
    }

    @JvmField
    @Volatile
    var clear: Runnable = Runnable { WebViewGlueBridge.clearProxyOverride() }

    /** Whether WebView can limit the override to some hosts (Chromium 92+). */
    @JvmField
    @Volatile
    var reverseBypass: () -> Boolean = WebViewGlueBridge::isReverseBypassSupported

    /** Whether the device has a proxy of its own, as WebView sees it. */
    @JvmField
    @Volatile
    var deviceProxy: () -> Boolean = ::deviceHasProxy

    /** Runs the callback on the main thread whenever the device's proxy changes, until the returned [Runnable] runs. */
    @JvmField
    @Volatile
    var watch: (onChange: Runnable) -> Runnable = ::watchDeviceProxy

    /**
     * The hosts the override covers for the routes' [routed] hosts, read on each apply; null for every host. This is
     * the one place the override's mode forks:
     * - WebView sends a host the override leaves out DIRECT, past the device's own proxy (global, per-network or PAC),
     *   so routed hosts alone only where the device has none: unrouted hosts then go direct, as they do without an
     *   override.
     * - With a device proxy, or on WebView without reverse bypass, every host: the proxy tunnels unrouted hosts on
     *   through the device's proxy, which `ProxySelector` follows.
     */
    fun scope(routed: List<String>): List<String>? {
        val mode = when {
            !reverseBypass() -> "every host: no reverse bypass"
            deviceProxy() -> "every host: device proxy"
            else -> null
        }
        Log.d(TAG, "override: ${mode ?: "routed hosts only"}")
        return routed.takeIf { mode == null }
    }
}

// Chromium follows the default network's proxy as ConnectivityManager gives it: a global proxy (the http_proxy setting
// included), else the network's own, a PAC one as its URL. Where it can't be read, the device may have one
private fun deviceHasProxy(): Boolean = try {
    applicationContext.getSystemService(ConnectivityManager::class.java).defaultProxy
        ?.let { isProxy(it.host, it.pacFileUrl?.toString()) } == true
} catch (e: Throwable) {
    Log.e("WebViewOverride", "deviceProxy", e)
    true
}

/** Whether a `ProxyInfo` with [host] and [pacFileUrl] is a proxy: ConnectivityManager gives a PAC one both. */
internal fun isProxy(host: String?, pacFileUrl: String?): Boolean = !host.isNullOrEmpty() || !pacFileUrl.isNullOrEmpty()

private fun watchDeviceProxy(onChange: Runnable): Runnable {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The broadcast is sticky: registering hands over the last one, which the override was set with
            if (isInitialStickyBroadcast) return
            // On the main thread, where a throw would crash the app
            try {
                onChange.run()
            } catch (e: Throwable) {
                Log.e("WebViewOverride", "proxy change", e)
            }
        }
    }
    val filter = IntentFilter(Proxy.PROXY_CHANGE_ACTION)
    // A system broadcast, which reaches only an exported receiver; apps targeting 34+ have to say so
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    } else {
        applicationContext.registerReceiver(receiver, filter)
    }
    return Runnable { applicationContext.unregisterReceiver(receiver) }
}
