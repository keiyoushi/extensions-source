package eu.kanade.tachiyomi.extension.id.cgbum

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class Cgbum : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(listUrl(page).addQueryParameter("sort", "views").build())

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(listUrl(page).addQueryParameter("sort", "latest").build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/cari".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
            return getMangaList(url)
        }

        val url = listUrl(page).apply {
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
        }.build()
        return getMangaList(url)
    }

    private fun listUrl(page: Int): HttpUrl.Builder = "$baseUrl/daftar-komik".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("article.comic-card").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("h3.comic-card-title a")!!
                title = link.text()
                setUrlWithoutDomain(link.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] !in MANGA_PATHS) return null

        val manga = SManga.create().apply { this.url = "/komik/${segments[1]}" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = parseDetails(document).apply { url = manga.url },
            chapters = parseChapters(document),
        )
    }

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("button[data-title]")!!.attr("data-title")
        thumbnail_url = document.selectFirst(".comic-cover img")?.absUrl("src")
        author = document.selectFirst(".meta-row:has(.meta-label:containsOwn(Author)) .meta-value")
            ?.text()
            ?.takeIf { it != "-" }
        genre = buildList {
            document.select(".comic-genres a.genre-pill").mapTo(this) { it.text() }
            document.selectFirst(".meta-row:has(.meta-label:containsOwn(Tipe)) .meta-value")?.text()?.let(::add)
        }.joinToString()
        status = when (document.selectFirst(".comic-cover .badge-status")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "tamat" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            document.selectFirst(".synopsis-content")?.textOrNull()?.let(::append)
            document.selectFirst("p.comic-alt-title")?.textOrNull()?.let {
                if (isNotEmpty()) append("\n\n")
                append("Judul alternatif: ", it)
            }
        }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select(".chapter-grid a.ch-grid-item").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.text()
            chapter_number = element.attr("data-chapter").toFloatOrNull() ?: -1f
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select("div.page-container[data-url]").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("data-url"))
        }
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/daftar-komik", Fetch.DOCUMENT).asJsoup()
        return document.select("#filterForm input[name=\"genres[]\"]")
            .map { it.attr("value") }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(Filter.Header("Filter diabaikan saat mencari judul"))
            add(SortFilter())
            add(StatusFilter())
            add(TypeFilter())
            data?.parseAs<List<String>>()?.let { add(GenreFilter(it)) }
        },
    )

    companion object {
        private val MANGA_PATHS = setOf("komik", "baca")
    }
}
