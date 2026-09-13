package eu.kanade.tachiyomi.extension.vi.truyentuoitho

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TruyenTuoiTho : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override val filterNonMangaItems = false

    override val chapterMode = ChapterMode.MangaAjaxPaginated

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl).asJsoup()
        val defaultPages = super.parsePages(document)
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
    private class ChapterImagesPayload(
        val images: List<String> = emptyList(),
    )

    companion object {
        private val protectedPayloadRegex = Regex(
            """const\s+[^;]+?=atob\('([^']+)'\),[^;]+?=atob\('([^']+)'\),[^;]+?=atob\('([^']+)'\),[^;]+?=atob\('([^']+)'\),[^;]+?='([^']+)'""",
            RegexOption.DOT_MATCHES_ALL,
        )
    }
}
