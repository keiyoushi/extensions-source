package eu.kanade.tachiyomi.extension.en.dragontea

import android.util.Base64
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class DragonTea : Madara(
    "DragonTea",
    "https://dragontea.ink",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override val mangaSubString = "novel"

    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }

    private val pageIndexRegex = Regex("""image-(\d+)[a-z]+""", RegexOption.IGNORE_CASE)

    override fun pageListParse(document: Document): List<Page> {
        val dataId = document.selectFirst(".entry-header.header")?.attr("data-id")?.toInt()
            ?: return super.pageListParse(document)
        val elements = document.select(".reading-content .page-break img")
        val pageCount = elements.size

        val idKey = "11" + ((dataId + 1307) * 3 - pageCount).toString()
        elements.forEach {
            val decryptedId = decryptAesJson(it.attr("id"), idKey).jsonPrimitive.content
            it.attr("id", decryptedId)
        }

        val orderedElements = elements.sortedBy {
            pageIndexRegex.find(it.attr("id"))?.groupValues?.get(1)?.toInt() ?: 0
        }

        val dtaKey = "15" + orderedElements.joinToString("") { it.attr("id").takeLast(1) } + (((dataId + 88) * 2) - pageCount - 5).toString()
        val srcKey = (dataId + 20).toString() + orderedElements.joinToString("") {
            decryptAesJson(it.attr("dta"), dtaKey).jsonPrimitive.content.takeLast(2)
        } + (pageCount * 4).toString()

        return orderedElements.mapIndexed { i, element ->
            val src = decryptAesJson(element.attr("data-src"), srcKey).jsonPrimitive.content
            Page(i, document.location(), src)
        }
    }

    private fun decryptAesJson(ciphertext: String, key: String): JsonElement {
        val cipherData = json.parseToJsonElement(ciphertext).jsonObject

        val unsaltedCiphertext = Base64.decode(cipherData["ct"]!!.jsonPrimitive.content, Base64.DEFAULT)
        val salt = cipherData["s"]!!.jsonPrimitive.content.decodeHex()
        val saltedCiphertext = SALTED + salt + unsaltedCiphertext

        return json.parseToJsonElement(CryptoAES.decrypt(Base64.encodeToString(saltedCiphertext, Base64.DEFAULT), key))
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    companion object {
        private val SALTED = "Salted__".toByteArray(Charsets.UTF_8)
    }
}
