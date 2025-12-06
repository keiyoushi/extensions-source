package eu.kanade.tachiyomi.extension.es.barmanga

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale

class BarManga : Madara(
    "BarManga",
    "https://libribar.com",
    "es",
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            extractRealTitle(document)?.let { title = it }
            extractRealCover(document)?.let { thumbnail_url = it }
        }
    }
    private fun extractRealTitle(document: Document): String? {
        return document.selectFirst("span[property=name]")?.text()
            ?: document.selectFirst("ol.breadcrumb li:last-child a")?.text()
    }

    private fun extractRealCover(document: Document): String? {
        document.select("script:containsData(img.src)")
            .firstNotNullOfOrNull { IMG_SRC_REGEX.find(it.html())?.groupValues?.get(1) }
            ?.let { return it }

        val style = document.selectFirst("div.profile-manga")?.attr("style") ?: return null
        return BG_URL_REGEX.find(style)?.groupValues?.get(1)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        document.select("div[style*='display: none'], div[style*='display:none']").remove()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(extractChapterUrl(element))
            name = extractChapterName(element)
            date_upload = extractChapterDate(element)
        }
    }

    private fun extractChapterUrl(element: Element): String {
        element.selectFirst("span.chapter-text-content")
            ?.attr("data-original-href")
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it.decodeBase64() }

        element.attributes().asList()
            .find { it.key.startsWith("data-") && it.key.endsWith("-url") }
            ?.value
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it.decodeBase64() }

        return element.selectFirst("a")?.attr("abs:href") ?: ""
    }

    private fun extractChapterName(element: Element): String {
        return element.selectFirst("span.chapter-text-content")?.text()?.trim()
            ?: element.selectFirst("a")?.text()?.trim()
            ?: "Capítulo"
    }

    private fun extractChapterDate(element: Element): Long {
        return parseChapterDate(element.selectFirst("span.chapter-release-date")?.text())
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.page-break img").mapIndexedNotNull { index, element ->
            val obfuscatedData = element.attr("data-obfuscated")

            val imageUrl = if (obfuscatedData.isEmpty()) {
                element.attr("abs:src")
            } else {
                val decodedUrl = obfuscatedData.decodeBase64()
                val token = element.attr("data-token")
                if (token.isEmpty()) decodedUrl else "$decodedUrl#token=$token"
            }

            imageUrl.takeIf { it.startsWith("http") }?.let { Page(index, document.location(), it) }
        }
    }

    override fun imageRequest(page: Page): Request {
        val urlWithToken = page.imageUrl!!
        val cleanUrl = urlWithToken.substringBefore("#token=")
        val token = urlWithToken.substringAfter("#token=", "")

        return super.imageRequest(page).newBuilder()
            .url(cleanUrl)
            .apply { if (token.isNotEmpty()) addHeader("X-Security-Token", token) }
            .build()
    }

    private fun String.decodeBase64(): String {
        return try {
            String(Base64.decode(this, Base64.DEFAULT), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private val IMG_SRC_REGEX = """img\.src\s*=\s*['"](.*?)['"];""".toRegex()
        private val BG_URL_REGEX = """url\((.*?)\)""".toRegex()
    }
}
