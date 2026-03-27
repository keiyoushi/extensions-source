package eu.kanade.tachiyomi.extension.vi.truyentuoitho

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class TruyenTuoiTho :
    Madara(
        "TruyenTuoiTho",
        "https://truyentuoitho.com",
        "vi",
        SimpleDateFormat("dd/MM/yyyy", Locale("vi")),
    ) {

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override val filterNonMangaItems = false

    override val useNewChapterEndpoint = true

    override fun xhrChaptersRequest(mangaUrl: String): Request {
        val normalizedMangaUrl = mangaUrl.removeSuffix("/")
        val chapterHeaders = xhrHeaders.newBuilder()
            .set("Referer", "$normalizedMangaUrl/")
            .set("Origin", baseUrl)
            .build()

        return POST("$normalizedMangaUrl/ajax/chapters/?t=1", chapterHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        val defaultPages = super.pageListParse(document)
        if (defaultPages.isNotEmpty()) return defaultPages

        val script = document.select("div.reading-content script")
            .asSequence()
            .map { it.data() }
            .firstOrNull { it.contains("split('').reverse().join('')") && it.contains("JSON[atob('cGFyc2U=')]") }
            ?: return emptyList()

        val payloadRegex = Regex(
            """const\s+[^;]+?=atob\('([^']+)'\),[^;]+?=atob\('([^']+)'\),[^;]+?=atob\('([^']+)'\),[^;]+?=atob\('([^']+)'\),[^;]+?='([^']+)'""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val match = payloadRegex.find(script) ?: return emptyList()

        val key = runCatching {
            buildString {
                append(match.groupValues[1].decodeBase64())
                append(match.groupValues[2].decodeBase64())
                append(match.groupValues[3].decodeBase64())
                append(match.groupValues[4].decodeBase64())
            }
        }.getOrElse { return emptyList() }
        if (key.isEmpty()) return emptyList()

        val encrypted = match.groupValues[5]
        val decodedPayload = runCatching {
            encrypted.reversed().decodeBase64().xorWithKey(key)
        }.getOrElse { return emptyList() }
        val images = runCatching {
            json.parseToJsonElement(decodedPayload)
                .jsonObject["images"]
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }
                .orEmpty()
        }.getOrElse { emptyList() }

        return images.mapIndexed { index, imageUrl ->
            Page(index, document.location(), imageUrl)
        }
    }

    private fun String.decodeBase64(): String = String(Base64.decode(this, Base64.DEFAULT), Charsets.UTF_8)

    private fun String.xorWithKey(key: String): String {
        val output = CharArray(length)
        forEachIndexed { index, ch ->
            output[index] = (ch.code xor key[index % key.length].code).toChar()
        }
        return String(output)
    }
}
