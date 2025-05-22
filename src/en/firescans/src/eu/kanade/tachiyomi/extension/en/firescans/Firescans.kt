package eu.kanade.tachiyomi.extension.en.firescans

import android.util.Base64
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.network.rateLimit
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

class Firescans : Madara("Firescans", "https://firescans.xyz", "en") {

    override val id: Long = 5761461704760730187
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 5.seconds)
        .build()

    override val useNewChapterEndpoint: Boolean = true

    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        val chapterProtector = document.selectFirst(chapterProtectorSelector)
            ?: return document.select(pageListParseSelector).mapIndexed { index, element ->
                val imageUrl = element.selectFirst("img")?.let { imageFromElement(it) }
                Page(index, document.location(), imageUrl)
            }

        val chapterProtectorHtml = if (chapterProtector.attr("src").startsWith("data:text/javascript;base64,")) {
            Base64.decode(chapterProtector.attr("src").substringAfter(","), Base64.DEFAULT).decodeToString()
        } else {
            chapterProtector.html()
        }

        val password = chapterProtectorHtml
            .substringAfter("wpmangaprotectornonce='")
            .substringBefore("';")
        val chapterData = json.parseToJsonElement(
            chapterProtectorHtml
                .substringAfter("chapter_data='")
                .substringBefore("';")
                .replace("\\/", "/"),
        ).jsonObject

        val unsaltedCiphertext = Base64.decode(chapterData["ct"]!!.jsonPrimitive.content, Base64.DEFAULT)
        val salt = chapterData["s"]!!.jsonPrimitive.content.decodeHex()
        val ciphertext = salted + salt + unsaltedCiphertext

        val rawImgArray = CryptoAES.decrypt(Base64.encodeToString(ciphertext, Base64.DEFAULT), password)
        val imgArrayString = json.parseToJsonElement(rawImgArray).jsonPrimitive.content
        val imgArray = json.parseToJsonElement(imgArrayString).jsonArray

        return imgArray.mapIndexed { idx, it ->
            Page(idx, document.location(), it.jsonPrimitive.content)
        }
    }
}
