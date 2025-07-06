package eu.kanade.tachiyomi.extension.zh.manxiaosi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okio.IOException

class UrlRedirect(val client: OkHttpClient, val headers: Headers) {
    fun redirect(baseUrl: String): List<String> {
        var response = client.newCall(GET(baseUrl, headers)).execute()

        if (!response.isSuccessful) {
            throw IOException("$baseUrl call failed: ${response.code}")
        }
        val targetUrl = response.asJsoup().selectFirst("#js-alert-btn")!!.attr("href")

        response = client.newCall(GET(targetUrl, headers)).execute()
        if (!response.isSuccessful) {
            throw IOException("$targetUrl call failed: ${response.code}")
        }

        val lis = mutableListOf<String>()
        response.asJsoup().select("div.login-box1 > ul > div > p > a").forEach {
            lis.add(it.attr("href"))
        }

        return lis
    }
}
