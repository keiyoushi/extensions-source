package eu.kanade.tachiyomi.extension.ar.hentaiman

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class HentaiMan : HttpSource() {
    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    private val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select("#manga-grid > div").mapNotNull { card ->
            val link = card.selectFirst("a[href*=/manga/]") ?: return@mapNotNull null
            val title = card.selectFirst("h3")?.text()?.trim() ?: return@mapNotNull null
            val img = card.selectFirst("img[src*=storage/covers]")
            val imgSrc = img?.attr("abs:src")?.takeIf { it.isNotEmpty() }
                ?: img?.attr("abs:data-src")

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = imgSrc
            }
        }.distinctBy { it.url }

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

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
            title = doc.selectFirst("h1")?.text()?.trim()!!
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

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val mangaPath = response.request.url.encodedPath
        return doc.select("li.chapter-item").mapNotNull { item ->
            val link = item.selectFirst("a[href]") ?: return@mapNotNull null
            val href = link.absUrl("href")
            val chapterPath = href.removePrefix("$baseUrl$mangaPath/")
            val chapterNum = chapterPath.trim('/').split("/").lastOrNull()?.toFloatOrNull()
            val chapterName = link.selectFirst("span:not([class])")?.text()?.trim() ?: ""
            SChapter.create().apply {
                setUrlWithoutDomain(href)
                name = "الفصل ${chapterNum?.toInt() ?: "?"} - $chapterName"
                chapter_number = chapterNum ?: 0f
                date_upload = dateFormat.tryParse(
                    link.selectFirst("p.text-gray-400")?.text()?.trim(),
                )
            }
        }.sortedByDescending { it.chapter_number }.distinctBy { it.url }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        return doc.select("#reader img.reader-page").mapIndexed { i, img ->
            val src = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            Page(i, imageUrl = src)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
