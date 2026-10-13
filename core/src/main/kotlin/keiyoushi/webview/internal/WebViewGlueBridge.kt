package keiyoushi.webview.internal

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import keiyoushi.utils.ScriptHandle
import keiyoushi.utils.WebMessageReplyProxy
import org.chromium.support_lib_boundary.JsReplyProxyBoundaryInterface
import org.chromium.support_lib_boundary.ScriptHandlerBoundaryInterface
import org.chromium.support_lib_boundary.WebMessageBoundaryInterface
import org.chromium.support_lib_boundary.WebMessagePayloadBoundaryInterface
import org.chromium.support_lib_boundary.WebSettingsBoundaryInterface
import org.chromium.support_lib_boundary.WebViewProviderBoundaryInterface
import org.chromium.support_lib_boundary.WebViewProviderFactoryBoundaryInterface
import org.chromium.support_lib_boundary.WebkitToCompatConverterBoundaryInterface
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.WeakHashMap

internal object WebViewGlueBridge {

    private lateinit var factory: WebViewProviderFactoryBoundaryInterface
    private val providers = WeakHashMap<WebView, WebViewProviderBoundaryInterface>()

    private fun ensureFactory() {
        if (::factory.isInitialized) return

        val webViewClassLoader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.getWebViewClassLoader()
        } else {
            @SuppressLint("DiscouragedPrivateApi")
            WebView::class.java.getDeclaredMethod("getFactory")
                .apply { isAccessible = true }
                .invoke(null).javaClass.getClassLoader()!!
        }

        val supportLibReflectionUtil = Class.forName(
            "org.chromium.support_lib_glue.SupportLibReflectionUtil",
            false,
            webViewClassLoader,
        )

        val webViewProviderFactory = supportLibReflectionUtil
            .getDeclaredMethod("createWebViewProviderFactory")
            .invoke(null) as InvocationHandler

