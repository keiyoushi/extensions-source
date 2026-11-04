package eu.kanade.tachiyomi.extension.en.templescan

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import keiyoushi.utils.applicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.InvocationHandler

/**
 * Sends [name] with [value] on every request WebView's default profile makes to an origin matching [originRules],
 * the app's own WebView screen included, in place of WebView's own value. Needs Chromium 139+; false if unsupported.
 */
internal suspend fun setWebViewOriginHeader(name: String, value: String, originRules: Set<String>): Boolean = withContext(Dispatchers.Main) {
    try {
        // WebView's profiles exist once its engine has started
        WebSettings.getDefaultUserAgent(applicationContext)
        val loader = webViewClassLoader()
        val factory = Class.forName("org.chromium.support_lib_glue.SupportLibReflectionUtil", false, loader)
            .getDeclaredMethod("createWebViewProviderFactory")
            .invoke(null) as InvocationHandler

        @Suppress("UNCHECKED_CAST")
        val features = factory.call(loader, "WebViewProviderFactoryBoundaryInterface", "getSupportedFeatures") as Array<String>
        if ("MULTI_PROFILE" !in features || "EXTRA_HEADER_FOR_ORIGINS" !in features) return@withContext false
        val store = factory.call(loader, "WebViewProviderFactoryBoundaryInterface", "getProfileStore") as InvocationHandler
        val profile = store.call(loader, "ProfileStoreBoundaryInterface", "getProfile", "Default") as InvocationHandler
        profile.call(loader, "ProfileBoundaryInterface", "setOriginMatchedHeader", name, value, originRules)
        true
    } catch (e: Exception) {
        Log.e("TempleScan", "WebView profile", e)
        false
    }
}

private fun webViewClassLoader(): ClassLoader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    WebView.getWebViewClassLoader()
} else {
    @SuppressLint("DiscouragedPrivateApi")
    WebView::class.java.getDeclaredMethod("getFactory").apply { isAccessible = true }.invoke(null)!!.javaClass.classLoader!!
}

// Chromium's support library resolves calls by the method of its own boundary interface
private fun InvocationHandler.call(loader: ClassLoader, boundary: String, method: String, vararg args: Any?): Any? {
    val target = Class.forName("org.chromium.support_lib_boundary.$boundary", false, loader).methods
        .first { it.name == method && it.parameterCount == args.size }
    return invoke(null, target, args.takeIf { it.isNotEmpty() })
}
