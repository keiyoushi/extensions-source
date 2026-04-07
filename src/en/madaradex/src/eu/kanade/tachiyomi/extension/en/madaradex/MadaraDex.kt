package eu.kanade.tachiyomi.extension.en.madaradex

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Locale

class MadaraDex :
    Madara(
        "MadaraDex",
        "https://madaradex.org",
        "en",
        dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
    ) {
    override fun headersBuilder() = super.headersBuilder()
        .set("sec-fetch-site", "same-site")

    override val mangaSubString = "title"

    private val siteUrl by lazy { baseUrl.toHttpUrl() }

    private fun randomHex16() = ByteArray(16)
        .also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    private fun refreshAuth() {
        if (client.cookieJar.loadForRequest(siteUrl).none { it.name == "mdx_fp" }) {
            client.cookieJar.saveFromResponse(
                siteUrl,
                listOf(
                    Cookie.Builder()
                        .domain("madaradex.org").path("/")
                        .name("mdx_fp").value(randomHex16())
                        .expiresAt(System.currentTimeMillis() + 2592000000L)
                        .build(),
                ),
            )
        }
        runCatching {
            client.newCall(
                Request.Builder()
                    .url("$baseUrl/wp-admin/admin-ajax.php")
                    .post(FormBody.Builder().add("action", "mdx_auth_refresh").build())
                    .header("X-Mdx-Auth-Refresh", "1")
                    .build(),
            ).execute().close()
        }
    }

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.header("X-Mdx-Auth-Refresh") != null) {
                return@addInterceptor chain.proceed(
                    request.newBuilder().removeHeader("X-Mdx-Auth-Refresh").build(),
                )
            }
            client.cookieJar.loadForRequest(siteUrl).let {
                if (it.none { c -> c.name == "mdx_fp" } || it.none { c -> c.name == "mdx_auth" }) refreshAuth()
            }
            chain.proceed(request).also {
                if (it.code == 403 && request.url.host == "cdn.madaradex.org") {
                    it.close()
                    refreshAuth()
                    return@addInterceptor chain.proceed(request)
                }
            }
        }
        .build()
}
