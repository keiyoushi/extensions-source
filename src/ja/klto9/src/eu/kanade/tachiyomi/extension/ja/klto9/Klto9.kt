package eu.kanade.tachiyomi.extension.ja.klto9

import android.util.Base64
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
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Klto9 : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("listType", "pagination")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "views")
            .addQueryParameter("sort_type", "DESC")
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("listType", "pagination")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "last_update")
            .addQueryParameter("sort_type", "DESC")
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga-list.html".toHttpUrl().newBuilder()
            .addQueryParameter("listType", "pagination")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("title", query)
        }

        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        genreFilter?.state?.forEach { genre ->
            when (genre.state) {
                Filter.TriState.STATE_INCLUDE -> url.addQueryParameter("include_genres[]", genre.slug)
                Filter.TriState.STATE_EXCLUDE -> url.addQueryParameter("exclude_genres[]", genre.slug)
            }
        }

        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        if (statusFilter != null && statusFilter.state != 0) {
            url.addQueryParameter("m_status", STATUS_VALUES[statusFilter.state].second)
        }

        val authorFilter = filters.firstInstanceOrNull<AuthorFilter>()
        if (authorFilter != null && authorFilter.state.isNotEmpty()) {
            url.addQueryParameter("author", authorFilter.state)
        }

        val groupFilter = filters.firstInstanceOrNull<GroupFilter>()
        if (groupFilter != null && groupFilter.state.isNotEmpty()) {
            url.addQueryParameter("group", groupFilter.state)
        }

        val sort = filters.firstInstanceOrNull<SortFilter>()?.state
        url.addQueryParameter("sort", sort?.let { SORT_VALUES[it.index].second } ?: "last_update")
        url.addQueryParameter("sort_type", if (sort?.ascending == true) "ASC" else "DESC")

        val document = client.get(url.build(), Fetch.DOCUMENT).asJsoup()
        return parseMangasPage(document)
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select("article.manga-update-card").map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst("a.pagination-btn[title=Next Page]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        val link = element.selectFirst("h3.update-title a")!!

        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = link.text()
            thumbnail_url = element.selectFirst("img.update-cover-img")?.coverUrl()
        }
    }

    // PageSpeed rewrites cover.php?u= to xcover.php,qu= and inlines small covers as data: urls
    private fun Element.coverUrl(): String? {
        val src = absUrl("src")
        val encoded = coverRegex.find(src)?.groupValues?.get(1)
        return when {
            encoded != null -> Base64.decode(encoded, Base64.URL_SAFE).decodeToString()
            src.startsWith("http") -> src
            else -> null
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val segment = url.pathSegments.singleOrNull() ?: return null
        val mangaPath = when {
            segment.startsWith("teap-") -> "/$segment"
            segment.startsWith("zmqs-") && segment.contains("-chapter-") ->
                segment
                    .substringAfter("zmqs-")
                    .substringBeforeLast("-chapter-")
                    .ifEmpty { null }
                    ?.let { "/teap-$it.html" }
            else -> null
        } ?: return null

        return fetchMangaDetails(SManga.create().apply { setUrlWithoutDomain(mangaPath) }).apply {
            setUrlWithoutDomain(mangaPath)
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.cinema-title")!!.text()
            author = document.creators("/manga-author")
            artist = document.creators("/manga-artist")
            description = listOfNotNull(
                document.selectFirst("#synopsis-content")?.wholeText()?.trim()?.takeUnless { it.isEmpty() || it == PLACEHOLDER },
                document.selectFirst("span.alt-value")?.textOrNull()?.takeUnless { it == PLACEHOLDER }
                    ?.split(", ")?.joinToString("\n", "**Other Names:**\n\n") { "- $it" },
            ).joinToString("\n\n").ifEmpty { null }
            genre = document.select("a.cinema-genre-pill").joinToString { it.text() }.ifEmpty { null }
            status = when (document.selectFirst("span.poster-status-chip")?.text()?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "paused" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst("#btn-manga-bookmark")?.attrOrNull("abs:data-cover")
        }
    }

    private fun Document.creators(path: String): String? = select("a.cinema-meta-link[href^=$path]")
        .map { it.text() }
        .filter { it != PLACEHOLDER }
        .joinToString()
        .ifEmpty { null }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val slug = manga.url.substringAfter("teap-").substringBefore(".html")
        val lstFilename = "${getRandomString(25)}.lst"
        val url = "$baseUrl/$lstFilename".toHttpUrl().newBuilder()
            .addQueryParameter("manga", slug)
            .build()

        return client.get(url, Fetch.XHR).parseAs<List<Dto>>().map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (_, slug, number) = chapter.url.split("#")
        return "$baseUrl/zmqs-$slug-chapter-${number.removeSuffix(".0")}.html"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val cid = chapter.url.substringBefore("#")
        val iogFilename = "${getRandomString(30)}.iog"
        val url = "$baseUrl/$iogFilename".toHttpUrl().newBuilder()
            .addQueryParameter("cid", cid)
            .build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return document.select("img").mapIndexed { index, img ->
            val src = img.absUrl("data-pagespeed-lazy-src").ifEmpty { img.absUrl("src") }
            Page(index, imageUrl = src)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/manga-list.html", Fetch.DOCUMENT).asJsoup()
        .select("button.filter-genre-pill")
        .map { GenreDto(it.attr("data-name"), it.attr("data-slug")) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            val genres = data?.parseAs<List<GenreDto>>().orEmpty()
            if (genres.isNotEmpty()) add(GenreFilter(genres.map { Genre(it.name, it.slug) }))
            add(StatusFilter())
            add(AuthorFilter())
            add(GroupFilter())
            add(SortFilter())
        },
    )

    private val coverRegex = Regex("""cover\.php[?,]q?u=([\w-]+)""")

    private fun getRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    companion object {
        private const val PLACEHOLDER = "Updating"
    }
}
