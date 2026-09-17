package keiyoushi.webview.internal

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import org.chromium.support_lib_boundary.WebSettingsBoundaryInterface
import org.chromium.support_lib_boundary.WebViewProviderFactoryBoundaryInterface
import org.chromium.support_lib_boundary.WebkitToCompatConverterBoundaryInterface
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

internal object WebViewGlueBridge {

    private lateinit var factory: WebViewProviderFactoryBoundaryInterface

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
    fun isFeatureSupported(featureName: String): Boolean {
        try {
            ensureFactory()
            for (feature in factory.supportedFeatures) {
                if (featureName == feature) return true
            }
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", """isFeatureSupported("$featureName")""", e)
        }
        return false
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
            ensureFactory()
            val webkitToCompatConverter = Proxy.newProxyInstance(
                WebViewGlueBridge::class.java.getClassLoader(),
                arrayOf(WebkitToCompatConverterBoundaryInterface::class.java),
                factory.webkitToCompatConverter,
            ) as WebkitToCompatConverterBoundaryInterface

            val boundarySettings = Proxy.newProxyInstance(
                WebViewGlueBridge::class.java.getClassLoader(),
                arrayOf(WebSettingsBoundaryInterface::class.java),
                webkitToCompatConverter.convertSettings(settings),
            ) as WebSettingsBoundaryInterface

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
