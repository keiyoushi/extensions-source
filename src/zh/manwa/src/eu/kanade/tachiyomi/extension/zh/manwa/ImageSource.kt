package eu.kanade.tachiyomi.extension.zh.manwa

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.toJsonString
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

class ImageSource(
    private val baseUrl: String,
    private val preferences: SharedPreferences,
) : Interceptor {
    @Volatile
    private var isUpdated = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (!request.url.toString().startsWith(baseUrl)) return chain.proceed(request)

        if (!isUpdated && updateList(chain)) {
            throw java.io.IOException("图源列表已自动更新，请在插件设置中选择合适的图源并重新请求（如果反复提示，可能是服务器故障）")
        }

        return chain.proceed(request)
    }

    @Synchronized
    private fun updateList(chain: Interceptor.Chain): Boolean {
        if (isUpdated) {
            return false
        }
        val request = GET(
            url = baseUrl,
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
                    throw Exception("Unexpected ${request.url} to update image source")
                }

                val document = response.asJsoup()
                val modalBody = document.selectFirst("#img-host-modal > div.modal-body")
                val links = modalBody?.select("a") ?: emptyList()

                val infoList = arrayListOf(ImageSourceInfo("None", ""))
                for (link in links) {
                    val href = link.attr("href")
                    val text = link.text()
                    infoList.add(ImageSourceInfo(text, href))
                }
                val newList = infoList.toJsonString()

                isUpdated = true
                if (newList != preferences.getString(APP_IMAGE_SOURCE_LIST_KEY, "")!!) {
                    preferences.edit().putString(APP_IMAGE_SOURCE_LIST_KEY, newList).apply()
                    return true
                } else {
                    return false
                }
            }
        } catch (_: Exception) {
            return false
        }
    }
}

@Serializable
data class ImageSourceInfo(
    val name: String,
    val param: String,
)
