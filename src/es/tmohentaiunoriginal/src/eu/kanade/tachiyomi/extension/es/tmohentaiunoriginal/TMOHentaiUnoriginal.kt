package eu.kanade.tachiyomi.extension.es.tmohentaiunoriginal

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.textOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class TMOHentaiUnoriginal : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1) { it.host == baseUrl.toHttpUrl().host }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get(libraryUrl(page, order = "likes_count")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get(libraryUrl(page, order = "creation")))

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = parseMangaList(client.get(libraryUrl(page, search = query.trim())))

    private fun libraryUrl(
        page: Int,
        order: String? = null,
        search: String? = null,
    ): HttpUrl = "$baseUrl/biblioteca"
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("page", page.toString())
        .apply {
            order?.let {
                addQueryParameter("order_item", it)
                addQueryParameter("order_dir", "desc")
            }
            search?.takeIf(String::isNotBlank)?.let { addQueryParameter("title", it) }
        }.build()

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.manga-card[href*=/library/]").mapNotNull(::mangaFromElement)
        val hasNextPage = document.selectFirst("ul.pagination li.active + li a[href*='page=']") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val title =
            element
                .selectFirst("h3.manga-card__title")
                ?.textOrNull()
                ?: return null

        return SManga.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            this.title = title
            thumbnail_url = element.selectFirst("img.manga-card__cover")?.attr("abs:src")
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val sourceHost = baseUrl.toHttpUrl().host.removePrefix("www.")
        if (url.host.removePrefix("www.") != sourceHost || url.pathSegments.firstOrNull() != "library") {
            return null
        }

        val document = client.get(url).asJsoup()
        return parseMangaDetails(document).apply {
            setUrlWithoutDomain(url.toString())
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = chaptersFromDocument(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1#md-title")!!.text()
        thumbnail_url =
            document
                .selectFirst("img#md-cover")
                ?.attr("abs:src")
        description =
            document
                .selectFirst(".md-info-row--synopsis .md-info-row__value p")
                ?.text()
                ?.trim()
                ?.ifBlank { null }
        genre =
            document
                .select("#md-tags-list span.label-info")
                .joinToString { it.text().trim() }
                .ifBlank { null }
        author = document.select(".md-badge--author").joinToString { it.text().trim() }.ifBlank { null }
        artist = author
        status = when {
            document.selectFirst(".md-cover-card__status")?.text()?.contains("complete", true) == true -> SManga.COMPLETED
            document.selectFirst(".md-cover-card__status")?.text()?.contains("ongoing", true) == true -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    private fun chaptersFromDocument(document: Document): List<SChapter> = document
        .select("a.md-preview-read-btn[href*=/view_uploads/]")
        .mapIndexed { index, link ->
            SChapter.create().apply {
                setUrlWithoutDomain(link.attr("abs:href").substringBefore('#'))
                name = if (index == 0) "Capítulo único" else "Lectura ${index + 1}"
            }
        }.distinctBy { it.url }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url.substringBefore('#')).asJsoup()

        val pages = document
            .select("#reader-wrap .reader-img-wrap img")
            .mapNotNull { image ->
                sequenceOf("data-src", "data-original", "src")
                    .map { image.attr("abs:$it") }
                    .firstOrNull(String::isNotBlank)
            }.distinct()

        return pages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()
}
