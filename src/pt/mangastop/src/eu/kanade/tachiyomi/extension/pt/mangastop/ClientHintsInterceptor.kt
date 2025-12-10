package eu.kanade.tachiyomi.extension.pt.mangastop

import okhttp3.Interceptor
import okhttp3.Response

class ClientHintsInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val userAgent = request.header("User-Agent")
            ?: return chain.proceed(request)

        val chromeVersion = CHROME_REGEX.find(userAgent)?.groupValues?.get(1) ?: "134"
        val isMobile = userAgent.contains("Android") || userAgent.contains("Mobile")
        val isEdge = userAgent.contains("Edg/")

        val secChUa = if (isEdge) {
            val edgeVersion = EDGE_REGEX.find(userAgent)?.groupValues?.get(1) ?: chromeVersion
            "\"Microsoft Edge\";v=\"$edgeVersion\", \"Chromium\";v=\"$chromeVersion\", \"Not?A_Brand\";v=\"99\""
        } else {
            "\"Chromium\";v=\"$chromeVersion\", \"Google Chrome\";v=\"$chromeVersion\", \"Not?A_Brand\";v=\"99\""
        }

        val platform = when {
            userAgent.contains("Windows") -> "\"Windows\""
            userAgent.contains("Android") -> "\"Android\""
            userAgent.contains("Mac") -> "\"macOS\""
            userAgent.contains("Linux") -> "\"Linux\""
            else -> "\"Windows\""
        }

        val newRequest = request.newBuilder()
            .header("Sec-Ch-Ua", secChUa)
            .header("Sec-Ch-Ua-Mobile", if (isMobile) "?1" else "?0")
            .header("Sec-Ch-Ua-Platform", platform)
            .build()

        return chain.proceed(newRequest)
    }

    companion object {
        private val CHROME_REGEX = Regex("""Chrome/(\d+)""")
        private val EDGE_REGEX = Regex("""Edg/(\d+)""")
    }
}
