package eu.kanade.tachiyomi.extension.zh.picacomic

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
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

    private var channel = listOf<String>()

    override fun lookup(hostname: String): List<InetAddress> {
        if (!hostname.endsWith(baseHost)) {
            return Dns.SYSTEM.lookup(hostname)
        }
        val ch = preferences.getString(APP_CHANNEL, "2")!!
        return when (ch) {
            "2" -> listOf(InetAddress.getByName(getChannelHost(0)))
            "3" -> listOf(InetAddress.getByName(getChannelHost(1)))
            else -> Dns.SYSTEM.lookup(hostname)
        }
    }

    private fun getChannelHost(index: Int): String {
        if (channel.size > index) {
            return channel[index]
        }

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
        )

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Unexpected ${request.url} code ${response.code}")
            }

            val responseBody = response.body.string()

            val ch = responseBody.parseAs<PicaChannel>()
            if (ch.status != "ok") {
                throw Exception("Unexpected ${request.url} status ${ch.status}")
            }

            channel = ch.addresses
            if (channel.size <= index) {
                throw Exception("Unexpected ${request.url} unable to obtain the target channel address")
            }
            return channel[index]
        } catch (e: Exception) {
            throw IOException(e.message)
        }
    }
}
