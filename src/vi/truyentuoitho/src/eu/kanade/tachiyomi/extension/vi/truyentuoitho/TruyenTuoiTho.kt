package eu.kanade.tachiyomi.extension.vi.truyentuoitho

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
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

    // Plain chapters put every page image in one .text-left block, so select the images themselves.
    override val pageListParseSelector = "div.page-break, li.blocks-gallery-item, .reading-content .text-left:not(:has(.blocks-gallery-item)) img"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
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
        if (!script.contains("split('').reverse().join('')")) {
            error("Not a protected payload script")
        }

        val match = protectedPayloadRegex.find(script) ?: error("Protected payload not found")
        val key = atobRegex.findAll(match.groupValues[1]).joinToString("") { it.groupValues[1].decodeBase64() }
        if (key.isEmpty()) error("Protected payload key is empty")

        val encrypted = match.groupValues[2]
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
        // The key is every atob() piece in the const, either "a=atob(..),b=atob(..)" or "d=atob(..)+atob(..)",
        // and the payload is the string assigned right after them.
        private val protectedPayloadRegex = Regex("""const\s+((?:\w+=)?(?:atob\('[^']+'\)[+,]\s*(?:\w+=)?)+)'([^']+)'""")
        private val atobRegex = Regex("""atob\('([^']+)'\)""")
    }
}
