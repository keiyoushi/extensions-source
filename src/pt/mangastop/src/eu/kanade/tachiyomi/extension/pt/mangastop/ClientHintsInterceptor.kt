package eu.kanade.tachiyomi.extension.pt.mangastop

import okhttp3.Interceptor
import okhttp3.Response

class ClientHintsInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val userAgent = request.header("User-Agent")
            ?: return chain.proceed(request)

        val browserInfo = detectBrowser(userAgent)
            ?: return chain.proceed(request)

        val isMobile = userAgent.contains("Mobile") ||
            userAgent.contains("Android") ||
            userAgent.contains("iPhone") ||
            userAgent.contains("iPad")

        val platform = detectPlatform(userAgent)
        val secChUa = buildSecChUa(browserInfo)

        val newRequest = request.newBuilder()
            .header("Sec-CH-UA", secChUa)
            .header("Sec-CH-UA-Mobile", if (isMobile) "?1" else "?0")
            .header("Sec-CH-UA-Platform", platform)
            .header("DNT", "1")
            .build()

        return chain.proceed(newRequest)
    }

    private fun detectBrowser(userAgent: String): BrowserInfo? = when {
        userAgent.contains("Firefox/") && !userAgent.contains("Chrome") -> null

        userAgent.contains("Safari/") &&
            !userAgent.contains("Chrome") &&
            !userAgent.contains("Chromium") -> null

        userAgent.contains("Edg/") || userAgent.contains("EdgA/") || userAgent.contains("EdgiOS/") -> {
            val edgeVersion = EDGE_REGEX.find(userAgent)?.groupValues?.get(1) ?: "134"
            val chromiumVersion = CHROME_REGEX.find(userAgent)?.groupValues?.get(1) ?: edgeVersion
            BrowserInfo("Microsoft Edge", edgeVersion, chromiumVersion)
        }

        userAgent.contains("OPR/") -> {
            val operaVersion = OPERA_REGEX.find(userAgent)?.groupValues?.get(1) ?: "118"
            val chromiumVersion = CHROME_REGEX.find(userAgent)?.groupValues?.get(1) ?: "134"
            BrowserInfo("Opera", operaVersion, chromiumVersion)
        }

        userAgent.contains("Chrome/") -> {
            val chromeVersion = CHROME_REGEX.find(userAgent)?.groupValues?.get(1) ?: "134"
            BrowserInfo("Google Chrome", chromeVersion, chromeVersion)
        }

        else -> null
    }

    private fun detectPlatform(userAgent: String): String = when {
        userAgent.contains("Windows") -> "\"Windows\""
        userAgent.contains("Android") -> "\"Android\""
        userAgent.contains("iPhone") || userAgent.contains("iPad") -> "\"iOS\""
        userAgent.contains("Macintosh") || userAgent.contains("Mac OS X") -> "\"macOS\""
        userAgent.contains("Linux") -> "\"Linux\""
        else -> "\"Windows\""
    }

    private fun buildSecChUa(info: BrowserInfo): String = "\"${info.name}\";v=\"${info.version}\", \"Chromium\";v=\"${info.chromiumVersion}\", \"Not A(Brand\";v=\"24\""

    private data class BrowserInfo(
        val name: String,
        val version: String,
        val chromiumVersion: String,
    )

    companion object {
        private val CHROME_REGEX = Regex("""Chrome/(\d+)""")
        private val EDGE_REGEX = Regex("""Edg[^/]*/(\d+)""")
        private val OPERA_REGEX = Regex("""OPR/(\d+)""")
    }
}
