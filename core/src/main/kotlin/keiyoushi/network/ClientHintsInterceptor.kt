package keiyoushi.network

import okhttp3.Interceptor
import okhttp3.Response

object ClientHintsInterceptor : Interceptor {
    private val CHROME_REGEX = Regex("""Chrome/(\d+)""")
    private val EDGE_REGEX = Regex("""Edg[^/]*/(\d+)""")
    private val OPERA_REGEX = Regex("""OPR/(\d+)""")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val userAgent = request.header("User-Agent")

        if (userAgent.isNullOrEmpty()) {
            return chain.proceed(request)
        } else {
            val (name, version, chromiumVersion) = when {
                userAgent.contains("Firefox/") && !userAgent.contains("Chrome") -> return chain.proceed(request)

                userAgent.contains("Safari/") &&
                    !userAgent.contains("Chrome") &&
                    !userAgent.contains("Chromium") -> return chain.proceed(request)

                userAgent.contains("Edg/") || userAgent.contains("EdgA/") || userAgent.contains("EdgiOS/") -> {
                    val edgeVersion = EDGE_REGEX.find(userAgent)?.groupValues?.get(1) ?: "134"
                    val chromiumVersion = CHROME_REGEX.find(userAgent)?.groupValues?.get(1) ?: edgeVersion
                    Triple("Microsoft Edge", edgeVersion, chromiumVersion)
                }

                userAgent.contains("OPR/") -> {
                    val operaVersion = OPERA_REGEX.find(userAgent)?.groupValues?.get(1) ?: "118"
                    val chromiumVersion = CHROME_REGEX.find(userAgent)?.groupValues?.get(1) ?: "134"
                    Triple("Opera", operaVersion, chromiumVersion)
                }

                userAgent.contains("Chrome/") -> {
                    val chromeVersion = CHROME_REGEX.find(userAgent)?.groupValues?.get(1) ?: "134"
                    Triple("Google Chrome", chromeVersion, chromeVersion)
                }

                else -> return chain.proceed(request)
            }

            val isMobile = userAgent.contains("Mobile") ||
                userAgent.contains("Android") ||
                userAgent.contains("iPhone") ||
                userAgent.contains("iPad")

            val platform = when {
                userAgent.contains("Windows") -> "\"Windows\""
                userAgent.contains("Android") -> "\"Android\""
                userAgent.contains("iPhone") || userAgent.contains("iPad") -> "\"iOS\""
                userAgent.contains("Macintosh") || userAgent.contains("Mac OS X") -> "\"macOS\""
                userAgent.contains("Linux") -> "\"Linux\""
                else -> "\"Windows\""
            }

            return chain.proceed(
                request
                    .newBuilder()
                    .header("Sec-CH-UA", "\"$name\";v=\"$version\", \"Chromium\";v=\"$chromiumVersion\", \"Not A(Brand\";v=\"24\"")
                    .header("Sec-CH-UA-Mobile", if (isMobile) "?1" else "?0")
                    .header("Sec-CH-UA-Platform", platform)
                    .build(),
            )
        }
    }
}
