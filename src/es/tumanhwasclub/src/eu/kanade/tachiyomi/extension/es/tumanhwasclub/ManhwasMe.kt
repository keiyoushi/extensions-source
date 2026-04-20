package eu.kanade.tachiyomi.extension.es.tumanhwasclub

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
import java.util.Calendar
import java.util.Locale

class ManhwasMe : HttpSource() {

    override val name = "ManhwasMe"

    // Preserves the original TuManhwas.Club source ID so existing users
    // don't lose their library when the class/name changed.
    override val id = 8004442288770923365L

    override val baseUrl = "https://manhwas.me"

    override val lang = "es"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Overrides to fix legacy TuManhwas URLs that used /manhwa/ instead of /manga/
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url.replace("/manhwa/", "/manga/"), headers)

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url.replace("/manhwa/", "/manga/"), headers)

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url.replace("/manhwa/", "/manga/"), headers)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/search?sort=-views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.results-grid a.result-card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst("div.result-card-title")!!.text()
                thumbnail_url = element.selectFirst("div.result-card-image img")?.let {
                    it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
                }
            }
        }

        val hasNextPage = document.selectFirst(".pagination a.page-btn:has(i.fa-chevron-right)") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/search?sort=-updated_at&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                addQueryParameter("filter[name]", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.selectedValue())
                    is TypeFilter -> addQueryParameter("type", filter.selectedValue())
                    is GenreFilter -> addQueryParameter("genre", filter.selectedValue())
                    is StatusFilter -> addQueryParameter("status", filter.selectedValue())
                    is ContentFilter -> addQueryParameter("caution", filter.selectedValue())
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        SortFilter(),
        TypeFilter(),
        GenreFilter(),
        StatusFilter(),
        ContentFilter(),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.detail-title")!!.text()
            thumbnail_url = document.selectFirst("div.detail-hero-cover img")?.let {
                it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
            }
            author = document.selectFirst("div.detail-stat-row:has(span.detail-stat-label:contains(Autores)) span.detail-stat-value")?.text()
            genre = document.select("div.detail-stat-row:has(span.detail-stat-label:contains(Géneros)) span.detail-stat-value a").joinToString { it.text() }
            description = document.selectFirst("div.detail-synopsis p")?.text()

            status = when (document.selectFirst("span.detail-tag-year")?.text()?.lowercase()) {
                "en curso" -> SManga.ONGOING
                "completado" -> SManga.COMPLETED
                "en pausa" -> SManga.ON_HIATUS
                "cancelado" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("div.detail-chapter-row").map { element ->
            SChapter.create().apply {
                val link = element.selectFirst("span.detail-col-chapter a")
                setUrlWithoutDomain(link?.absUrl("href") ?: "")

                // Cleans up "Ch. 90.00" to "Chapter 90"
                name = link?.text()?.replace("Ch.", "Chapter")?.removeSuffix(".00") ?: ""

                date_upload = parseDate(element.selectFirst("span.detail-col-updated")?.text())
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("div.reader-pages .img-wrap img").mapIndexed { index, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L

        return if (dateStr.contains("hace", ignoreCase = true)) {
            val number = dateStr.replace(nonDigitRegex, "").toIntOrNull() ?: return 0L
            val cal = Calendar.getInstance()
            when {
                dateStr.contains("segundo") -> cal.add(Calendar.SECOND, -number)
                dateStr.contains("minuto") -> cal.add(Calendar.MINUTE, -number)
                dateStr.contains("hora") -> cal.add(Calendar.HOUR, -number)
                dateStr.contains("día") || dateStr.contains("dia") -> cal.add(Calendar.DAY_OF_YEAR, -number)
                dateStr.contains("semana") -> cal.add(Calendar.WEEK_OF_YEAR, -number)
                dateStr.contains("mes") -> cal.add(Calendar.MONTH, -number)
                dateStr.contains("año") -> cal.add(Calendar.YEAR, -number)
            }
            cal.timeInMillis
        } else {
            dateFormat.tryParse(dateStr)
        }
    }

    companion object {
        private val dateFormat by lazy { SimpleDateFormat("dd/MM/yy", Locale.ROOT) }
        private val nonDigitRegex = Regex("""\D""")
    }
}
