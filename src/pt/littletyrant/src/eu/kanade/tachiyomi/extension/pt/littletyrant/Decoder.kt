package eu.kanade.tachiyomi.extension.pt.littletyrant

import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.parseAs
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document

private val XOR_KEY_REGEX = Regex("""xorKey\s*=\s*'([^']+)'""")
private val PROXY_URLS_REGEX = Regex("""proxyUrls\s*=\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)

class Decoder(private val client: OkHttpClient, private val baseUrl: String) {
    private var xorKey = ""

    fun getImageUrls(document: Document): List<String> {
        val token = fetchToken()
        val paths = extractKeyAndPaths(document)
        return paths.map { "$baseUrl$it#token=$token" }
    }

    fun decrypt(response: Response): Response {
        val bytes = response.body!!.bytes()
        val key = xorKey.toByteArray()

        for (i in 0 until minOf(1024, bytes.size)) {
            bytes[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }

        return response.newBuilder()
            .body(bytes.toResponseBody(response.body!!.contentType()))
            .build()
    }

    private fun fetchToken(): String {
        val res = client.newCall(GET("$baseUrl/wp-content/themes/madara2/token-generator.php")).execute()
        return res.parseAs<TokenDto>().token
    }

    private fun extractKeyAndPaths(document: Document): List<String> {
        val xorKeyScript = document.select("script:containsData(xorKey)").first()?.data() ?: error("No XOR key")
        xorKey = XOR_KEY_REGEX.find(xorKeyScript)!!.groupValues.get(1)

        val urlScript = document.select("script:containsData(proxyUrls)").first()?.data() ?: error("No image URLS")
        return PROXY_URLS_REGEX.find(urlScript)!!.groupValues.get(1)
            .split(",")
            .map { it.trim().trim('"') }
    }
}
