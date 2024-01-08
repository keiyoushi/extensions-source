package eu.kanade.tachiyomi.multisrc.a3manga

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

open class A3Manga(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest: Boolean = false

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)

    override fun popularMangaSelector() = ".comic-list .comic-item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select(".comic-title-link a").attr("href"))
        title = element.select(".comic-title").text().trim()
        thumbnail_url = element.select(".img-thumbnail").attr("abs:src")
    }

    override fun popularMangaNextPageSelector() = "li.next:not(.disabled)"

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_ID_SEARCH).trim()
                fetchMangaDetails(
                    SManga.create().apply {
                        url = "/truyen-tranh/$id/"
                    },
                )
                    .map {
                        it.url = "/truyen-tranh/$id/"
                        MangasPage(listOf(it), false)
                    }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        POST(
            "$baseUrl/wp-admin/admin-ajax.php",
            headers,
            FormBody.Builder()
                .add("action", "searchtax")
                .add("keyword", query)
                .build(),
        )

    override fun searchMangaSelector(): String = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchResponseDto>()

        if (!dto.success) {
            return MangasPage(emptyList(), false)
        }

        val manga = dto.data
            .filter { it.cstatus != "Nhóm dịch" }
            .map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.link)
                    title = it.title
                    thumbnail_url = it.img
                }
            }

        return MangasPage(manga, false)
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.select(".info-title").text()
        author = document.select(".comic-info strong:contains(Tác giả) + span").text().trim()
        description = document.select(".intro-container .text-justify").text().substringBefore("— Xem Thêm —")
        genre = document.select(".comic-info .tags a").joinToString { tag ->
            tag.text().split(' ').joinToString(separator = " ") { word ->
                word.replaceFirstChar { it.titlecase() }
            }
        }
        thumbnail_url = document.select(".img-thumbnail").attr("abs:src")

        val statusString = document.select(".comic-info strong:contains(Tình trạng) + span").text()
        status = when (statusString) {
            "Đang tiến hành" -> SManga.ONGOING
            "Trọn bộ " -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListSelector(): String = ".chapter-table table tbody tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = element.select("a .hidden-sm").text()
        date_upload = runCatching {
            dateFormat.parse(element.select("td").last()!!.text())?.time
        }.getOrNull() ?: 0
    }

    protected fun decodeImgList(document: Document): String {
        val htmlContentScript = document.selectFirst("script:containsData(htmlContent)")?.html()
            ?.substringAfter("var htmlContent=\"")
            ?.substringBefore("\";")
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?.replace("\\/", "/")
            ?: throw Exception("Couldn't find script with image data.")
        val htmlContent = json.decodeFromString<CipherDto>(htmlContentScript)
        val ciphertext = Base64.decode(htmlContent.ciphertext, Base64.DEFAULT)
        val iv = htmlContent.iv.decodeHex()
        val salt = htmlContent.salt.decodeHex()

        val passwordScript = document.selectFirst("script:containsData(chapterHTML)")?.html()
            ?: throw Exception("Couldn't find password to decrypt image data.")
        val passphrase = passwordScript.substringAfter("var chapterHTML=CryptoJSAesDecrypt('")
            .substringBefore("',htmlContent")
            .replace("'+'", "")

        val keyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 999, 256)
        val keyS = SecretKeySpec(keyFactory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyS, IvParameterSpec(iv))

        val imgListHtml = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)

        return imgListHtml
    }

    override fun pageListParse(document: Document): List<Page> {
        val imgListHtml = decodeImgList(document)

        return Jsoup.parseBodyFragment(imgListHtml).select("img").mapIndexed { idx, element ->
            val encryptedUrl = element.attributes().find { it.key.startsWith("data") }?.value
            val effectiveUrl = encryptedUrl?.decodeUrl() ?: element.attr("abs:src")
            Page(idx, imageUrl = effectiveUrl)
        }
    }

    private fun String.decodeUrl(): String? {
        // We expect the URL to start with `https://`, where the last 3 characters are encoded.
        // The length of the encoded character is not known, but it is the same across all.
        // Essentially we are looking for the two encoded slashes, which tells us the length.
        val patternIdx = patternsLengthCheck.indexOfFirst { pattern ->
            val matchResult = pattern.find(this)
            val g1 = matchResult?.groupValues?.get(1)
            val g2 = matchResult?.groupValues?.get(2)
            g1 == g2 && g1 != null
        }
        if (patternIdx == -1) {
            return null
        }

        // With a known length we can predict all the encoded characters.
        // This is a slightly more expensive pattern, hence the separation.
        val matchResult = patternsSubstitution[patternIdx].find(this)
        return matchResult?.destructured?.let { (colon, slash, period) ->
            this
                .replace(colon, ":")
                .replace(slash, "/")
                .replace(period, ".")
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    // https://stackoverflow.com/a/66614516
    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    companion object {
        const val KEY_ALGORITHM = "PBKDF2WithHmacSHA512"
        const val CIPHER_TRANSFORMATION = "AES/CBC/PKCS7PADDING"

        const val PREFIX_ID_SEARCH = "id:"
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }

        private val patternsLengthCheck: List<Regex> = (20 downTo 1).map { i ->
            """^https.{$i}(.{$i})(.{$i})""".toRegex()
        }
        private val patternsSubstitution: List<Regex> = (20 downTo 1).map { i ->
            """^https(.{$i})(.{$i}).*(.{$i})(?:webp|jpeg|tiff|.{3})$""".toRegex()
        }
    }
}
