package eu.kanade.tachiyomi.extension.id.mynimeku

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.string
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MyNimeku : KeiSource() {
    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id"))

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3, 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage = fullList(page, listOf("o:popular", "t:$COMIC_TYPES"))

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("latest-komik")
            .addPageSegments(page)
            .build()
        return client.get(url, Fetch.DOCUMENT).asJsoup().toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val types = filters.firstInstance<TypeFilter>().state.filter { it.state }.joinToString(",") { it.id }
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }.joinToString(",") { it.id }
        val segments = listOfNotNull(
            query.trim().takeIf { it.isNotEmpty() }?.let { "j:$it" },
            filters.firstInstance<StatusFilter>().toUriPart().takeIf { it.isNotEmpty() }?.let { "s:$it" },
            "o:${filters.firstInstance<OrderFilter>().toUriPart()}",
            "t:${types.ifEmpty { COMIC_TYPES }}",
            genres.takeIf { it.isNotEmpty() }?.let { "g:$it" },
        )
        return fullList(page, segments)
    }

    private suspend fun fullList(page: Int, segments: List<String>): MangasPage = client.get(fullListUrl(page, segments), Fetch.DOCUMENT).asJsoup().toMangasPage()

    private fun fullListUrl(page: Int, segments: List<String>) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("full-list")
        .addPathSegment("mix")
        .addPathSegment(segments.joinToString("~"))
        .addPageSegments(page)
        .build()

    private fun HttpUrl.Builder.addPageSegments(page: Int) = apply {
        if (page > 1) {
            addPathSegment("page")
            addPathSegment(page.toString())
        }
        addPathSegment("")
    }

    private fun Document.toMangasPage(): MangasPage {
        val mangas = select("article.mynimeku-update-feed__item, article.mynimeku-mix-feed__item").map { item ->
            val link = item.selectFirst("a[class$=__series-title]")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = item.selectFirst("a[class$=__cover] img")?.imageUrl()
            }
        }
        return MangasPage(mangas, select("a.is-arrow:containsOwn(Next)").isNotEmpty())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "komik") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/komik/$slug/" }
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
        title = document.selectFirst("h1.komik-series-hero__title")!!.text()
        thumbnail_url = document.selectFirst(".komik-series-hero__cover img")?.imageUrl()
        author = document.tableValue("Author")
        genre = (document.select(".komik-series-taxonomy__terms a").map { it.text() } + listOfNotNull(document.tableValue("Type")))
            .joinToString()
            .ifEmpty { null }
        status = when (document.tableValue("Status")?.lowercase()) {
            "on-going" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".komik-series-entry"))
            .altTitles(listOf("Japanese", "English", "Synonyms").mapNotNull { document.tableValue(it) }.distinct().filter { it != title })
            .build()
    }

    private fun Document.tableValue(label: String): String? = select("table.komik-series-table tr")
        .firstOrNull { it.selectFirst("th")?.text() == label }
        ?.selectFirst("td")?.text()
        ?.takeIf { it.isNotEmpty() }

    private fun parseChapters(document: Document): List<SChapter> = document.select("div.komik-series-chapter-row").map { row ->
        val link = row.selectFirst("a.komik-series-chapter-item")!!
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.selectFirst(".komik-series-chapter-item__title")!!.text().removeSuffix(" Bahasa Indonesia")
            chapter_number = row.attr("data-chapter-number").toFloatOrNull() ?: -1f
            date_upload = dateFormat.tryParseDate(link.selectFirst(".komik-series-chapter-item__date")?.text(), ZoneId.of("Asia/Jakarta"))
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url, Fetch.DOCUMENT).asJsoup()
        return document.select("div.komik-reader-content[data-reader-content] img[src]")
            .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = buildJsonObject {
        client.get(fullListUrl(1, listOf("o:popular")), Fetch.DOCUMENT).asJsoup()
            .select("input[name=\"mkmix_genre[]\"]")
            .forEach { put(it.attr("value"), it.attr("data-label")) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            StatusFilter(),
            OrderFilter(),
            TypeFilter(),
            data?.jsonObject?.map { (id, name) -> Genre(name.string, id) }?.let { GenreFilter(it) },
        ),
    )

    private fun Element.imageUrl(): String = absUrl("data-lazy-src").ifEmpty { absUrl("src") }

    private companion object {
        const val COMIC_TYPES = "DOUJINSHI,MANGA,MANHUA,MANHWA,ONE-SHOT"
    }
}
