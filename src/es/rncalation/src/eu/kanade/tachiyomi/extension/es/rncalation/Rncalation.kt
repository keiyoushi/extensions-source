package eu.kanade.tachiyomi.extension.es.rncalation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Rncalation : HttpSource() {

    override val name = "RNCALATION"

    override val baseUrl = "https://rncalation.online"

    override val lang = "es"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    private val dateFormat = SimpleDateFormat("M/d/yyyy", Locale.US)

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "views")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.comic-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.selectFirst("p[class*=line-clamp-2]")!!.text()
                thumbnail_url = element.selectFirst("img.card-media")?.let {
                    it.absUrl("src")
                }
            }
        }
        val hasNextPage = document.selectFirst("a.lib-page-btn.lib-page-btn--nav:contains(→)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "updated_at")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/library".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            description = document.selectFirst("p[class*=leading-relaxed]")?.text()
            thumbnail_url = document.selectFirst("img.card-media, img[class*=cover]")?.absUrl("src")
            val badges = document.select("span[class*=badge]")
            genre = badges.mapNotNull { badge ->
                val text = badge.text()
                when {
                    text.contains("★") -> null
                    text.contains("vistas") -> null
                    text in statusMap -> null
                    else -> text
                }
            }.joinToString()
            status = badges.firstOrNull { it.text() in statusMap }?.text()
                ?.let { statusMap[it] } ?: SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a[href*=/cap/][class*=flex items-center gap-3]").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.selectFirst("span[class*=flex-1]")!!.text()
                date_upload = element.selectFirst("span[class*=text-\\[.65rem\\]]")?.text()
                    ?.let { dateFormat.tryParse(it) } ?: 0L
            }
        }
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img.page-img").mapIndexed { i, img ->
            val imageUrl = img.attr("data-src").ifEmpty { img.attr("src") }
            Page(i, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val statusMap = mapOf(
            "En emisión" to SManga.ONGOING,
            "Finalizado" to SManga.COMPLETED,
            "Pausado" to SManga.ON_HIATUS,
        )
    }
}
