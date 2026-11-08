package eu.kanade.tachiyomi.extension.id.bacakomikpics

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

@Source
abstract class BacaKomikPics : KeiSource() {
    private val apiUrl get() = "$baseUrl/wp-json/wp/v2"

    private val chapterDataRegex = Regex("""chapterData\s*=\s*(\[.*?]);""", RegexOption.DOT_MATCHES_ALL)
    private val chapterNumberRegex = Regex("""\d+(?:\.\d+)?""")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/komik-populer/", Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".grid a.card").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                title = card.selectFirst(".card-title")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = seriesList(page, null, "modified", "desc", null)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        return seriesList(
            page,
            query.trim().ifEmpty { null },
            sort?.orderBy ?: "relevance",
            sort?.order ?: "desc",
            filters.firstInstanceOrNull<GenreFilter>()?.genreId,
        )
    }

    private suspend fun seriesList(page: Int, query: String?, orderBy: String, order: String, genreId: String?): MangasPage {
        val url = "$apiUrl/komik_series".toHttpUrl().newBuilder()
            .addQueryParameter("per_page", "20")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("_embed", "wp:featuredmedia")
            .apply {
                query?.let { addQueryParameter("search", it) }
                if (query == null && orderBy == "relevance") {
                    addQueryParameter("orderby", "modified")
                    addQueryParameter("order", "desc")
                } else {
                    addQueryParameter("orderby", orderBy)
                    addQueryParameter("order", order)
                }
                genreId?.let { addQueryParameter("genre", it) }
            }
            .build()
        val response = client.get(url, Fetch.XHR)
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 1
        val mangas = response.parseAs<List<SeriesDto>>().map { it.toSManga() }
        return MangasPage(mangas, page < totalPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/series/$slug/" }
        return parseDetails(client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup(), manga).apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(document, manga), parseChapters(document))
    }

    private fun parseDetails(document: Document, manga: SManga): SManga = manga.apply {
        val info = document.selectFirst(".series-info")!!
        val synopsis = info.selectFirst(".sinopsis p")
        title = info.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".series-thumb img.thumb")?.absUrl("src")
        genre = info.select("a.genre-link").joinToString { it.text() }.ifEmpty { null }
        author = synopsis?.field("Author")
        status = when (synopsis?.field("Status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(synopsis?.field("Sinopsis"))
            .altTitles(listOfNotNull(synopsis?.field("Alternatif")))
            .build()
    }

    private fun Element.field(label: String): String? {
        val strong = select("strong").firstOrNull { it.text().startsWith(label) } ?: return null
        return generateSequence(strong.nextSibling()) { it.nextSibling() }
            .takeWhile { !(it is Element && it.tagName() == "strong") }
            .filterIsInstance<TextNode>()
            .joinToString("") { it.text() }
            .trim()
            .ifEmpty { null }
    }

    private fun parseChapters(document: Document): List<SChapter> {
        val json = document.select("script").firstNotNullOfOrNull { chapterDataRegex.find(it.data()) }?.groupValues?.get(1)
            ?: return emptyList()
        return json.parseAs<List<ChapterDto>>().map { chapter ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapter.url)
                name = chapter.title
                chapter_number = chapterNumberRegex.find(chapter.title)?.value?.toFloatOrNull() ?: -1f
            }
        }.reversed()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".viewer-komik img[data-wm]")
            .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$apiUrl/genre".toHttpUrl().newBuilder()
            .addQueryParameter("per_page", "100")
            .addQueryParameter("orderby", "count")
            .addQueryParameter("order", "desc")
            .build()
        val genres = client.get(url, Fetch.XHR).parseAs<List<GenreDto>>()
        return buildJsonObject { genres.forEach { put(it.id.toString(), it.name) } }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.jsonObject?.map { (id, name) -> name.string to id }.orEmpty()
        return FilterList(listOfNotNull(SortFilter(), genres.takeIf { it.isNotEmpty() }?.let { GenreFilter(it) }))
    }
}
