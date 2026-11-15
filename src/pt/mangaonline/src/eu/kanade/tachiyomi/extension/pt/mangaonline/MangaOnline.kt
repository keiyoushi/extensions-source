package eu.kanade.tachiyomi.extension.pt.mangaonline

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaOnline : KeiSource() {

    // WordPress builds the pages on each request, the images are static files
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds) { !it.encodedPath.startsWith("/wp-content/") }

    override suspend fun getPopularManga(page: Int) = getCatalog(page, "popular")

    override suspend fun getLatestUpdates(page: Int) = getCatalog(page, "recentes")

    private suspend fun getCatalog(page: Int, order: String): MangasPage {
        val url = listUrl(page, "manga").addQueryParameter("ordem", order).build()
        return client.get(url, Fetch.DOCUMENT).asJsoup().toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = listUrl(page).addQueryParameter("s", query.trim()).build()
        return client.get(url, Fetch.DOCUMENT).asJsoup().toMangasPage()
    }

    private fun Document.toMangasPage(): MangasPage {
        val mangas = select("article.home-manga-card, .manga-card").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.selectFirst("a[href*=/manga/]")!!.absUrl("href"))
                title = card.selectFirst("h3")!!.text()
                // Lists show resized covers, the file without the size suffix is the original
                thumbnail_url = card.selectFirst("img")?.absUrl("src")?.replace(COVER_SIZE_REGEX, "")
            }
        }
        return MangasPage(mangas, selectFirst("a.next.page-numbers") != null)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty)
        if (url.host != baseUrl.toHttpUrl().host || slug == null) return MangasPage(emptyList(), false)

        return when (url.pathSegments[0]) {
            "manga" -> MangasPage(listOf(getManga("/manga/$slug/")), false)
            "capitulo" -> {
                // A chapter page links to its series
                val document = client.get(url, Fetch.DOCUMENT).asJsoup()
                val series = document.selectFirst("a.reader-title__manga")!!.absUrl("href").toHttpUrl()
                MangasPage(listOf(getManga(series.encodedPath)), false)
            }
            "genre" -> client.get(listUrl(page, "genre", slug).build(), Fetch.DOCUMENT).asJsoup().toMangasPage()
            else -> MangasPage(emptyList(), false)
        }
    }

    private suspend fun getManga(path: String): SManga {
        val manga = SManga.create().apply { url = path }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // Details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.manga-title")!!.ownText()
            thumbnail_url = document.selectFirst("img.manga-cover-image")?.absUrl("src")
            description = MarkdownDescription.Builder()
                .description(document.selectFirst(".synopsis-content"))
                .build()
            // The imported series also carry numeric terms among their genres
            genre = document.select(".manga-tag").map { it.text() }.filterNot { it.all(Char::isDigit) }.joinToString().ifEmpty { null }
            status = when (document.selectFirst(".manga-meta-item:has(.meta-label:contains(Status)) .meta-value")?.text()?.lowercase()) {
                "em andamento" -> SManga.ONGOING
                "completo" -> SManga.COMPLETED
                "hiato" -> SManga.ON_HIATUS
                "cancelado" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }

        val days = document.select("article.chapter-grid-item").associate {
            it.selectFirst("a")!!.absUrl("href") to it.selectFirst("time.chapter-grid-date")?.text()
        }
        val chapterList = document.select(".chapters-list li.chapter-item").map { item ->
            val link = item.selectFirst("a.chapter-link")!!
            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = link.text()
                date_upload = parseDate(item.selectFirst(".chapter-date")?.text(), days[link.absUrl("href")])
            }
        }
        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("img.chapter-image").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    // Lists are at /<segments>/, then /<segments>/page/<n>/
    private fun listUrl(page: Int, vararg segments: String): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder().apply {
        segments.forEach { addPathSegment(it) }
        if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        addPathSegment("")
    }

    // The chapter list says how long ago ("há 2 semanas"), its grid the day without the year ("26 DE OUT.")
    private fun parseDate(ago: String?, day: String?): Long {
        val (dayOfMonth, monthName) = DAY_REGEX.find(day ?: return 0L)?.destructured ?: return 0L
        val month = MONTHS.indexOf(monthName) + 1
        if (month == 0) return 0L
        val today = LocalDate.now(ZONE)
        val approx = AGO_REGEX.find(ago.orEmpty())?.destructured?.let { (amount, unit) ->
            when (unit) {
                "dia", "dias" -> today.minusDays(amount.toLong())
                "semana", "semanas" -> today.minusWeeks(amount.toLong())
                "mês", "meses" -> today.minusMonths(amount.toLong())
                "ano", "anos" -> today.minusYears(amount.toLong())
                else -> today
            }
        } ?: today
        val date = MonthDay.of(month, dayOfMonth.toInt()).atYear(approx.year).let {
            when {
                it > approx.plusMonths(6) -> it.minusYears(1)
                it < approx.minusMonths(6) -> it.plusYears(1)
                else -> it
            }
        }
        return date.atStartOfDay(ZONE).toInstant().toEpochMilli()
    }

    companion object {
        private val COVER_SIZE_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
        private val DAY_REGEX = Regex("""(\d{1,2}) DE ([A-Z]{3})""")
        private val AGO_REGEX = Regex("""há (\d+) (\S+)""")
        private val MONTHS = listOf("JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ")
        private val ZONE = ZoneId.of("America/Sao_Paulo")
    }
}
