// Feature checks go through isFeatureSupported, which lint doesn't know
@file:SuppressLint("RequiresFeature")

package keiyoushi.webview.internal

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** Spoofs the Sec-CH-UA client hints to match [userAgent]. */
internal fun setClientHints(settings: WebSettings, userAgent: String) {
    if (!isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
        Log.i("KeiyoushiWebView", "USER_AGENT_METADATA not supported")
        return
    }
    try {
        val metadata = clientHintsFor(userAgent, WebSettingsCompat.getUserAgentMetadata(settings))
        if (metadata == null) {
            Log.i("KeiyoushiWebView", "Not a valid Chrome UserAgent for client hints")
            return
        }
        WebSettingsCompat.setUserAgentMetadata(settings, metadata)
    } catch (e: Throwable) {
        Log.e("KeiyoushiWebView", "setClientHints", e)
    }
}

/** The client hints a Chrome with [userAgent] sends, from the engine's [current] ones; null when it isn't Chrome's. */
internal fun clientHintsFor(userAgent: String, current: UserAgentMetadata): UserAgentMetadata? {
    val versionMatch = CHROME_VERSION_REGEX.find(userAgent) ?: return null

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
            platform = "Android"
            platformVersion = ANDROID_VERSION_REGEX.find(userAgent)?.groupValues?.get(1) ?: ""
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
            platform = "macOS"
            platformVersion = MAC_OS_X_REGEX.find(userAgent)?.groupValues?.get(1)?.replace("_", ".") ?: ""
        }
        userAgent.contains("Linux") -> {
            val arch = LINUX_ARCH_REGEX.find(userAgent)?.groupValues?.get(1)
            platform = "Linux"
            architecture = if (arch == "aarch64") "arm" else "x86"
        }
    }

    // Real Chrome reduces its UA string to "Chrome/<major>.0.0.0" and "Android 10; K", but its client hints still
    // report the exact versions. Keep the engine's own values when the claimed major version is the engine's,
    // instead of the placeholders from the UA.
    val hintFullVersion = current.fullVersion?.takeIf { it.substringBefore('.') == majorVersion } ?: fullVersion
    if (userAgent.contains("Android 10; K)")) {
        current.platformVersion?.let { platformVersion = it }
    }

    val brands = current.brandVersionList.map { brand ->
        val name = when (brand.brand) {
            "Android WebView" -> "Google Chrome"
            "Chromium" -> brand.brand
            else -> return@map brand
        }
        UserAgentMetadata.BrandVersion.Builder(brand)
            .setBrand(name)
            .setMajorVersion(majorVersion)
            .setFullVersion(hintFullVersion)
            .build()
    }

    return UserAgentMetadata.Builder(current)
        .setBrandVersionList(brands)
        .setFullVersion(hintFullVersion)
        .setPlatform(platform)
        .setPlatformVersion(platformVersion)
        .setArchitecture(architecture)
        .setMobile(isMobile)
        .build()
}

private val CHROME_VERSION_REGEX = """Chrome/(\d+)(\.[\d.]+)?""".toRegex()
private val MAC_OS_X_REGEX = """Mac OS X ([\d_]+)""".toRegex()
private val ANDROID_VERSION_REGEX = """Android ([\d.]+)""".toRegex()
private val LINUX_ARCH_REGEX = """Linux (x86_64|i686|aarch64|armv\d+)""".toRegex()
