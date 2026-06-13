package eu.kanade.tachiyomi.extension.fr.dassouscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class DassouScan : HttpSource() {

    override val name = "Dassou Scan"
    override val baseUrl = "https://dassouscan.com"
    override val lang = "fr"
    override val supportsLatest = true
    override val versionId = 2

    private val dateFormat = SimpleDateFormat("d MMMM yyyy 'à' HH:mm", Locale.FRENCH)

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val path = if (page > 1) "page/$page/" else ""
        val url = "$baseUrl/$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("post_type", "dsc_manga")
            addQueryParameter("tri", "popular")
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("article.featured-card").map { element ->
            SManga.create().apply {
                title = element.attr("data-title").takeIf { it.isNotEmpty() } ?: throw Exception("Title is empty")
                setUrlWithoutDomain(element.selectFirst("a.dsc-card-link")!!.absUrl("href"))

                val bgStyle = element.selectFirst(".cover")?.attr("style") ?: ""
                thumbnail_url = bgStyle.substringAfter("url(").substringBefore(")").removeSurrounding("\"").removeSurrounding("'")
            }
        }

        val hasNextPage = document.selectFirst("a.next.page-numbers, button.dmaj-pagination__next:not([disabled])") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val path = if (page > 1) "page/$page/" else ""
        val url = "$baseUrl/$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("post_type", "dsc_manga")
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val path = if (page > 1) "page/$page/" else ""
        val url = "$baseUrl/$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("post_type", "dsc_manga")
            addQueryParameter("s", query)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")?.text()?.takeIf { it.isNotEmpty() } ?: throw Exception("Manga title is missing")
            description = document.selectFirst(".hero-synopsis p")?.text()
            genre = document.select(".hero-tags a.tag:not(.tag--dsc-tag)").joinToString { it.text() }

            val bgStyle = document.selectFirst(".cover")?.attr("style") ?: ""
            thumbnail_url = bgStyle.substringAfter("url(").substringBefore(")").removeSurrounding("\"").removeSurrounding("'")
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.dsc-manga-chapter-block:not(:has(a[href*=/inscription/]))").map { element ->
            SChapter.create().apply {
                val link = element.selectFirst("a.dsc-manga-chapter-block__title-link")!!

                setUrlWithoutDomain(link.absUrl("href"))
                name = element.selectFirst(".chapter-title")?.ownText() ?: link.text()

                val dateStr = element.selectFirst(".chapter-info")?.text() ?: ""
                if (dateStr.contains("à")) {
                    date_upload = dateFormat.tryParse(dateStr)
                }
            }
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("#dsc-chapter-reader-content .dsc-chapter-strip-img").mapIndexed { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
