package eu.kanade.tachiyomi.extension.zh.picacomic

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.utils.parseAs
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.OkHttpClient
import okio.IOException
import java.net.InetAddress

class ChannelDns(
    private val baseHost: String,
    private val client: OkHttpClient,
    private val preferences: SharedPreferences,
) : Dns {

    private val defaultInitUrl = "http://68.183.234.72/init"

    private var channelHost: String? = null

    override fun lookup(hostname: String): List<InetAddress> {
        if (!hostname.endsWith(baseHost)) {
            return Dns.SYSTEM.lookup(hostname)
        }
        val ch = preferences.getString(APP_CHANNEL, "2")!!
        return when (ch) {
            "2" -> listOf(InetAddress.getByName(getChannelHost()))
            else -> Dns.SYSTEM.lookup(hostname)
        }
    }

    private fun getChannelHost(): String {
        channelHost?.let { return it }

        val chUrl =
            preferences.getString(APP_CHANNEL_URL, defaultInitUrl)?.takeIf { it.isNotBlank() }
                ?: defaultInitUrl

        val request = GET(
            url = chUrl,
            headers = Headers.headersOf(
                "Accept-Encoding",
                "gzip",
                "User-Agent",
                "okhttp/3.8.1",
            ),
        ).newBuilder().fetch(Fetch.XHR).build()

        try {
            val ch = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Unexpected ${request.url} code ${response.code}")
                }
                response.parseAs<PicaChannel>()
            }
            if (ch.status != "ok") {
                throw Exception("Unexpected ${request.url} status ${ch.status}")
            }

            return ch.addresses.firstOrNull()
                ?.also { channelHost = it }
                ?: throw Exception("Unexpected ${request.url} unable to obtain the target channel address")
        } catch (e: Exception) {
            throw IOException(e.message)
        }
    }
}
