package eu.kanade.tachiyomi.extension.en.hiperdex

import okhttp3.Interceptor
import okhttp3.Response

class ClearanceInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val hasClearanceCookie = request.headers("Cookie").any { cookieHeader ->
            cookieHeader.split(";").any { cookie ->
                cookie.trim().startsWith("$COOKIE_NAME=")
            }
        }

        if (hasClearanceCookie) return chain.proceed(request)

        val response = chain.proceed(request)
        if (response.code in ERROR_CODES && response.header("Server") in SERVER_CHECK) {
            val hasClearanceInResponse = response.headers("Set-Cookie").any { cookieHeader ->
                cookieHeader.split(";").any { cookie ->
                    cookie.trim().startsWith("$COOKIE_NAME=")
                }
            }

            if (hasClearanceInResponse) return response

            val newResponse = response.newBuilder()
                .header(
                    "Set-Cookie",
                    "$COOKIE_NAME=; Path=/; Domain=${request.url.host}",
                )
                .body(response.body)
                .build()

            return newResponse
        }
        return response
    }

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        private const val COOKIE_NAME = "cf_clearance"
    }
}
