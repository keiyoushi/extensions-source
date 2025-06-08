package eu.kanade.tachiyomi.extension.zh.manwa

import android.content.SharedPreferences
import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException

class UpdateMirror(
    private val baseUrl: String,
    private val preferences: SharedPreferences,
) : Interceptor {
    @Volatile
    private var isUpdated = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!request.url.toString().startsWith(baseUrl)) return chain.proceed(request)

        val failedResponse = try {
            val response = chain.proceed(request)
            if (response.isSuccessful) return response
            response.close()
            Result.success(response)
        } catch (e: Exception) {
            if (chain.call().isCanceled() || e.message?.contains("Cloudflare") == true) throw e
            Result.failure(e)
        }

        if (isUpdated || updateUrl(chain)) {
            throw IOException("镜像网址已自动更新，请在插件设置中选择合适的镜像网址并重启应用（如果反复提示，可能是服务器故障）")
        }
        return failedResponse.getOrThrow()
    }

    @Synchronized
    private fun updateUrl(chain: Interceptor.Chain): Boolean {
        if (isUpdated) return true

        var url = preferences.getString(APP_REDIRECT_URL_KEY, "")!!
        if (url.isBlank()) {
            url = "https://fuwt.cc/mw666"
        }

        val request = GET(
            url = url,
            headers = Headers.headersOf(
                "Accept-Encoding",
                "gzip",
                "User-Agent",
                "okhttp/3.8.1",
            ),
        )

        try {
            chain.proceed(request).use { response ->
                if (!response.isSuccessful) {
                    return false
                }

                val extractLksBase64 = extractLksBase64(response.body.string()) ?: return false
                val extractLks =
                    String(Base64.decode(extractLksBase64, Base64.DEFAULT)).parseAs<List<String>>()
                val extractLksJson = extractLks.map { it.trimEnd('/') }.toJsonString()

                if (extractLksJson != preferences.getString(APP_URL_LIST_PREF_KEY, "")!!) {
                    preferences.edit().putString(APP_URL_LIST_PREF_KEY, extractLksJson).apply()
                }

                isUpdated = true
                return true
            }
        } catch (_: Exception) {
            return false
        }
    }

    private fun extractLksBase64(html: String): String? {
        val doc = Jsoup.parse(html)
        val scripts = doc.getElementsByTag("script")

        val prefix = "var lks = JSON.parse(atob("
        val regex = Regex("""atob\(['"]([A-Za-z0-9+/=]+)['"]\)""")

        for (script in scripts) {
            val lines = script.data().lines()
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith(prefix)) {
                    val match = regex.find(trimmedLine)
                    if (match != null) {
                        return match.groupValues[1]
                    }
                }
            }
        }
        return null
    }
}
