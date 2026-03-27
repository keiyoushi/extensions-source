package eu.kanade.tachiyomi.extension.vi.truyentuoitho

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
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

    private val chapterDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("vi"))

    override val filterNonMangaItems = false

    override val useNewChapterEndpoint = true

    override fun parseChapterDate(date: String?): Long {
        val parsed = chapterDateFormat.tryParse(date?.trim())
        return if (parsed != 0L) parsed else super.parseChapterDate(date)
    }

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

        val decodedPayload = document.select("div.reading-content script")
            .map { script ->
                runCatching { decodeProtectedPayload(script.data()) }.getOrNull()
            }
            .firstInstanceOrNull<String>()
            ?: return emptyList()

        val images = runCatching {
            decodedPayload.parseAs<ChapterImagesPayload>().images
        }.getOrElse { emptyList() }

        return images.mapIndexed { index, imageUrl ->
            Page(index, document.location(), imageUrl)
        }
    }

    private fun decodeProtectedPayload(script: String): String {
        if (!script.contains("split('').reverse().join('')") || !script.contains("JSON[atob('cGFyc2U=')]")) {
            error("Not a protected payload script")
        }

        val match = protectedPayloadRegex.find(script) ?: error("Protected payload not found")
        val key = buildString {
            append(match.groupValues[1].decodeBase64())
            append(match.groupValues[2].decodeBase64())
            append(match.groupValues[3].decodeBase64())
            append(match.groupValues[4].decodeBase64())
        }
        if (key.isEmpty()) error("Protected payload key is empty")

        val encrypted = match.groupValues[5]
        return encrypted.reversed().decodeBase64().xorWithKey(key)
    }

    private fun String.decodeBase64(): String = String(Base64.decode(this, Base64.DEFAULT), Charsets.UTF_8)

    private fun String.xorWithKey(key: String): String {
        val output = CharArray(length)
        forEachIndexed { index, ch ->
            output[index] = (ch.code xor key[index % key.length].code).toChar()
        }
        return String(output)
    }

    @Serializable
    private data class ChapterImagesPayload(
        val images: List<String> = emptyList(),
    )

    companion object {
        private val protectedPayloadRegex = Regex(
            """const\s+[^;]+?=atob\('([^']+)'\),[^;]+?=atob\('([^']+)'\),[^;]+?=atob\('([^']+)'\),[^;]+?=atob\('([^']+)'\),[^;]+?='([^']+)'""",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
