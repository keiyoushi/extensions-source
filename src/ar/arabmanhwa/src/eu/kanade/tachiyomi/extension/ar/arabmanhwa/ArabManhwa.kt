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
import java.time.Duration
import java.time.Instant

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
        val knownDates = chapters.associate { it.url to it.date_upload }
        val chapterList = document.select("a.chapter-row").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst(".chapter-row__l")!!.text()
                date_upload = parseRelativeDate(element.selectFirst(".chapter-row__d")?.text().orEmpty(), knownDates[url])
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

    // The site only shows times like "منذ 8 أشهر" (8 months ago), truncated to whole units with
    // 30-day months. Two of a unit is written in the dual form without a number ("منذ شهرين"),
    // one of a unit has neither. A date stored by an earlier refresh is kept while it still fits
    // the text, so coarse values like "8 months ago" don't move it on every refresh. The window
    // reaches 5 minutes further back as pages can come from the site's cache (s-maxage=300).
    private fun parseRelativeDate(text: String, knownDate: Long?): Long {
        val count = NUMBER_REGEX.find(text)?.value?.toLong()
        val (amount, unit) = RELATIVE_UNITS.firstNotNullOfOrNull { (words, unit) ->
            words.firstOrNull(text::contains)?.let { word -> (count ?: if (word in DUALS) 2L else 1L) to unit }
        } ?: return 0L
        val now = Instant.now()
        val newest = now.minus(unit.multipliedBy(amount)).toEpochMilli()
        val oldest = now.minus(unit.multipliedBy(amount + 1)).minus(PAGE_CACHE_MAX_AGE).toEpochMilli()
        return knownDate?.takeIf { it in oldest..newest } ?: newest
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select(".reader img[data-src]")
        .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("data-src")) }

    companion object {
        private val PAGE_CACHE_MAX_AGE = Duration.ofMinutes(5)
        private val NUMBER_REGEX = Regex("""\d+""")
        private val DUALS = setOf("دقيقتين", "ساعتين", "يومين", "أسبوعين", "شهرين", "سنتين")
        private val RELATIVE_UNITS = listOf(
            listOf("ثانية", "ثوان") to Duration.ofSeconds(1),
            listOf("دقيقتين", "دقيقة", "دقائق") to Duration.ofMinutes(1),
            listOf("ساعتين", "ساعة", "ساعات") to Duration.ofHours(1),
            listOf("يومين", "يوم", "أيام") to Duration.ofDays(1),
            listOf("أسبوعين", "أسبوع", "أسابيع") to Duration.ofDays(7),
            listOf("شهرين", "شهر", "أشهر", "شهور") to Duration.ofDays(30),
            listOf("سنتين", "سنة", "سنوات") to Duration.ofDays(365),
        )
    }
}
