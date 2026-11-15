package eu.kanade.tachiyomi.extension.es.visormangaunoriginal

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

@Source
abstract class VisorMangaUnoriginal : KeiSource() {

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("$baseUrl/biblioteca?page=$page".toHttpUrl())

    // The site has no paginated update list, only the chapters added last on its home page
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
            .select(".last-chapters-content .image-post > a[href*=/leer/]")
            .map { link ->
                SManga.create().apply {
                    url = CHAPTER_SLUG.matchEntire(link.absUrl("href").substringAfterLast('/'))!!.groupValues[1]
                    title = link.selectFirst(".chapter-title-updated")!!.text()
                    thumbnail_url = link.selectFirst("img")?.absUrl("src")
                }
            }
            .distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val url = when {
            query.isNotBlank() -> "$baseUrl/biblioteca".toHttpUrl().newBuilder().addQueryParameter("search", query.trim())
            genre != null -> "$baseUrl/genero/$genre".toHttpUrl().newBuilder()
            else -> "$baseUrl/biblioteca".toHttpUrl().newBuilder()
        }
        return getMangaList(url.addQueryParameter("page", page.toString()).build())
    }

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".manga_portada .image-post > a[href*=/manga/]").map { link ->
            SManga.create().apply {
                this.url = link.absUrl("href").substringAfterLast('/')
                title = link.selectFirst(".chapter-title-updated")!!.text()
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a.page-link[rel=next]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return client.get("$baseUrl/manga/$slug", Fetch.DOCUMENT).asJsoup().toSManga(slug)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(document.toSManga(manga.url), document.toSChapters())
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/leer/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#image-alls img[data-src]")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("data-src")) }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/biblioteca", Fetch.DOCUMENT).asJsoup()
        .select("ul.genres a[href*=/genero/]")
        .map { Genre(it.text(), it.absUrl("href").substringAfterLast('/')) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Genre>>() ?: return FilterList()
        return FilterList(GenreFilter(genres))
    }

    private fun Document.toSManga(slug: String) = SManga.create().apply {
        url = slug
        title = selectFirst(".breadcrumb li:last-child")!!.text()
        thumbnail_url = selectFirst(".front-page-image img")?.absUrl("src")
        genre = (listOfNotNull(selectFirst(".front-page-image .type-manga")?.text()) + select(".genres-manga-content .genre-entrada").map { it.text() })
            .joinToString()
            .ifEmpty { null }
        // The site escapes its synopses twice
        description = selectFirst(".sinopsis p.content")?.text()?.let { Parser.unescapeEntities(it, false) }
    }

    private fun Document.toSChapters() = select(".ul-manga-chapter a[href*=/leer/]").map { link ->
        SChapter.create().apply {
            url = link.absUrl("href").substringAfterLast('/')
            name = link.selectFirst(".chapter-li-a")!!.text()
            chapter_number = CHAPTER_SLUG.matchEntire(url)?.groupValues?.get(2)?.toFloatOrNull() ?: -1f
        }
    }

    companion object {
        // Chapter pages are named after the series slug and the chapter number: /leer/jinx-103.00
        private val CHAPTER_SLUG = Regex("""(.+)-(\d+(?:\.\d+)?)""")
    }
}
