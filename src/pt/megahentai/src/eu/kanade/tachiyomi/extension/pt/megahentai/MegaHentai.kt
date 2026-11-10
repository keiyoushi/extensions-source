package eu.kanade.tachiyomi.extension.pt.megahentai

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class MegaHentai : KeiSource() {
    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) {
        it.host == baseUrl.toHttpUrl().host && !it.encodedPath.startsWith("/wp-content/")
    }

    override suspend fun getPopularManga(page: Int): MangasPage = mangaList(listUrl("popular", page).build())

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaList(listUrl(LIST_PATH, page).build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = listUrl(null, page)
                .addQueryParameter("s", query)
                .addQueryParameter("post_type", "colecoes")
                .build()
            return mangaList(url)
        }

        val url = listUrl(LIST_PATH, page).apply {
            filters.filterIsInstance<SelectFilter>().forEach { addQueryParameter(it.param, it.selected) }
        }.build()
        return mangaList(url)
    }

    // Page 1 without /page/1/, which redirects
    private fun listUrl(path: String?, page: Int): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder().apply {
        path?.let(::addPathSegment)
        if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        addPathSegment("")
    }

    private suspend fun mangaList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".items_grid article, .result-item article").map(::mangaFromCard)
        return MangasPage(mangas, document.selectFirst(".pagination #nextpagination") != null)
    }

    private fun mangaFromCard(element: Element): SManga {
        val link = element.selectFirst("h3 a, .title a")!!
        return SManga.create().apply {
            url = link.absUrl("href").toHttpUrl().pathSegments[1]
            title = link.text()
            thumbnail_url = element.selectFirst("img")?.let(::imageUrl)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val mangaSlug = when (url.pathSegments[0]) {
            "ler-online" -> slug
            "ler" -> client.get(getChapterUrl(SChapter.create().apply { this.url = slug }), Fetch.DOCUMENT).asJsoup()
                .selectFirst(".btcontrol_single a:has(.icon-bars)")!!
                .absUrl("href").toHttpUrl().pathSegments[1]
            else -> return null
        }
        val manga = SManga.create().apply { this.url = mangaSlug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/ler-online/${manga.url}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(mangaDetails(manga, document), chapterList(document))
    }

    private fun mangaDetails(manga: SManga, document: Document) = manga.apply {
        val header = document.selectFirst(".sheader_content")!!
        title = header.selectFirst("h1")!!.text().removePrefix("Todos os Capítulos de ")
        thumbnail_url = header.selectFirst(".poster .thumb img")?.let(::imageUrl)
        author = header.select(".data_info:has(div:containsOwn(Autor)) a").eachText().joinToString().ifEmpty { null }
        artist = header.select(".data_info:has(div:containsOwn(Artista)) a").eachText().joinToString().ifEmpty { null }
        genre = header.select(".gen_flex a[href*=/genero/]").eachText().joinToString().ifEmpty { null }
        status = when (header.selectFirst(".data_info:has(div:containsOwn(Status)) span")?.text()?.lowercase()) {
            "ativo" -> SManga.ONGOING
            "completo" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(header.selectFirst(".sinopse .texto"))
            .altTitles(header.select(".alt_title li").map { it.text() }, label = "Nomes alternativos")
            .build()
    }

    private fun chapterList(document: Document): List<SChapter> {
        // The series page's last-modified time, which moves when a chapter is added
        val updated = document.selectFirst("script.yoast-schema-graph")?.data()
            ?.parseAs<SchemaGraph>()?.graph?.firstNotNullOfOrNull { it.dateModified }
        return document.select("ul#capitulos li.bookc > a").mapIndexed { index, link ->
            SChapter.create().apply {
                url = link.absUrl("href").toHttpUrl().pathSegments[1]
                name = link.selectFirst("span:not(.pt1)")!!.text()
                if (index == 0) date_upload = Instant.tryParse(updated)
            }
        }
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("#relacionados_manga article")
        .map(::mangaFromCard)

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/ler/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#content.cap > img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/$LIST_PATH/", Fetch.DOCUMENT).asJsoup()
        .select("#filter_form select[name=genero] option")
        .mapNotNull { it.attrOrNull("value") }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<String>>().orEmpty()
        return FilterList(
            buildList {
                add(Filter.Header("Os filtros não se aplicam à busca por texto"))
                add(TypeFilter())
                add(StatusFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
                add(OrderFilter())
            },
        )
    }

    // Full-size upload instead of the WordPress thumbnail size
    private fun imageUrl(img: Element): String? = (img.attrOrNull("abs:data-src") ?: img.attrOrNull("abs:src"))
        ?.replace(thumbnailSizeRegex, "")

    companion object {
        private const val LIST_PATH = "novos-capitulos-de-hentai-online-adicionados"
        private val thumbnailSizeRegex = Regex("""-\d+x\d+(?=\.\w+$)""")
    }
}
