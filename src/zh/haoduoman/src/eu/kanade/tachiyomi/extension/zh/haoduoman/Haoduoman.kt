package eu.kanade.tachiyomi.extension.zh.haoduoman

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class Haoduoman : HttpSource() {

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // Popular / catalogue

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manhua")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    // Latest updates

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/new", headers)

    override fun latestUpdatesParse(response: Response): MangasPage =
        MangasPage(mangaListParse(response).mangas, false)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        MangasPage(mangaListParse(response).mangas, false)

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.media")
            .mapNotNull(::mangaFromElement)
            .distinctBy { it.url }

        val hasNextPage = document.select("a[href]").any { element ->
            val href = element.attr("href")
            href.startsWith("/manhua/page/") && element.text().trim() in setOf("»", "下一页")
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val titleLink = element.selectFirst("a.title[href]") ?: return null
        val path = titleLink.attr("href").trim()
        if (!MANGA_PATH.matches(path)) return null

        return SManga.create().apply {
            setUrlWithoutDomain(titleLink.absUrl("href"))
            title = titleLink.text().trim()
            thumbnail_url = element.selectFirst("a.image[data-original]")?.absUrl("data-original")
        }
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val metaFields = document.select(".metas-body > span")
        val author = metaFields.firstOrNull { it.text().startsWith("作者：") }
            ?.text()
            ?.substringAfter("作者：")
            ?.trim()

        val statusText = metaFields.firstOrNull { it.text().startsWith("状态：") }?.text().orEmpty()
        val genres = document.select(".metas-body a").filter {
            it.attr("href").contains("/manhua/theme/")
        }.map { it.text().trim() }.filter { it.isNotEmpty() }

        return SManga.create().apply {
            title = document.selectFirst(".metas-title")?.text()?.trim().orEmpty()
            thumbnail_url = document.selectFirst(".metas-image img")?.absUrl("src")
            this.author = author
            artist = author
            genre = genres.joinToString(", ")
            description = document.selectFirst(".metas-desc p")?.text()?.trim().orEmpty()
            status = when {
                statusText.contains("连载") -> SManga.ONGOING
                statusText.contains("完结") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Chapters are rendered oldest-first on the detail page.

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".comic-chapters a[href]")
        .mapNotNull { element ->
            val path = element.attr("href").trim()
            if (!CHAPTER_PATH.matches(path)) return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text().trim()
            }
        }
        .reversed()

    // Reader page

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapterUrl = response.request.url.toString()
        val encodedParams = document.select("script")
            .asSequence()
            .mapNotNull { script -> PARAMS_PATTERN.find(script.data())?.groupValues?.get(2) }
            .firstOrNull()
            ?: throw Exception("好多漫阅读参数缺失")

        val params = decodeParams(encodedParams)
            ?: throw Exception("好多漫阅读参数无法解密")

        val host = params.optString("host")
        if (host.isNotEmpty() && host !in setOf("www.haoduoman.com", "haoduoman.com")) {
            throw Exception("好多漫阅读参数来自未知域名")
        }

        val images = params.optJSONArray("chapter_images")
            ?: throw Exception("好多漫章节图片列表缺失")

        return buildList {
            for (index in 0 until images.length()) {
                val imageUrl = images.optString(index).trim()
                if (imageUrl.isNotEmpty()) {
                    add(Page(size, url = chapterUrl, imageUrl = imageUrl))
                }
            }
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", page.url.ifBlank { "$baseUrl/" })
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun decodeParams(encoded: String): JSONObject? = runCatching {
        val payload = Base64.decode(encoded, Base64.DEFAULT)
        require(payload.size > IV_SIZE)

        val iv = payload.copyOfRange(0, IV_SIZE)
        val encrypted = payload.copyOfRange(IV_SIZE, payload.size)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(AES_KEY.toByteArray(StandardCharsets.UTF_8), "AES"),
            IvParameterSpec(iv),
        )
        JSONObject(cipher.doFinal(encrypted).toString(StandardCharsets.UTF_8))
    }.getOrNull()

    companion object {
        private const val IV_SIZE = 16
        // Fixed by the public CMS reader used by haoduoman.com.
        private const val AES_KEY = "5V&RoR%Jf@pJPydF"

        private val MANGA_PATH = Regex("""^/manhua/\d+/?$""")
        private val CHAPTER_PATH = Regex("""^/manhua/\d+/\d+\.html$""")
        private val PARAMS_PATTERN = Regex(
            """\bparams\s*=\s*(['"])(.*?)\1""",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
    }
}
