package eu.kanade.tachiyomi.extension.ar.hentaiman

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiMan : HttpSource() {

    override val baseUrl = "https://hentaiman.net"

    override val name = "HentaiMan"

    override val lang = "ar"

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = Jsoup.parse(response.body!!.string())
        val mangas = doc.select("#manga-grid > div").mapNotNull { card ->
            val link = card.selectFirst("a[href*=/manga/]") ?: return@mapNotNull null
            val href = link.attr("abs:href")
            val title = card.selectFirst("h3")?.text()?.trim() ?: return@mapNotNull null
            val img = card.selectFirst("img[src*=storage/covers]")
            val imgSrc = img?.attr("abs:src")?.takeIf { it.isNotEmpty() }
                ?: img?.attr("abs:data-src")

            SManga.create().apply {
                url = href.removePrefix(baseUrl)
                this.title = title
                thumbnail_url = imgSrc
            }
        }.filterNotNull().distinctBy { it.url }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("search", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = Jsoup.parse(response.body!!.string())
        return SManga.create().apply {
            title = doc.selectFirst("h1")?.text()?.trim() ?: ""
            thumbnail_url = doc.selectFirst("img[src*=storage/covers/lg], img[src*=storage/covers/md]")
                ?.attr("abs:src")
            description = doc.select("[aria-label=Alternative Title]").text().trim().ifEmpty {
                doc.selectFirst("dl dd")?.text()?.trim()
            }
            genre = doc.select("a[href*=list/genre]").joinToString { it.text().trim() }
            status = when {
                doc.select("span.status-completed").isNotEmpty() -> SManga.COMPLETED
                doc.select("span.status-on-going").isNotEmpty() -> SManga.ONGOING
                doc.select("span.status-hiatus").isNotEmpty() -> SManga.ON_HIATUS
                doc.select("span.status-canceled").isNotEmpty() -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = Jsoup.parse(response.body!!.string())
        val mangaPath = response.request.url.encodedPath
        return doc.select("li.chapter-item").mapNotNull { item ->
            val link = item.selectFirst("a[href]") ?: return@mapNotNull null
            val href = link.attr("abs:href")
            val chapterPath = href.removePrefix("$baseUrl$mangaPath/")
            val chapterNum = chapterPath.trim('/').split("/").lastOrNull()?.toFloatOrNull()
            val chapterName = link.selectFirst("span:not([class])")?.text()?.trim() ?: ""
            SChapter.create().apply {
                url = href.removePrefix(baseUrl)
                name = "الفصل ${chapterNum?.toInt() ?: "?"} - $chapterName"
                chapter_number = chapterNum ?: 0f
                date_upload = try {
                    val dateStr = link.selectFirst("p.text-gray-400")?.text()?.trim() ?: ""
                    val parts = dateStr.split("/")
                    if (parts.size == 3) {
                        SimpleDateFormat("dd/MM/yy", Locale.US).parse(dateStr)?.time ?: 0L
                    } else {
                        0L
                    }
                } catch (_: Exception) {
                    0L
                }
            }
        }.sortedByDescending { it.chapter_number }.distinctBy { it.url }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = Jsoup.parse(response.body!!.string())
        return doc.select("#reader img.reader-page").mapIndexed { i, img ->
            val src = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            Page(i, imageUrl = src)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
