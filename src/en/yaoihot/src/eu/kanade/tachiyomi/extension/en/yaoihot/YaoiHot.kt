package eu.kanade.tachiyomi.extension.en.yaoihot

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.Calendar

class YaoiHot : HttpSource() {

    override val name = "YaoiHot"

    override val baseUrl = "https://yaoihot.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addQueryParameter("orderby", "views")
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addQueryParameter("orderby", "modified")
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addQueryParameter("s", query)
                addQueryParameter("post_type", "manga")
            } else {
                // Fallback to popular if the query is empty
                addPathSegment("manga")
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addQueryParameter("orderby", "views")
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".manga-grid .manga-card").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".manga-card-link")!!
                setUrlWithoutDomain(link.attr("abs:href"))
                title = element.selectFirst(".manga-card-title")!!.text()
                thumbnail_url = element.selectFirst(".manga-cover-img")?.attr("abs:src")
            }
        }

        val hasNextPage = document.selectFirst(".next.page-numbers") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".manga-title")!!.text()
            author = document.selectFirst(".author-line")?.text()?.substringAfter("Author:")?.trim()
            description = document.selectFirst(".summary-content")?.text()
            genre = document.select(".genre-tag").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".manga-cover-img")?.attr("abs:src")
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".chapters-list .chapter-item").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                name = element.selectFirst(".chapter-title")!!.text()
                date_upload = element.selectFirst(".chapter-date")?.text()?.let { parseRelativeDate(it) } ?: 0L
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".reader-page img").mapIndexed { index, img ->
            val imgUrl = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
            Page(index, imageUrl = imgUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities ==============================
    private fun parseRelativeDate(dateStr: String): Long {
        val trimmed = dateStr.trim().lowercase()
        if (trimmed.isEmpty()) return 0L

        val number = trimmed.substringBefore(" ").toIntOrNull() ?: return 0L
        val cal = Calendar.getInstance()

        when {
            trimmed.contains("year") -> cal.add(Calendar.YEAR, -number)
            trimmed.contains("month") -> cal.add(Calendar.MONTH, -number)
            trimmed.contains("week") -> cal.add(Calendar.WEEK_OF_YEAR, -number)
            trimmed.contains("day") -> cal.add(Calendar.DAY_OF_MONTH, -number)
            trimmed.contains("hour") -> cal.add(Calendar.HOUR, -number)
            trimmed.contains("min") -> cal.add(Calendar.MINUTE, -number)
            trimmed.contains("sec") -> cal.add(Calendar.SECOND, -number)
            else -> return 0L
        }

        return cal.timeInMillis
    }
}
