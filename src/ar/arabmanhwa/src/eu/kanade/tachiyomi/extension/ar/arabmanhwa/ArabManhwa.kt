package eu.kanade.tachiyomi.extension.ar.arabmanhwa

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Source
abstract class ArabManhwa : KeiSource() {
    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page, null)

    private suspend fun browse(page: Int, sort: String?): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            if (page > 1) addPathSegments("page/$page")
            addPathSegment("")
            if (sort != null) addQueryParameter("sort", sort)
        }.build()
        return parseList(client.get(url).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page")
            addPathSegment("")
            addQueryParameter("s", query)
        }.build()
        return parseList(client.get(url).asJsoup())
    }

    private fun parseList(document: Document): MangasPage {
        val mangas = document.select("article.card").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.card__cover")!!.absUrl("href"))
                title = element.selectFirst(".card__title")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val document = client.get(url).asJsoup()
        return parseDetails(document).apply { this.url = url.encodedPath }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val chapterList = document.select("a.chapter-row").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".chapter-row__l")!!.text()
                date_upload = parseRelativeDate(element.selectFirst(".chapter-row__d")?.text().orEmpty())
            }
        }
        return SMangaUpdate(parseDetails(document), chapterList)
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.thead__title")!!.text()
        thumbnail_url = document.selectFirst(".thead__cover img")?.absUrl("src")
        description = document.selectFirst("#syn-body")?.text()
        genre = document.select(".tgenres a").joinToString { it.text() }
        status = when {
            document.selectFirst(".thead__badges .badge--ongoing") != null -> SManga.ONGOING
            document.selectFirst(".thead__badges .badge--completed") != null -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // The site only shows times like "منذ 8 أشهر" (8 months ago). Two of a unit is written in the
    // dual form without a number ("منذ شهرين"), one of a unit has neither.
    private fun parseRelativeDate(text: String): Long {
        val count = NUMBER_REGEX.find(text)?.value?.toInt()
        val (amount, unit) = RELATIVE_UNITS.firstNotNullOfOrNull { (words, unit) ->
            words.firstOrNull(text::contains)?.let { word -> (count ?: if (word in DUALS) 2 else 1) to unit }
        } ?: return 0L
        return ZonedDateTime.now().minus(amount.toLong(), unit).toInstant().toEpochMilli()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select(".reader img[data-src]")
        .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("data-src")) }

    companion object {
        private val NUMBER_REGEX = Regex("""\d+""")
        private val DUALS = setOf("دقيقتين", "ساعتين", "يومين", "أسبوعين", "شهرين", "سنتين")
        private val RELATIVE_UNITS = listOf(
            listOf("ثانية", "ثوان") to ChronoUnit.SECONDS,
            listOf("دقيقتين", "دقيقة", "دقائق") to ChronoUnit.MINUTES,
            listOf("ساعتين", "ساعة", "ساعات") to ChronoUnit.HOURS,
            listOf("يومين", "يوم", "أيام") to ChronoUnit.DAYS,
            listOf("أسبوعين", "أسبوع", "أسابيع") to ChronoUnit.WEEKS,
            listOf("شهرين", "شهر", "أشهر", "شهور") to ChronoUnit.MONTHS,
            listOf("سنتين", "سنة", "سنوات") to ChronoUnit.YEARS,
        )
    }
}
