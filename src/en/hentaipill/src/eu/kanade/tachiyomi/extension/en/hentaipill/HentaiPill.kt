package eu.kanade.tachiyomi.extension.en.hentaipill

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Source
abstract class HentaiPill : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("popular", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("language/english", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                addQueryParameter("q", query.trim())
                addQueryParameter("page", page.toString())
                filters.firstInstance<SortFilter>().toUriPart().takeIf { it.isNotEmpty() }?.let { addQueryParameter("sort", it) }
            }.build()
            return parseMangaList(client.get(url).asJsoup())
        }

        val tag = filters.filterIsInstance<TagFilter>().firstOrNull { it.state.isNotBlank() }
        val path = tag?.let { "${it.path}/${it.state.toSlug()}" } ?: filters.firstInstance<CategoryFilter>().toUriPart()
        return getMangaList(path, page)
    }

    private suspend fun getMangaList(path: String, page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments(path)
            if (page > 1) addPathSegment(page.toString())
        }.build()
        return parseMangaList(client.get(url).asJsoup())
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("div.galleries-list > div.gallery-item").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("a[href*=/gallery/]")!!
                url = link.absUrl("href").toHttpUrl().pathSegments[1]
                title = link.selectFirst("span")!!.text()
                thumbnail_url = link.selectFirst("img.lazy")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "gallery") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null

        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("div.reading-page-header h1")!!.text()
            thumbnail_url = document.selectFirst("button.js-button-favorite")?.absUrl("data-cover")
            artist = document.tableRow("Artists")
            author = artist
            genre = document.tableRow("Tags")
            description = buildString {
                document.tableRow("Parodies")?.let { appendLine("Parodies: $it") }
                document.tableRow("Characters")?.let { appendLine("Characters: $it") }
            }.trim().ifEmpty { null }
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = dateFormat.tryParseDateTime(document.selectFirst("div.reading-page-header-data span")?.ownText())
        }

        return SMangaUpdate(details, listOf(chapter))
    }

    private fun Document.tableRow(label: String): String? = select(".table-row:has(.table-label:containsOwn($label)) .table-value a")
        .joinToString { it.text() }
        .ifEmpty { null }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/gallery/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/gallery/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.reading-pages-content img.lazy").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("data-src"))
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Sort only applies to text search"),
        SortFilter(),
        Filter.Separator(),
        Filter.Header("Ignored with text search; the first filled-in tag is used"),
        TagFilter("Genre", "genre", "drunk female"),
        TagFilter("Parody", "parody", "fate grand order"),
        TagFilter("Character", "character", "raiden shogun"),
        TagFilter("Artist", "artist", "panqlao"),
        Filter.Separator(),
        CategoryFilter(),
    )

    private fun String.toSlug(): String = lowercase().replace(slugRegex, "-").trim('-')

    private val slugRegex = Regex("""[^a-z0-9]+""")

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)
}
