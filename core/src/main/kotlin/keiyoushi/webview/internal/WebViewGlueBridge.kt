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

        factory = cast(WebViewProviderFactoryBoundaryInterface::class.java, webViewProviderFactory)
    }

    private fun <T> cast(boundaryClass: Class<T>, handler: InvocationHandler): T {
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(
            WebViewGlueBridge::class.java.getClassLoader(),
            arrayOf<Class<*>?>(boundaryClass),
            handler,
        ) as T
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
    fun setClientHints(
        settings: WebSettings,
        majorVersion: String,
        fullVersion: String,
    ) {
        if (!isFeatureSupported("USER_AGENT_METADATA")) {
            Log.i("WebViewGlueBridge", "USER_AGENT_METADATA not supported")
            return
        }

        try {
            ensureFactory()
            val webkitToCompatConverter = cast(
                WebkitToCompatConverterBoundaryInterface::class.java,
                factory.webkitToCompatConverter,
            )
            val boundarySettings = cast(
                WebSettingsBoundaryInterface::class.java,
                webkitToCompatConverter.convertSettings(settings),
            )

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

            boundarySettings.setUserAgentMetadataFromMap(updated)
        } catch (e: Throwable) {
            Log.e("WebViewGlueBridge", "setUserClientHints", e)
        }
    }
}
