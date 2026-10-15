package eu.kanade.tachiyomi.extension.en.hentaisco

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class HentaiSco : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = browse(paged("hentai-list", page)) { addQueryParameter("m_orderby", "views") }

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(paged("hentai-list", page)) {}

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            return browse("search") {
                addQueryParameter("q", query)
                // The search pager's own /search/page/N/ links are 404
                if (page > 1) addQueryParameter("page", page.toString())
            }
        }

        val genre = filters.firstInstanceOrNull<GenreFilter>()?.slug.orEmpty()
        return browse(paged(if (genre.isEmpty()) "hentai-list" else "genre/$genre", page)) {
            filters.filterIsInstance<QueryFilter>().forEach { it.addToUrl(this) }
        }
    }

    private fun paged(path: String, page: Int) = if (page > 1) "$path/page/$page" else path

    private suspend fun browse(path: String, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(path)
            .addPathSegment("")
            .apply(params)
            .build()
        val document = client.get(url).asJsoup()
        return MangasPage(document.mangaList(), document.selectFirst("nav.pager a[rel=next]") != null)
    }

    private fun Document.mangaList(): List<SManga> = select("article.cover-card").map { card ->
        SManga.create().apply {
            url = card.selectFirst("a")!!.absUrl("href").toHttpUrl().pathSegments[1]
            title = card.selectFirst("h3")!!.text()
            thumbnail_url = card.selectFirst("img")?.absUrl("src")
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "hentai") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        return getMangaUpdate(SManga.create().apply { this.url = slug }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = document.selectFirst("article[data-manga-id]")!!

        manga.apply {
            title = details.selectFirst("h1")!!.text()
            thumbnail_url = details.selectFirst(".cover-frame img")?.absUrl("src")
            author = details.select("dt:containsOwn(Author) + dd a").joinToString { it.text() }.ifEmpty { null }
            artist = details.select("dt:containsOwn(Artist) + dd a").joinToString { it.text() }.ifEmpty { null }
            genre = details.select("a[href^=/genre/], a[href^=/tag/]").joinToString { it.text().removePrefix("#") }.ifEmpty { null }
            description = (details.select("[data-read-more-body] p") + details.select("h1 + p"))
                .joinToString("\n\n") { it.text() }
                .ifEmpty { null }
            val info = details.selectFirst("p:has(+ h1)")?.text()?.lowercase().orEmpty()
            status = when {
                "ongoing" in info -> SManga.ONGOING
                "completed" in info -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }

        val chapterList = details.select("ol[data-chapter-list] > li > a").map { link ->
            SChapter.create().apply {
                url = link.absUrl("href").toHttpUrl().pathSegments.drop(1).filter(String::isNotEmpty).joinToString("/")
                name = link.selectFirst("span")!!.text()
                date_upload = Instant.tryParse(link.selectFirst("time")?.attr("datetime"))
            }
        }

        return SMangaUpdate(manga, chapterList)
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga)).asJsoup().mangaList()

    // Madara-era entries: a post id with memo["path"], or a "/hentai/<slug>/" url
    private val SManga.slug: String
        get() {
            val path = memo["path"]?.stringOrNull ?: url.takeIf { it.startsWith("/") } ?: return url
            return baseUrl.toHttpUrl().resolve(path)!!.pathSegments[1]
        }

    // Madara-era chapters: a bare slug with memo["mangaPath"], or a "/hentai/<slug>/<chapter>/" url
    private val SChapter.path: String
        get() {
            val path = memo["mangaPath"]?.stringOrNull?.plus(url) ?: url.takeIf { it.startsWith("/") } ?: return url
            return baseUrl.toHttpUrl().resolve(path)!!.pathSegments.drop(1).filter(String::isNotEmpty).joinToString("/")
        }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("[data-reader-pages] img.reader-page")
        .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments("hentai-list/").build()
        return client.get(url).asJsoup()
            .select("section[aria-labelledby=browse-genres] a")
            .associate { it.ownText() to it.absUrl("href").toHttpUrl().pathSegments[1] }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<Map<String, String>>().orEmpty()
        return FilterList(
            listOfNotNull(
                SortFilter(),
                StatusFilter(),
                TypeFilter(),
                genres.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
            ),
        )
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("hentai")
        .addPathSegment(manga.slug)
        .addPathSegment("")
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("hentai")
        .addPathSegments(chapter.path)
        .addPathSegment("")
        .build()
        .toString()
}
