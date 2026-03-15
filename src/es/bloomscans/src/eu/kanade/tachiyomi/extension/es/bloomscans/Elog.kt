package eu.kanade.tachiyomi.extension.es.bloomscans

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

private const val TAG = "ELOGGER"

class Elog : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        Log.d(
            TAG,
            buildString {
                appendLine("***************Request**********")
                appendLine("URL         : ${request.url}")
                appendLine("Method      : ${request.method}")
                // append("Content-Type: ${request.body?.contentType() ?: "N/A"}")
                // appendLine("\n***********************************")
            },
        )

        val response = chain.proceed(request)

        // if (response.code != 200) {
        Log.d(
            TAG,
            buildString {
                appendLine("***************RESPONSE**********")
                appendLine("URL         : ${response.request.url}")
                appendLine("Status Code : ${response.code} ${response.message}")
                append("Content-Type: ${response.header("Content-Type") ?: "N/A"}")
                appendLine("\n***********************************")
            },
        )
        // }

        return response
    }
}
