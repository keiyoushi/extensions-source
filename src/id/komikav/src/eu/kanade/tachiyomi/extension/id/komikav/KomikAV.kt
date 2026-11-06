package eu.kanade.tachiyomi.extension.id.komikav

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class KomikAV : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(4)

    override suspend fun getPopularManga(page: Int) = mangaList("$baseUrl/popular/".toHttpUrl(), page)

    override suspend fun getLatestUpdates(page: Int) = mangaList("$baseUrl/".toHttpUrl(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            when {
                query.isNotBlank() -> addPathSegment("search").addPathSegment(query.trim())
                genre != null -> addPathSegments("tax/genre").addPathSegment(genre)
                type != null -> addPathSegment("type").addPathSegment(type)
                else -> addPathSegment("comic-list")
            }
            addPathSegment("")
        }.build()
        return mangaList(url, page)
    }

    // The site's "load more" asks for ?page=N and gets pages 1 to N in one response.
    // The app drops cards it has already shown, so each app page asks for several site pages.
    private suspend fun mangaList(url: HttpUrl, page: Int): MangasPage {
        val sitePage = page * SITE_PAGES_PER_PAGE
        val pageUrl = url.newBuilder().addQueryParameter("page", sitePage.toString()).build()
        val mangas = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
            .select("a.relative.overflow-hidden[href^=\"/manga/\"]:has(img.lazyimage)")
            .map(::mangaFromElement)
            .distinctBy { it.url }
        return MangasPage(mangas, mangas.size >= sitePage * PAGE_SIZE)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        val img = element.selectFirst("img")!!
        title = img.attr("alt")
        thumbnail_url = img.absUrl("data-src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug/" }
        return parseDetails(client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup(), manga)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(document, manga),
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(document: Document, manga: SManga) = manga.apply {
        document.selectFirst("h1.font-kghappy")?.text()?.let { title = it }
        document.selectFirst("meta[property=og:image]")?.attr("content")?.let { thumbnail_url = it }
        author = document.select("p:containsOwn(Author) ~ p").joinToString { it.text() }
        val type = document.selectFirst("div.rounded-l-full")?.text()
        genre = (listOfNotNull(type) + document.select("a[href^=\"/tax/genre/\"]").map { it.text() }).joinToString()
        status = when (document.selectFirst("div.rounded-r-full")?.text()?.lowercase()) {
            "on-going" -> SManga.ONGOING
            "end" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        // The synopsis is plain text with newlines and a literal "&#x20;" the site fails to decode.
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("div.mt-4.w-full > p")?.wholeText()?.replace("&#x20;", ""))
            .build()
    }

    private fun parseChapters(document: Document) = document.select("div.overflow-y-auto > a[href*=\"/chapter-\"]").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst("p")!!.text()
            date_upload = parseRelativeDate(element.selectFirst("p.text-xs")?.text())
        }
    }

    // Mirrors the site's own time-ago text ("3 jam lalu"); "bln" is 4 weeks and "thn" 48 weeks there.
    private fun parseRelativeDate(text: String?): Long {
        val parts = text?.split(' ') ?: return 0L
        val value = parts.getOrNull(0)?.toLongOrNull() ?: return 0L
        val ago = when (parts.getOrNull(1)) {
            "dtk" -> value.seconds
            "mnt" -> value.minutes
            "jam" -> value.hours
            "hari" -> value.days
            "mgg" -> (value * 7).days
            "bln" -> (value * 28).days
            "thn" -> (value * 336).days
            else -> return 0L
        }
        return System.currentTimeMillis() - ago.inWholeMilliseconds
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(baseUrl + chapter.url, Fetch.DOCUMENT).asJsoup()
        .select("img.imgku:not(.langsungload)")
        .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("src")) }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = buildJsonObject {
        client.get("$baseUrl/tax/genre/", Fetch.DOCUMENT).asJsoup()
            .select("a[href^=\"/tax/genre/\"]")
            .forEach { put(it.attr("href").removePrefix("/tax/genre/").trim('/'), it.text()) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            Filter.Header("Filters are ignored when searching by text; genre wins over type"),
            TypeFilter(),
            data?.jsonObject?.map { (slug, name) -> name.string to slug }?.let(::GenreFilter),
        ),
    )

    private open class UriPartFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
        val selected get() = options[state].second
    }

    private class TypeFilter : UriPartFilter("Type", listOf("Any" to null, "Manga" to "manga", "Manhwa" to "manhwa", "Manhua" to "manhua"))

    private class GenreFilter(genres: List<Pair<String, String>>) : UriPartFilter("Genre", listOf("Any" to null) + genres)

    private companion object {
        const val PAGE_SIZE = 18
        const val SITE_PAGES_PER_PAGE = 3
    }
}
