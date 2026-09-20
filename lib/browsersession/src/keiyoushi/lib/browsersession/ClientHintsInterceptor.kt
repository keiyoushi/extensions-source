package keiyoushi.lib.browsersession

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Structured Client Hints representation parsed from a User-Agent string.
 *
 * @property brands List of browser brand and major version pairs.
 * @property isMobile True if the browser identity indicates a mobile platform.
 * @property platform Operating system platform (e.g., "Android", "Windows", "macOS").
 * @property isChromiumBased True if the browser is powered by Chromium.
 */
class ClientHints(
    val brands: List<Pair<String, String>>,
    val isMobile: Boolean,
    val platform: String,
    val isChromiumBased: Boolean = true,
) {
    /**
     * Formatted `Sec-CH-UA` header string adhering to RFC 8941 structured headers,
     * or null if the browser is not Chromium-based or lacks recognized brands.
     */
    val secChUa: String? = if (isChromiumBased && brands.isNotEmpty()) {
        brands.joinToString(", ") { (brand, version) ->
            "\"$brand\";v=\"$version\""
        }
    } else {
        null
    }

    /** Formatted `Sec-CH-UA-Mobile` header string (`?1` for mobile, `?0` for desktop). */
    val secChUaMobile: String = if (isMobile) "?1" else "?0"

    /** Formatted `Sec-CH-UA-Platform` header string (quoted platform name). */
    val secChUaPlatform: String = "\"$platform\""
}

/**
 * OkHttp [Interceptor] that aligns request headers with modern browser Client Hints specifications
 * (`Sec-CH-UA`, `Sec-CH-UA-Mobile`, and `Sec-CH-UA-Platform`).
 *
 * Automatically inspects the outgoing `User-Agent` header, parses browser and platform metadata,
 * caches results in a thread-safe [ConcurrentHashMap], and attaches matching Client Hints headers.
 *
 * @param force When true, overwrites existing Client Hints headers. When false (default),
 * preserves any pre-existing headers.
 */
class ClientHintsInterceptor(
    private val force: Boolean = false,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val userAgent = request.header("User-Agent")
        if (userAgent.isNullOrBlank()) {
            return chain.proceed(request)
        }

        val hints = parse(userAgent)
        val builder = request.newBuilder()

        if (hints.secChUa != null && (force || request.header("Sec-CH-UA") == null)) {
            builder.header("Sec-CH-UA", hints.secChUa)
        }
        if (hints.isChromiumBased) {
            if (force || request.header("Sec-CH-UA-Mobile") == null) {
                builder.header("Sec-CH-UA-Mobile", hints.secChUaMobile)
            }
            if (force || request.header("Sec-CH-UA-Platform") == null) {
                builder.header("Sec-CH-UA-Platform", hints.secChUaPlatform)
            }
        }

        return chain.proceed(builder.build())
    }

    companion object {
        private val hintsCache = ConcurrentHashMap<String, ClientHints>()

        private val CHROME_REGEX = """(?:Chrome|CriOS)/(\d+)""".toRegex()
        private val EDG_REGEX = """Edg(?:e|A|iOS)?/(\d+)""".toRegex()
        private val SAMSUNG_REGEX = """SamsungBrowser/(\d+)""".toRegex()
        private val OPERA_REGEX = """(?:OPR|OPT)/(\d+)""".toRegex()
        private val FIREFOX_REGEX = """Firefox/(\d+)""".toRegex()

        /**
         * Parses [userAgent] into [ClientHints], utilizing an internal thread-safe cache.
         */
        fun parse(userAgent: String): ClientHints = hintsCache.computeIfAbsent(userAgent) { parseInternal(it) }

        /**
         * Clears the internal parsed User-Agent cache.
         */
        fun clearCache() {
            hintsCache.clear()
        }

        private fun parseInternal(userAgent: String): ClientHints {
            val isMobile = userAgent.contains("Mobile", ignoreCase = true) ||
                userAgent.contains("Phone", ignoreCase = true)

            val platform = when {
                userAgent.contains("Android", ignoreCase = true) -> "Android"
                userAgent.contains("iPhone", ignoreCase = true) ||
                    userAgent.contains("iPad", ignoreCase = true) ||
                    userAgent.contains("iPod", ignoreCase = true) -> "iOS"
                userAgent.contains("Windows", ignoreCase = true) -> "Windows"
                userAgent.contains("Macintosh", ignoreCase = true) ||
                    userAgent.contains("Mac OS X", ignoreCase = true) -> "macOS"
                userAgent.contains("CrOS", ignoreCase = true) -> "Chrome OS"
                userAgent.contains("Linux", ignoreCase = true) -> "Linux"
                else -> "Android"
            }

            val chromeMatch = CHROME_REGEX.find(userAgent)
            val chromeVer = chromeMatch?.groupValues?.get(1)

            val edgMatch = EDG_REGEX.find(userAgent)
            val samsungMatch = SAMSUNG_REGEX.find(userAgent)
            val operaMatch = OPERA_REGEX.find(userAgent)
            val firefoxMatch = FIREFOX_REGEX.find(userAgent)

            val brands = mutableListOf<Pair<String, String>>()
            var isChromium = true

            when {
                edgMatch != null -> {
                    val edgVer = edgMatch.groupValues[1]
                    brands.add("Microsoft Edge" to edgVer)
                    if (chromeVer != null) brands.add("Chromium" to chromeVer)
                    brands.add("Not?A_Brand" to "99")
                }
                samsungMatch != null -> {
                    val samsungVer = samsungMatch.groupValues[1]
                    brands.add("Samsung Internet" to samsungVer)
                    if (chromeVer != null) brands.add("Chromium" to chromeVer)
                    brands.add("Not?A_Brand" to "99")
                }
                operaMatch != null -> {
                    val operaVer = operaMatch.groupValues[1]
                    brands.add("Opera" to operaVer)
                    if (chromeVer != null) brands.add("Chromium" to chromeVer)
                    brands.add("Not?A_Brand" to "99")
                }
                chromeMatch != null -> {
                    brands.add("Chromium" to chromeVer!!)
                    brands.add("Google Chrome" to chromeVer)
                    brands.add("Not?A_Brand" to "99")
                }
                firefoxMatch != null -> {
                    isChromium = false
                    val ffVer = firefoxMatch.groupValues[1]
                    brands.add("Firefox" to ffVer)
                }
                else -> {
                    isChromium = false
                }
            }

            return ClientHints(brands, isMobile, platform, isChromium)
        }
    }
}
