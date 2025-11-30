package eu.kanade.tachiyomi.extension.zh.bakamh

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * OkHttp Interceptor that adds Client Hints headers based on User-Agent
 *
 * References:
 * - https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/User-Agent
 * - https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-CH-UA
 *
 * ## Usage:
 * ```kotlin
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(UserAgentClientHintsInterceptor())
 *     .build()
 * ```
 *
 * ## Features:
 * - Automatically parses browser and platform information from User-Agent header
 * - Generates corresponding Sec-CH-UA series headers (Client Hints)
 * - Supports mainstream browsers: Chrome, Edge, Opera, Firefox, Safari
 * - Supports mainstream platforms: Windows, macOS, Linux, Android, iOS
 * - Automatically identifies mobile and desktop devices
 * - Uses caching and precompiled regex for performance optimization
 *
 * ## Generated Headers:
 * - Sec-CH-UA: Browser brand and version
 * - Sec-CH-UA-Mobile: Whether it's a mobile device (?0 or ?1)
 * - Sec-CH-UA-Platform: Platform name (e.g., "Windows", "Android")
 * - Sec-CH-UA-Platform-Version: Platform version (optional)
 * - Sec-CH-UA-Full-Version-List: Full version list (optional)
 */
class UserAgentClientHintsInterceptor : Interceptor {

    private val parser = UAParser()

    // Thread-safe UA parsing result cache (max 50 UAs)
    private val cache = ConcurrentHashMap<String, SecCHHeaders>(16, 0.75f)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val userAgent = originalRequest.header("User-Agent")

        // Skip if no User-Agent header
        if (userAgent.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }

        // Get from cache or parse UA and generate Client Hints headers
        val secCHHeaders = cache.getOrPut(userAgent) {
            parser.parseUAtoSecCH(userAgent).also {
                // Simple LRU: if cache exceeds limit, clear oldest entries
                if (cache.size > 50) {
                    cache.keys.take(cache.size - 40).forEach { key -> cache.remove(key) }
                }
            }
        }

        // Build new request with Sec-CH-UA related headers
        val newRequest = originalRequest.newBuilder()
            .header("Sec-CH-UA", secCHHeaders.secCHUA)
            .header("Sec-CH-UA-Mobile", secCHHeaders.secCHUAMobile)
            .header("Sec-CH-UA-Platform", secCHHeaders.secCHUAPlatform)
            .apply {
                secCHHeaders.secCHUAPlatformVersion?.let {
                    header("Sec-CH-UA-Platform-Version", "\"$it\"")
                }
                secCHHeaders.secCHUAFullVersionList?.let {
                    header("Sec-CH-UA-Full-Version-List", it)
                }
            }
            .build()

        return chain.proceed(newRequest)
    }
}

/**
 * Data class for Sec-CH-UA Client Hints headers
 */
internal data class SecCHHeaders(
    val secCHUA: String,
    val secCHUAMobile: String,
    val secCHUAPlatform: String,
    val secCHUAPlatformVersion: String? = null,
    val secCHUAFullVersionList: String? = null,
)

/**
 * User-Agent parser
 * Parses User-Agent string into corresponding Client Hints headers
 *
 * Uses precompiled regular expressions for performance optimization
 */
internal class UAParser {

    companion object {
        private const val UNKNOWN_VERSION = "119"
        private const val NOT_A_BRAND_VERSION = "24"

        // Precompiled regular expressions
        private val MAC_OS_VERSION_PATTERN = Pattern.compile("Mac OS X (\\d+[._]\\d+)")
        private val ANDROID_VERSION_PATTERN = Pattern.compile("Android (\\d+)")
        private val IOS_VERSION_PATTERN = Pattern.compile("OS (\\d+[._]\\d+)")
        private val EDGE_VERSION_PATTERN = Pattern.compile("Edg/(\\d+)")
        private val OPERA_VERSION_PATTERN = Pattern.compile("OPR/(\\d+)")
        private val CHROME_VERSION_PATTERN = Pattern.compile("Chrome/(\\d+)")
        private val FIREFOX_VERSION_PATTERN = Pattern.compile("Firefox/(\\d+)")
        private val SAFARI_VERSION_PATTERN = Pattern.compile("Version/(\\d+)")
    }

