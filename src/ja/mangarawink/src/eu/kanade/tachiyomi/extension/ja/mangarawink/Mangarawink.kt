package eu.kanade.tachiyomi.extension.ja.mangarawink

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Source
abstract class Mangarawink : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page) {
        addQueryParameter("sort", "10")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page) {
        addQueryParameter("sort", "-1")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(page) {
        if (query.isNotBlank()) addQueryParameter("keyword", query)
        filters.filterIsInstance<SelectFilter>()
            .filter { it.value.isNotEmpty() }
            .forEach { addQueryParameter(it.param, it.value) }
    }

    private suspend fun getMangaList(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("search/manga")
            .apply(params)
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("ul.list_grid > li").map { element ->
            SManga.create().apply {
                val link = element.selectFirst(".book_name a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = element.selectFirst(".book_avatar img")?.attrOrNull("abs:data-original")
            }
        }
        val hasNextPage = document.selectFirst(".page_redirect a:has(p.active) + a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(document, manga.url), parseChapters(document))
    }

    private fun parseDetails(document: Document, mangaUrl: String) = SManga.create().apply {
        val info = document.selectFirst(".book_info")!!
        url = mangaUrl
        title = info.selectFirst("h1")!!.text()
        thumbnail_url = info.selectFirst(".book_avatar img")?.attrOrNull("abs:src")
        author = info.selectFirst(".author .col-xs-9")?.text()
            ?.takeIf { it != "更新中" }
            ?.split(",")
            ?.joinToString { it.trim() }
        genre = info.select(".list01 a").joinToString { it.text() }.ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(
                document.select(".story-detail-info p")
                    .map { it.text() }
                    // Older entries open with an SEO line, "<romanized title> Raw free"
                    .filterNot { it.isEmpty() || it.endsWith("Raw free") }
                    .joinToString("\n\n"),
            )
            .altTitles(info.selectFirst(".other-name")?.text()?.split(",").orEmpty().filter { it.trim() != title })
            .build()
    }

    private fun parseChapters(document: Document) = document.select(".works-chapter-item").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst(".name-chap a")!!
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.text()
            date_upload = parseRelativeDate(element.selectFirst(".time-chap")?.text())
        }
    }

    private fun parseRelativeDate(text: String?): Long {
        val (amount, unit) = text?.let { RELATIVE_DATE_REGEX.find(it) }?.destructured ?: return 0L
        return ZonedDateTime.now().minus(amount.toLong(), DATE_UNITS.getValue(unit)).toInstant().toEpochMilli()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".page-chapter img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("data-original")) }

    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
    )

    companion object {
        private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s*(秒|分|時間|日|週間|月|年)前""")

        private val DATE_UNITS = mapOf(
            "秒" to ChronoUnit.SECONDS,
            "分" to ChronoUnit.MINUTES,
            "時間" to ChronoUnit.HOURS,
            "日" to ChronoUnit.DAYS,
            "週間" to ChronoUnit.WEEKS,
            "月" to ChronoUnit.MONTHS,
            "年" to ChronoUnit.YEARS,
        )
    }
}
