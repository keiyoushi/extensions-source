package eu.kanade.tachiyomi.extension.all.susunova

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
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Susunova : KeiSource() {
    private val relativeDateRegex = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""")
    private val chapterNumberRegex = Regex("""\d+(?:\.\d+)?""")

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3, 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = catalog(page, "", "popular", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = catalog(page, "", "latest", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = catalog(page, query.trim(), filters.firstInstance<SortFilter>().toUriPart(), filters)

    private suspend fun catalog(page: Int, query: String, sort: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/catalog/manga".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotEmpty()) addQueryParameter("q", query)
                filters.forEach { filter ->
                    when (filter) {
                        is GenreFilter -> addUriPart("genre", filter.toUriPart())
                        is TypeFilter -> addUriPart("criteria", filter.toUriPart())
                        is LanguageFilter -> addUriPart("language", filter.toUriPart())
                        is NationFilter -> addUriPart("nation", filter.toUriPart())
                        is StatusFilter -> addUriPart("status", filter.toUriPart())
                        else -> {}
                    }
                }
            }
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("a[href*=/manga/]:has(h3)").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                title = card.selectFirst("h3")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.select("a[rel=next]").isNotEmpty())
    }

    private fun HttpUrl.Builder.addUriPart(name: String, value: String) {
        if (value.isNotEmpty()) addQueryParameter(name, value)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        return parseDetails(client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup(), manga)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(document, manga), parseChapters(document))
    }

    private fun parseDetails(document: Document, manga: SManga): SManga = manga.apply {
        val heading = document.selectFirst("h1")!!
        title = heading.text()
        thumbnail_url = document.selectFirst("img[alt$=cover]")?.absUrl("src")
        author = document.infoValue("Author")
        genre = document.select("div.mt-4.flex-wrap > span.rounded-full").joinToString { it.text() }.ifEmpty { null }
        val badges = heading.previousElementSibling()?.select("span").orEmpty().map { it.text().lowercase() }
        status = when {
            "ongoing" in badges -> SManga.ONGOING
            "completed" in badges -> SManga.COMPLETED
            "hiatus" in badges -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("p.whitespace-pre-line")?.text())
            .altTitles(listOfNotNull(heading.nextElementSibling()?.takeIf { it.tagName() == "p" }?.text()))
            .build()
    }

    private fun Document.infoValue(label: String): String? = select("div:has(> span:containsOwn($label)) > p").firstOrNull()?.text()

    private fun parseChapters(document: Document): List<SChapter> = document.select("div[x-show=\"tab === 'chapters'\"] a[href]").map { row ->
        SChapter.create().apply {
            setUrlWithoutDomain(row.absUrl("href"))
            val chapterName = row.selectFirst("span.truncate")!!.text()
            name = if (row.select("span").any { it.text() == "Lock" }) "🔒 $chapterName" else chapterName
            chapter_number = chapterNumberRegex.find(chapterName)?.value?.toFloatOrNull() ?: -1f
            date_upload = row.select("span").firstNotNullOfOrNull { parseRelativeDate(it.text()) } ?: 0L
        }
    }

    private fun parseRelativeDate(text: String): Long? {
        val match = relativeDateRegex.find(text) ?: return null
        val amount = match.groupValues[1].toInt()
        val duration: Duration = when (match.groupValues[2]) {
            "second" -> amount.seconds
            "minute" -> amount.minutes
            "hour" -> amount.hours
            "day" -> amount.days
            "week" -> (amount * 7).days
            "month" -> (amount * 30).days
            else -> (amount * 365).days
        }
        return System.currentTimeMillis() - duration.inWholeMilliseconds
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url, Fetch.DOCUMENT).asJsoup()
        return document.select("img[alt^=Page]")
            .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        GenreFilter(),
        TypeFilter(),
        LanguageFilter(),
        NationFilter(),
        StatusFilter(),
    )
}
