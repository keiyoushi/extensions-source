package eu.kanade.tachiyomi.extension.en.earlymanga

import android.util.Base64
import eu.kanade.tachiyomi.extension.R
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.absoluteValue
import kotlin.random.Random

class EarlyManga : ParsedHttpSource() {

    override val name = "EarlyManga"

    override val baseUrl = "https://earlycomic.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val userAgentRandomizer1 = "${Random.nextInt(9).absoluteValue}"
    private val userAgentRandomizer2 = "${Random.nextInt(10,99).absoluteValue}"
    private val userAgentRandomizer3 = "${Random.nextInt(100,999).absoluteValue}"

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/8$userAgentRandomizer1.0.4$userAgentRandomizer3.1$userAgentRandomizer2 Safari/537.36",
        )
        .add("Referer", baseUrl)

    // popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/hot-manga?page=$page", headers)

    override fun popularMangaSelector() = "div.content-homepage-item"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.select("a").attr("abs:href").substringAfter(baseUrl)
        manga.title = element.select(".colum-content a.homepage-item-title").text()
        manga.thumbnail_url = element.select("a img").attr("abs:src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "li.paging:not(.disabled)"

    // latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesSelector() = ".container > .main-content .content-homepage-item"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = ".load-data-btn"

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?search=$query", headers)
    }

    override fun searchMangaSelector() = "div.manga-entry"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.select("a").attr("abs:href").substringAfter(baseUrl)
        manga.title = element.select("a.manga_title").attr("title")
        manga.thumbnail_url = element.select("a img").attr("abs:src")
        return manga
    }

    override fun searchMangaNextPageSelector(): String? = null

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        thumbnail_url = document.select(".manga-page-img").attr("abs:src")
        title = document.select("title").text()
        author = document.select(".author-link a").text()
        artist = document.select(".artist-link a").text()
        status = parseStatus(document.select(".pub_stutus").text())
        description = document.select(".desc:not([class*=none])").text().replace("_", "")
        genre = document.select(".manga-info-card a.badge-secondary").joinToString { it.text() }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("ongoing", true) -> SManga.ONGOING
        status.contains("completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapters
    override fun chapterListRequest(manga: SManga) = chapterListRequest(manga.url, 1)

    private fun chapterListRequest(mangaUrl: String, page: Int): Request {
        return GET("$baseUrl$mangaUrl?page=$page", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()

        val crypt1 = (R.mipmap.ic_launcher ushr 87895464) * (456123 ushr 42) - 16 / 4 * -3
        val crypt5 = crypt1.toString().toByteArray(Charsets.UTF_32BE)
        val crypt6 = MessageDigest.getInstance("SHA-1").digest(crypt5).take(16)
        val crypt2 = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val crypt3 = SecretKeySpec(crypt6.toByteArray(), "AES")
        val crypt4 = IvParameterSpec(Base64.decode("totally not some plaintxt".toByteArray(Charsets.UTF_8), Base64.DEFAULT))
        crypt2.init(Cipher.DECRYPT_MODE, crypt3, crypt4)
        val str1 = String(crypt2.doFinal(Base64.decode(chapterListSelector().toByteArray(Charsets.UTF_8), Base64.DEFAULT)))
        val str2 = String(crypt2.doFinal(Base64.decode("FhWk/QUpqr6795+ktyPR2s7RUKP9XJVPx/HNMcxbEwg=".toByteArray(Charsets.UTF_8), Base64.DEFAULT)))
        val str3 = String(crypt2.doFinal(Base64.decode("r2llTpuYqOQBPsnD4nruudrDl0IbVOE3J2+3M4Gae1y4eVMFxjaIobY+6A1g6zjo4gXhIEPRcCddl/Y2GN6CsA4TAtnZD6QulM5qj2+SuBj7MwWJPgrdeAiJUw7YYWROm/vhyT+lsofEJkCXwg+VOQ==".toByteArray(Charsets.UTF_8), Base64.DEFAULT)))
        val str4 = String(crypt2.doFinal(Base64.decode("r2llTpuYqOQBPsnD4nruudrDl0IbVOE3J2+3M4Gae1zTjwoHDBmNuSPotdFbY2FlefoT7PZAKpDSS8nzO/n8soZp0ElftLVjNWbI4rvfnAHid6SbvT4G68fmPZBpp7zAkrEERr66utE0Uf5vfb2f0Q==".toByteArray(Charsets.UTF_8), Base64.DEFAULT)))
        val str5 = String(crypt2.doFinal(Base64.decode("r2llTpuYqOQBPsnD4nruudrDl0IbVOE3J2+3M4Gae1y4eVMFxjaIobY+6A1g6zjoJRqX9VqATcFxezY/RP+EBTCAzmHHWKQuomALunDOFrgPCzOYOQIry+HeW/LArcH5OCkC8r7cM/Sh7EeIO18Mh/hntbB9VZGZrtZBmR/gNGI=".toByteArray(Charsets.UTF_8), Base64.DEFAULT)))

        var i = (50 * 256 / 8 ushr 10) + 1
        fun doThings(): List<Element> {
            val redHerring = document.select(str1)
            return redHerring.map { allChapters ->
                var next = allChapters
                repeat(10) {
                    if (next.selectFirst(str2) != null) {
                        val current = next.select(str5 ?: str3).text() ?: str4
                        next.addClass(current)
                    } else {
                        next = next.parent()
                    }
                }
                next
            }
        }

        doThings().map { chapters.add(chapterFromElement(it)) }
        while (document.select(paginationNextPageSelector).isNotEmpty()) {
            val currentPage = document.select(".nav-link.active").attr("href")
            document = client.newCall(chapterListRequest(currentPage, i)).execute().asJsoup()
            doThings().map { chapters.add(chapterFromElement(it)) }
            ++i
        }

        return chapters
    }

    private val paginationNextPageSelector = popularMangaNextPageSelector()

    override fun chapterListSelector() = "fNFYGQlj6FsW4YGRZLzMtkJqwtW4rBpDBJLUkK0lfHqUsPji6tQKpkBZzvEcpJUy"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val crypt1 = (R.mipmap.ic_launcher ushr 123456) * (789456 ushr 78) + 52 / 4 * -1
        val crypt5 = crypt1.toString().toByteArray(Charsets.UTF_32BE)
        val crypt6 = MessageDigest.getInstance("SHA-1").digest(crypt5).take(16)
        val crypt2 = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val crypt3 = SecretKeySpec(crypt6.toByteArray(), "AES")
        val crypt4 = IvParameterSpec(Base64.decode("totally not some plaintxt".toByteArray(Charsets.UTF_8), Base64.DEFAULT))
        crypt2.init(Cipher.DECRYPT_MODE, crypt3, crypt4)
        val str1 = String(crypt2.doFinal(Base64.decode("ckFyOt1FSclkqG4dG2+mbw==".toByteArray(Charsets.UTF_8), Base64.DEFAULT)))
        val str2 = element.selectFirst(str1)!!

        setUrlWithoutDomain(str2.attr("href"))
        name = str2.text()
        date_upload = parseChapterDate(element.select(".ml-1").attr("title"))
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.US).parse(date)?.time ?: 0
        } catch (_: Exception) {
            0L
        }
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select(
            "img[src*=manga],img[src*=chapter],div>div>img[src]",
        ).mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")
}
