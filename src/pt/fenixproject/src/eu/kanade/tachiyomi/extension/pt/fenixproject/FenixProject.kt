package eu.kanade.tachiyomi.extension.pt.fenixproject

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.lib.cryptoaes.CryptoAES
import keiyoushi.network.rateLimit
import keiyoushi.utils.decodeHex
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class FenixProject :
    Madara(
        "Fenix Project",
        "https://fenixproject.site",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val chapterUrlSuffix = ""

    override fun pageListRequest(chapter: SChapter): Request {
        val request = super.pageListRequest(chapter)

        // Bypass LiteSpeed Cache to ensure the server returns the unoptimized HTML
        // containing the raw image elements instead of stripped lazy-load placeholders.
        val url = request.url.newBuilder()
            .addQueryParameter("nocache", System.currentTimeMillis().toString())
            .build()

        return request.newBuilder()
            .url(url)
            .build()
    }

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        var chapterData: String? = null
        var password: String? = null

        val chapterDataRegex = Regex("""chapter_data\s*=\s*(?:'([^']*)'|"((?:\\"|[^"])*)")""")
        val nonceRegex = Regex("""wpmangaprotectornonce\s*=\s*(?:'([^']*)'|"((?:\\"|[^"])*)")""")

        fun extractData(text: String) {
            if (chapterData == null) {
                chapterDataRegex.find(text)?.let { match ->
                    chapterData = match.groupValues[1].takeIf { it.isNotEmpty() }
                        ?: match.groupValues[2].replace("\\\"", "\"")
                }
            }
            if (password == null) {
                nonceRegex.find(text)?.let { match ->
                    password = match.groupValues[1].takeIf { it.isNotEmpty() }
                        ?: match.groupValues[2].replace("\\\"", "\"")
                }
            }
        }

        // First check standard script element from Madara
        val chapterProtector = document.selectFirst(chapterProtectorSelector)
        if (chapterProtector != null) {
            val chapterProtectorHtml = chapterProtector.attr("src")
                .takeIf { it.startsWith("data:text/javascript;base64,") }
                ?.substringAfter("data:text/javascript;base64,")
                ?.let { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) }
                ?: chapterProtector.html()

            extractData(chapterProtectorHtml)
        }

        // Check raw HTML in case it's inline
        extractData(document.html())

        // If still missing, check LiteSpeed cache JS
        if (chapterData == null || password == null) {
            document.select("script[src*=/litespeed/js/]").forEach { script ->
                if (chapterData != null && password != null) return@forEach
                val litespeedScript = script.attr("abs:src")
                try {
                    val jsResponse = client.newCall(GET(litespeedScript, headers)).execute().body.string()
                    extractData(jsResponse)
                } catch (_: Exception) {
                    // Ignore and continue fallback
                }
            }
        }

        if (chapterData != null && password != null) {
            val chapterDataJson = json.parseToJsonElement(chapterData.replace("\\/", "/")).jsonObject
            val unsaltedCiphertext = Base64.decode(
                chapterDataJson["ct"]!!.jsonPrimitive.content,
                Base64.DEFAULT,
            )
            val salt = chapterDataJson["s"]!!.jsonPrimitive.content.decodeHex()
            val ciphertext = salted + salt + unsaltedCiphertext

            val rawImgArray = CryptoAES.decrypt(Base64.encodeToString(ciphertext, Base64.DEFAULT), password)
            val imgArrayString = json.parseToJsonElement(rawImgArray).jsonPrimitive.content
            val imgArray = json.parseToJsonElement(imgArrayString).jsonArray

            return imgArray.mapIndexed { idx, it ->
                Page(idx, document.location(), it.jsonPrimitive.content)
            }
        }

        return document.select(pageListParseSelector).mapIndexed { index, element ->
            val imageUrl = element.selectFirst("img")?.let { imageFromElement(it) }
            Page(index, document.location(), imageUrl)
        }
    }
}
