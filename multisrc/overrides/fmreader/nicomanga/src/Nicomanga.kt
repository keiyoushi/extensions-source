package eu.kanade.tachiyomi.extension.ja.nicomanga

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class Nicomanga : FMReader("Nicomanga", "https://nicomanga.com", "ja") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // =========================== Manga Details ============================

    override val infoElementSelector = ".card-body > div.row"
    override val mangaDetailsSelectorGenre = "li:has(b:contains(Genre)) a.btn-danger"

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = urlRegex.find(manga.url)?.groupValues?.get(1) ?: throw Exception("Unable to get slug")
        val headers = headersBuilder().apply {
            add("Accept", "*/*")
            add("Host", baseUrl.toHttpUrl().host)
            set("Referer", baseUrl + manga.url)
        }.build()
        return GET("$baseUrl/app/manga/controllers/cont.Listchapterapi.php?slug=$slug", headers)
    }

    override fun chapterFromElement(element: Element, mangaTitle: String): SChapter = SChapter.create().apply {
        element.select(chapterUrlSelector).first()!!.let {
            setUrlWithoutDomain("$baseUrl/${it.attr("href")}")
            name = it.attr("title")
        }

        date_upload = element.select(chapterTimeSelector)
            .let { if (it.hasText()) parseRelativeDate(it.text()) else 0 }
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val id = chapterIdRegex.find(response.use { it.body.string() })?.groupValues?.get(1) ?: throw Exception("chapter-id not found")
        val doc = client.newCall(
            GET("$baseUrl/app/manga/controllers/cont.imgsList.php?cid=$id", headers),
        ).execute().asJsoup()
        return doc.select("img.chapter-img[data-src]").mapIndexed { i, page ->
            Page(i + 1, imageUrl = page.attr("data-src"))
        }
    }

    // ============================= Utilities ==============================

    override fun getImgAttr(element: Element?): String? {
        return when {
            element?.attr("style")?.contains("background-image") == true -> {
                val url = thumbnailURLRegex.find(element.attr("style"))?.groupValues?.get(1)
                when {
                    url?.startsWith("/") == true -> baseUrl + url
                    else -> url
                }
            }
            else -> super.getImgAttr(element)
        }
    }

    companion object {
        private val thumbnailURLRegex = Regex("background-image:[^;]?url\\s*\\(\\s*'?([^')]+?)'?(\\)|\$)")
        private val urlRegex = Regex("manga-([^/]+)\\.html\$")
        private val chapterIdRegex = Regex("imgsListchap\\((\\d+)")
    }
}
