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

internal object WebViewGlueBridge {

    private lateinit var factory: WebViewProviderFactoryBoundaryInterface
    private lateinit var supportedFeatures: Set<String>

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

        val proxy = Proxy.newProxyInstance(
            WebViewGlueBridge::class.java.getClassLoader(),
            arrayOf(WebViewProviderFactoryBoundaryInterface::class.java),
            webViewProviderFactory,
        ) as WebViewProviderFactoryBoundaryInterface
        supportedFeatures = proxy.supportedFeatures.toHashSet()
        factory = proxy
    }

    @Synchronized
    private fun getProvider(webView: WebView): WebViewProviderBoundaryInterface? = try {
        ensureFactory()
        Proxy.newProxyInstance(
            WebViewGlueBridge::class.java.classLoader,
            arrayOf(WebViewProviderBoundaryInterface::class.java),
            factory.createWebView(webView),
        ) as WebViewProviderBoundaryInterface
    } catch (e: Throwable) {
        Log.e("WebViewGlueBridge", "getProvider", e)
        null
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

    /** Exact match only: features suffixed with `:dev` are unreleased and must not be used. */
    @Synchronized
    fun isFeatureSupported(featureName: String): Boolean {
        try {
            ensureFactory()
            return featureName in supportedFeatures
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

    // The add* functions below return null/false only when the feature is unavailable, so callers
    // can fall back. Errors from WebView itself (e.g. IllegalArgumentException for an invalid
    // allowedOriginRules entry) propagate instead of silently triggering the fallback.

    @Synchronized
    fun addDocumentStartJavaScript(
        webView: WebView,
        script: String,
        allowedOriginRules: Set<String>,
    ): ScriptHandle? {
        if (!isFeatureSupported("DOCUMENT_START_SCRIPT:1")) return null
        val provider = getProvider(webView) ?: return null
        return toScriptHandle(provider.addDocumentStartJavaScript(script, allowedOriginRules.toTypedArray()))
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
        val provider = getProvider(webView) ?: return null
        // Registers the world; addJavaScriptOnEvent throws for unregistered world names.
        provider.getJavaScriptWorld(worldName)
        return toScriptHandle(
            provider.addJavaScriptOnEvent(script, allowedOriginRules.toTypedArray(), event, worldName),
        )
    }

    private fun toScriptHandle(handler: InvocationHandler): ScriptHandle {
        val scriptHandler = Proxy.newProxyInstance(
            WebViewGlueBridge::class.java.classLoader,
            arrayOf(ScriptHandlerBoundaryInterface::class.java),
            handler,
        ) as ScriptHandlerBoundaryInterface
        return ScriptHandle {
            runCatching { scriptHandler.remove() }
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
        val provider = getProvider(webView) ?: return false
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "getSupportedFeatures" -> arrayOf("WEB_MESSAGE_LISTENER", "WEB_MESSAGE_ARRAY_BUFFER")
                "onPostMessage" -> {
                    // args: view, message, sourceOrigin, isMainFrame, replyProxy
                    val (_, messageHandler, sourceOrigin, isMainFrame, replyHandler) = args!!
                    val message = try {
                        extractMessageString(messageHandler as InvocationHandler)
                    } catch (t: Throwable) {
                        onError(t)
                        return@InvocationHandler null
                    }
                    val replyProxy = createReplyProxy(
                        replyHandler as InvocationHandler,
                        sourceOrigin = sourceOrigin.toString(),
                        isMainFrame = isMainFrame as Boolean,
                        onError = onError,
                    )
                    listener(message, replyProxy)
                    null
                }
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "WebMessageListener($jsObjectName)"
                else -> null
            }
        }
        provider.addWebMessageListener(jsObjectName, allowedOriginRules.toTypedArray(), handler)
        return true
    }

    private fun extractMessageString(messageHandler: InvocationHandler): String {
        val webMessage = Proxy.newProxyInstance(
            WebViewGlueBridge::class.java.classLoader,
            arrayOf(WebMessageBoundaryInterface::class.java),
            messageHandler,
        ) as WebMessageBoundaryInterface

        // getData() throws for non-string payloads, so read the payload whenever it's available.
        if (!isFeatureSupported("WEB_MESSAGE_ARRAY_BUFFER")) return webMessage.getData().orEmpty()

        val payload = Proxy.newProxyInstance(
            WebViewGlueBridge::class.java.classLoader,
            arrayOf(WebMessagePayloadBoundaryInterface::class.java),
            webMessage.getMessagePayload(),
        ) as WebMessagePayloadBoundaryInterface
        return if (payload.getType() == 0) {
            payload.getAsString().orEmpty()
        } else {
            String(payload.getAsArrayBuffer(), Charsets.UTF_8)
        }
    }

    private fun createReplyProxy(
        replyHandler: InvocationHandler,
        sourceOrigin: String,
        isMainFrame: Boolean,
        onError: (Throwable) -> Unit,
    ): WebMessageReplyProxy {
        val proxy = Proxy.newProxyInstance(
            WebViewGlueBridge::class.java.classLoader,
            arrayOf(JsReplyProxyBoundaryInterface::class.java),
            replyHandler,
        ) as JsReplyProxyBoundaryInterface

        return object : WebMessageReplyProxy {
            override val sourceOrigin: String? = sourceOrigin
            override val isMainFrame: Boolean? = isMainFrame

            override fun postMessage(message: String) {
                runCatching { proxy.postMessage(message) }
            }

            override fun evaluateJs(script: String, callback: ((String?) -> Unit)?) {
                if (!isFeatureSupported("JS_INJECTION_IN_FRAME_AND_WORLD")) {
                    onError(UnsupportedOperationException("WebMessageReplyProxy.evaluateJs requires JS_INJECTION_IN_FRAME_AND_WORLD (Chromium 146+)"))
                    return
                }
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
                try {
                    proxy.executeJavaScript(script, callbackHandler)
                } catch (t: Throwable) {
                    onError(t)
                }
            }
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

            // Real Chrome reduces its UA string to "Chrome/<major>.0.0.0" and "Android 10; K", but
            // its client hints still report the exact versions. Keep the engine's own values when
            // the claimed major version is the engine's, instead of the placeholders from the UA.
            val hintFullVersion = (uaMetadata["FULL_VERSION"] as? String)
                ?.takeIf { it.substringBefore('.') == majorVersion }
                ?: fullVersion
            if (userAgent.contains("Android 10; K)")) {
                (uaMetadata["PLATFORM_VERSION"] as? String)?.let { platformVersion = it }
            }

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
                        brand[2] = hintFullVersion
                    }
                }.toTypedArray()

                updated["BRAND_VERSION_LIST"] = updatedBrands
            }

            updated["FULL_VERSION"] = hintFullVersion
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
