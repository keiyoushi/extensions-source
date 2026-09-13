package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

@Source
abstract class XXXYaoi : MadaraNoAjax() {

    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds)

    override fun Headers.Builder.configureHeaders() = set("Upgrade-Insecure-Requests", "1")
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .set("Sec-GPC", "1")
        .set("Sec-Fetch-User", "?1")
        .set("Sec-Fetch-Site", "none")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Dest", "document")
        .set("Priority", "u=0, i")
        .set("Pragma", "no-cache")

    override val mangaSubString = "bl"

    override val mangaDetailsSelectorTitle = ".xyaoi-main-title, h1"
    override val mangaDetailsSelectorAuthor = "a[href*=author]"
    override val mangaDetailsSelectorArtist = "a[href*=artist]"
    override val mangaDetailsSelectorStatus = "span:contains(status) + span"
    override val mangaDetailsSelectorDescription = "[class*=synopsis]"

    override fun searchCardSelector() = ".xyaoi-search-card"

    override val archiveUrlSelector = "h3 > a"

    override fun chapterFromElement(element: Element, mangaPath: String) = super.chapterFromElement(element, mangaPath)?.apply {
        name = element.selectFirst("div > span:nth-child(1)")!!.text()
        date_upload = parseChapterDate(element.selectFirst("div:has(> span:nth-child(1)) + div")?.text())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return getPages(document)
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
            .takeUnless(List<Page>::isEmpty)
            ?: super.getPageList(chapter)
    }

    private fun getPages(document: Document): List<String> {
        val script = document.selectFirst("script:containsData(page-break)")?.data() ?: return emptyList()
        val key = PAGE_KEY_REGEX.find(script)!!.groupValues.last()
        val attr = PAYLOAD_ATTR_REGEX.find(script)!!.groupValues.last()

        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val encrypted = document.selectFirst("[$attr]")!!.attr(attr)

        val decodedBytes = Base64.decode(encrypted, Base64.DEFAULT)
        val decryptedBytes = ByteArray(decodedBytes.size) { i ->
            (decodedBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }
        return String(decryptedBytes, Charsets.UTF_8).parseAs<List<String>>()
    }

    companion object {
        private val PAGE_KEY_REGEX = """key\s+=\s+.([^']+)""".toRegex()
        private val PAYLOAD_ATTR_REGEX = """=\s+?'(data[^']+)""".toRegex()
    }
}