    fun parseUAtoSecCH(ua: String): SecCHHeaders {
        val brands = mutableListOf<String>()

        // Detect platform and mobile device
        val (platform, isMobile, platformVersion) = detectPlatform(ua)

        // Detect browser brands and versions
        detectBrowserBrands(ua, brands)

        // Add obfuscation brand (prevent browser fingerprinting)
        brands.add("\"Not?A_Brand\";v=\"$NOT_A_BRAND_VERSION\"")

        return SecCHHeaders(
            secCHUA = brands.joinToString(", "),
            secCHUAMobile = if (isMobile) "?1" else "?0",
            secCHUAPlatform = platform,
            secCHUAPlatformVersion = platformVersion,
            secCHUAFullVersionList = brands.joinToString(", "),
        )
    }

    private fun detectPlatform(ua: String): Triple<String, Boolean, String?> {
        return when {
            ua.contains("Windows NT 10.0") ->
                Triple("\"Windows\"", false, "10.0")

            ua.contains("Windows NT 6.3") ->
                Triple("\"Windows\"", false, "8.1")

            ua.contains("Windows NT 6.2") ->
                Triple("\"Windows\"", false, "8")

            ua.contains("Windows NT 6.1") ->
                Triple("\"Windows\"", false, "7")

            ua.contains("Macintosh") || ua.contains("Mac OS X") -> {
                val version = extractVersion(ua, MAC_OS_VERSION_PATTERN)?.replace("_", ".")
                Triple("\"macOS\"", false, version)
            }

            ua.contains("Android") -> {
                val version = extractVersion(ua, ANDROID_VERSION_PATTERN)
                Triple("\"Android\"", true, version)
            }

            ua.contains("iPhone") || ua.contains("iPad") -> {
                val version = extractVersion(ua, IOS_VERSION_PATTERN)?.replace("_", ".")
                val isMobile = ua.contains("iPhone") || ua.contains("Mobile")
                Triple("\"iOS\"", isMobile, version)
            }

            ua.contains("Linux") ->
                Triple("\"Linux\"", ua.contains("Mobile"), null)

            else ->
                Triple("\"Windows\"", ua.contains("Mobile"), null)
        }
    }

    private fun detectBrowserBrands(ua: String, brands: MutableList<String>) {
        when {
            ua.contains("Edg/") -> {
                // Microsoft Edge (Chromium-based)
                val version = extractVersion(ua, EDGE_VERSION_PATTERN) ?: UNKNOWN_VERSION
                val chromeVersion = extractVersion(ua, CHROME_VERSION_PATTERN) ?: UNKNOWN_VERSION
                brands.add("\"Chromium\";v=\"$chromeVersion\"")
                brands.add("\"Microsoft Edge\";v=\"$version\"")
            }

            ua.contains("OPR/") -> {
                // Opera (Chromium-based)
                val version = extractVersion(ua, OPERA_VERSION_PATTERN) ?: UNKNOWN_VERSION
                val chromeVersion = extractVersion(ua, CHROME_VERSION_PATTERN) ?: UNKNOWN_VERSION
                brands.add("\"Chromium\";v=\"$chromeVersion\"")
                brands.add("\"Opera\";v=\"$version\"")
            }

            ua.contains("Chrome") -> {
                // Chrome browser (must be detected after Edge and Opera)
                val version = extractVersion(ua, CHROME_VERSION_PATTERN) ?: UNKNOWN_VERSION
                brands.add("\"Chromium\";v=\"$version\"")
                brands.add("\"Google Chrome\";v=\"$version\"")
            }

            ua.contains("Firefox") -> {
                // Firefox
                val version = extractVersion(ua, FIREFOX_VERSION_PATTERN) ?: UNKNOWN_VERSION
                brands.add("\"Firefox\";v=\"$version\"")
            }

            ua.contains("Safari") -> {
                // Safari (must be detected after Chrome)
                val version = extractVersion(ua, SAFARI_VERSION_PATTERN) ?: UNKNOWN_VERSION
                brands.add("\"Safari\";v=\"$version\"")
            }

            else -> {
                // Unknown browser, default to Chromium
                brands.add("\"Chromium\";v=\"$UNKNOWN_VERSION\"")
                brands.add("\"Not_A Brand\";v=\"$UNKNOWN_VERSION\"")
            }
        }
    }

    private fun extractVersion(ua: String, pattern: Pattern): String? {
        return pattern.matcher(ua)
            .takeIf { it.find() }
            ?.group(1)
    }
}