        factory = Proxy.newProxyInstance(
            WebViewGlueBridge::class.java.getClassLoader(),
            arrayOf(WebViewProviderFactoryBoundaryInterface::class.java),
            webViewProviderFactory,
        ) as WebViewProviderFactoryBoundaryInterface
    }

    @Synchronized
    private fun getProvider(webView: WebView): WebViewProviderBoundaryInterface? {
        providers[webView]?.let { return it }
        return try {
            ensureFactory()
            val webViewProvider = factory.createWebView(webView)
            val provider = Proxy.newProxyInstance(
                WebViewGlueBridge::class.java.classLoader,
                arrayOf(WebViewProviderBoundaryInterface::class.java),
                webViewProvider,
            ) as WebViewProviderBoundaryInterface
            providers[webView] = provider
            provider
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", "getProvider", e)
            null
        }
    }

    @Synchronized
    private fun getBoundarySettings(settings: WebSettings): WebSettingsBoundaryInterface? = try {
        ensureFactory()
        val webkitToCompatConverter = Proxy.newProxyInstance(
            WebViewGlueBridge::class.java.classLoader,
            arrayOf(WebkitToCompatConverterBoundaryInterface::class.java),
            factory.webkitToCompatConverter,
        ) as WebkitToCompatConverterBoundaryInterface

        Proxy.newProxyInstance(
            WebViewGlueBridge::class.java.classLoader,
            arrayOf(WebSettingsBoundaryInterface::class.java),
            webkitToCompatConverter.convertSettings(settings),
        ) as WebSettingsBoundaryInterface
    } catch (e: Throwable) {
        Log.e("WebViewGlueBridge", "getBoundarySettings", e)
        null
    }

    @Synchronized
    fun isFeatureSupported(featureName: String): Boolean {
        try {
            ensureFactory()
            for (feature in factory.supportedFeatures) {
                if (feature == featureName || feature.startsWith("$featureName:")) return true
            }
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", """isFeatureSupported("$featureName")""", e)
        }
        return false
    }

    @Synchronized
    fun setAudioMuted(webView: WebView, muted: Boolean): Boolean {
        if (!isFeatureSupported("MUTE_AUDIO")) return false
        return try {
            getProvider(webView)?.setAudioMuted(muted)
            true
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", "setAudioMuted", e)
            false
        }
    }

    @Synchronized
    fun setIncludeCookiesOnIntercept(settings: WebSettings, include: Boolean): Boolean {
        if (!isFeatureSupported("COOKIE_INTERCEPT")) return false
        return try {
            getBoundarySettings(settings)?.setIncludeCookiesOnIntercept(include)
            true
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", "setIncludeCookiesOnIntercept", e)
            false
        }
    }

    @Synchronized
    fun setWillSuppressErrorPage(settings: WebSettings, suppress: Boolean): Boolean {
        if (!isFeatureSupported("SUPPRESS_ERROR_PAGE")) return false
        return try {
            getBoundarySettings(settings)?.setWillSuppressErrorPage(suppress)
            true
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", "setWillSuppressErrorPage", e)
            false
        }
    }

    @Synchronized
    fun addDocumentStartJavaScript(
        webView: WebView,
        script: String,
        allowedOriginRules: Set<String>,
    ): ScriptHandle? {
        if (!isFeatureSupported("DOCUMENT_START_SCRIPT")) return null
        return try {
            val provider = getProvider(webView) ?: return null
            val handler = provider.addDocumentStartJavaScript(script, allowedOriginRules.toTypedArray())
            val scriptHandler = Proxy.newProxyInstance(
                WebViewGlueBridge::class.java.classLoader,
                arrayOf(ScriptHandlerBoundaryInterface::class.java),
                handler,
            ) as ScriptHandlerBoundaryInterface
            ScriptHandle {
                runCatching { scriptHandler.remove() }
            }
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", "addDocumentStartJavaScript", e)
            null
        }
    }

    @Synchronized
    fun addJavaScriptOnEvent(
        webView: WebView,
        script: String,
        event: Int,
        worldName: String,
        allowedOriginRules: Set<String>,
    ): ScriptHandle? {
        if (!isFeatureSupported("JS_INJECTION_IN_FRAME_AND_WORLD")) return null
        return try {
            val provider = getProvider(webView) ?: return null
            val handler = provider.addJavaScriptOnEvent(
                script,
                allowedOriginRules.toTypedArray(),
                event,
                worldName,
            )
            val scriptHandler = Proxy.newProxyInstance(
                WebViewGlueBridge::class.java.classLoader,
                arrayOf(ScriptHandlerBoundaryInterface::class.java),
                handler,
            ) as ScriptHandlerBoundaryInterface
            ScriptHandle {
                runCatching { scriptHandler.remove() }
            }
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", "addJavaScriptOnEvent", e)
            null
        }
    }

    @Synchronized
    fun addWebMessageListener(
        webView: WebView,
        jsObjectName: String,
        allowedOriginRules: Set<String>,
        onError: (Throwable) -> Unit = {},
        listener: (message: String, replyProxy: WebMessageReplyProxy) -> Unit,
    ): Boolean {
        if (!isFeatureSupported("WEB_MESSAGE_LISTENER")) return false
        return try {
            val provider = getProvider(webView) ?: return false
            val handler = InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "getSupportedFeatures" -> arrayOf("WEB_MESSAGE_LISTENER", "WEB_MESSAGE_ARRAY_BUFFER")
                    "onPostMessage" -> {
                        // args[0]: WebView view
                        // args[1]: InvocationHandler webMessage
                        // args[2]: Uri sourceOrigin
                        // args[3]: boolean isMainFrame
                        // args[4]: InvocationHandler replyProxy
                        val msgHandler = args?.getOrNull(1) as? InvocationHandler
                        val replyHandler = args?.getOrNull(4) as? InvocationHandler

                        val messageString = msgHandler?.let { extractMessageString(it) }.orEmpty()
                        val replyProxy = replyHandler?.let { createReplyProxy(it, onError) } ?: NoOpReplyProxy

                        listener(messageString, replyProxy)
                        null
                    }
                    "equals" -> proxy === args?.getOrNull(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "WebMessageListener($jsObjectName)"
                    else -> null
                }
            }
            provider.addWebMessageListener(jsObjectName, allowedOriginRules.toTypedArray(), handler)
            true
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", "addWebMessageListener", e)
            false
        }
    }

    private fun extractMessageString(messageHandler: InvocationHandler): String {
        return try {
            val webMessage = Proxy.newProxyInstance(
                WebViewGlueBridge::class.java.classLoader,
                arrayOf(WebMessageBoundaryInterface::class.java),
                messageHandler,
            ) as WebMessageBoundaryInterface

            webMessage.getData() ?: run {
                val payloadHandler = webMessage.getMessagePayload() ?: return ""
                val payload = Proxy.newProxyInstance(
                    WebViewGlueBridge::class.java.classLoader,
                    arrayOf(WebMessagePayloadBoundaryInterface::class.java),
                    payloadHandler,
                ) as WebMessagePayloadBoundaryInterface
                if (payload.getType() == 0) {
                    payload.getAsString().orEmpty()
                } else {
                    payload.getAsArrayBuffer()?.let { String(it, Charsets.UTF_8) }.orEmpty()
                }
            }
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", "extractMessageString", e)
            ""
        }
    }

    private fun createReplyProxy(
        replyHandler: InvocationHandler,
        onError: (Throwable) -> Unit,
    ): WebMessageReplyProxy {
        val proxy = Proxy.newProxyInstance(
            WebViewGlueBridge::class.java.classLoader,
            arrayOf(JsReplyProxyBoundaryInterface::class.java),
            replyHandler,
        ) as JsReplyProxyBoundaryInterface

        return object : WebMessageReplyProxy {
            override fun postMessage(message: String) {
                runCatching { proxy.postMessage(message) }
            }

            override fun evaluateJs(script: String, callback: ((String?) -> Unit)?) {
                val callbackHandler = InvocationHandler { _, method, args ->
                    when (method.name) {
                        "onSuccess" -> {
                            val result = args?.getOrNull(0) as? String
                            try {
                                callback?.invoke(result)
                            } catch (t: Throwable) {
                                onError(t)
                            }
                            null
                        }
                        "onFailure" -> {
                            try {
                                callback?.invoke(null)
                            } catch (t: Throwable) {
                                onError(t)
                            }
                            null
                        }
                        else -> null
                    }
                }
                runCatching { proxy.executeJavaScript(script, callbackHandler) }
            }
        }
    }

    private object NoOpReplyProxy : WebMessageReplyProxy {
        override fun postMessage(message: String) = Unit
        override fun evaluateJs(script: String, callback: ((String?) -> Unit)?) {
            callback?.invoke(null)
        }
    }

    /** Spoofs the Sec-CH-UA client hints.  */
    @Synchronized
    fun setClientHintsFromUserAgent(
        settings: WebSettings,
        userAgent: String,
    ) {
        val versionMatch = CHROME_VERSION_REGEX.find(userAgent)

        if (versionMatch == null) {
            Log.i("WebViewGlueBridge", "Not a valid Chrome UserAgent for client hints")
            return
        }

        if (!isFeatureSupported("USER_AGENT_METADATA")) {
            Log.i("WebViewGlueBridge", "USER_AGENT_METADATA not supported")
            return
        }

        val majorVersion = versionMatch.groupValues[1]
        val fullVersion = majorVersion + versionMatch.groupValues[2].ifEmpty { ".0.0.0" }
        val isMobile = userAgent.contains("Mobile") ||
            userAgent.contains("Android") ||
            userAgent.contains("iPhone") ||
            userAgent.contains("iPad")
        var platform = "Android"
        var platformVersion = ""
        var architecture = "arm"

        when {
            userAgent.contains("Android") -> {
                val androidVersion = ANDROID_VERSION_REGEX.find(userAgent)?.groupValues?.get(1) ?: ""
                platform = "Android"
                platformVersion = androidVersion
            }
            userAgent.contains("iPhone") || userAgent.contains("iPad") -> {
                platform = "iOS"
            }
            userAgent.contains("Windows") -> {
                platform = "Windows"
                platformVersion = "19.0.0"
                architecture = "x86"
            }
            userAgent.contains("Macintosh") || userAgent.contains("Mac OS X") -> {
                val macVersion = MAC_OS_X_REGEX.find(userAgent)?.groupValues?.get(1)?.replace("_", ".")
                platform = "macOS"
                platformVersion = macVersion ?: ""
            }
            userAgent.contains("Linux") -> {
                val arch = LINUX_ARCH_REGEX.find(userAgent)?.groupValues?.get(1)
                platform = "Linux"
                architecture = if (arch == "aarch64") "arm" else "x86"
            }
        }

        try {
            val boundarySettings = getBoundarySettings(settings) ?: return

            val uaMetadata = boundarySettings.userAgentMetadataMap
            val updated = HashMap(uaMetadata)

            val brandList = uaMetadata["BRAND_VERSION_LIST"]
            if (brandList is Array<*> && brandList.isArrayOf<Array<String>>()) {
                @Suppress("UNCHECKED_CAST")
                val brands = brandList as Array<Array<String>>

                val updatedBrands = brands.map { original ->
                    original.copyOf().also { brand ->
                        if (brand.size < 3) return@also

                        when (brand[0]) {
                            "Android WebView" -> brand[0] = "Google Chrome"
                            "Chromium" -> Unit
                            else -> return@also
                        }

                        brand[1] = majorVersion
                        brand[2] = fullVersion
                    }
                }.toTypedArray()

                updated["BRAND_VERSION_LIST"] = updatedBrands
            }

            updated["FULL_VERSION"] = fullVersion
            updated["PLATFORM"] = platform
            updated["PLATFORM_VERSION"] = platformVersion
            updated["ARCHITECTURE"] = architecture
            updated["MOBILE"] = isMobile

            boundarySettings.setUserAgentMetadataFromMap(updated)
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", "setUserClientHints", e)
        }
    }
}

private val CHROME_VERSION_REGEX = """Chrome/(\d+)(\.[\d.]+)?""".toRegex()
private val MAC_OS_X_REGEX = """Mac OS X ([\d_]+)""".toRegex()
private val ANDROID_VERSION_REGEX = """Android ([\d.]+)""".toRegex()
private val LINUX_ARCH_REGEX = """Linux (x86_64|i686|aarch64|armv\d+)""".toRegex()
