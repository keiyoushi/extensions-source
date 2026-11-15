package eu.kanade.tachiyomi.extension.pt.pandasuki

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class PandaSuki : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host

    private val apiUrl get() = "$baseUrl/wp-json/wp/v2"

    override suspend fun getPopularManga(page: Int) = fetchManhwas(page) {
        addQueryParameter("orderby", "post_views")
    }

    // The site's latest updates are the grid of its home page, which has no other pages
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
            .select(".elementor-widget-loop-grid .e-loop-item")
            .map { item ->
                SManga.create().apply {
                    url = item.selectFirst("a[href*=/manhwa/]")!!.absUrl("href").toHttpUrl().pathSegments[1]
                    title = item.selectFirst(".elementor-heading-title")!!.text()
                    thumbnail_url = item.selectFirst("img")?.imageUrl()
                }
            }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        val genres = filters.firstInstanceOrNull<GenreFilter>()
        val included = genres?.included.orEmpty()
        val excluded = genres?.excluded.orEmpty()

        return fetchManhwas(page) {
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
            addQueryParameter("orderby", sort?.orderBy ?: "post_views")
            addQueryParameter("order", sort?.order ?: "desc")
            if (included.isNotEmpty()) {
                addQueryParameter("genero[terms]", included.joinToString(","))
                addQueryParameter("genero[operator]", "AND")
            }
            if (excluded.isNotEmpty()) addQueryParameter("genero_exclude", excluded.joinToString(","))
        }
    }

    private suspend fun fetchManhwas(page: Int, block: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$apiUrl/manhwa".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "20")
            addQueryParameter("_embed", "wp:featuredmedia")
            addQueryParameter("_fields", "slug,title,_links.wp:featuredmedia,_embedded")
            block()
        }.build()
        val response = client.get(url, Fetch.XHR)
        // A page past the last one is an error (400), not an empty list
        val totalPages = response.header("X-WP-TotalPages")!!.toInt()
        val mangas = response.parseAs<List<MangaDto>>().map { it.toSManga() }
        return MangasPage(mangas, page < totalPages)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") != host || segments.size != 2 || segments[0] != "genero") {
            return super.getMangasByUrl(url, page)
        }
        val genre = client.get("$apiUrl/genero?slug=${segments[1]}&_fields=id,name", Fetch.XHR)
            .parseAs<List<GenreDto>>()
            .singleOrNull() ?: return MangasPage(emptyList(), false)
        return fetchManhwas(page) {
            addQueryParameter("orderby", "post_views")
            addQueryParameter("genero", genre.id.toString())
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") != host || segments.size != 2) return null
        val seriesUrl = when (segments[0]) {
            "manhwa" -> "$baseUrl/manhwa/${segments[1]}/"
            // Chapter slugs don't always start with the series slug, but the chapter page links to its series
            "capitulo" -> client.get("$baseUrl/capitulo/${segments[1]}/", Fetch.DOCUMENT).asJsoup()
                .selectFirst("[data-elementor-type=single-post] a[href*=/manhwa/]")
                ?.absUrl("href") ?: return null
            else -> return null
        }
        val document = client.get(seriesUrl, Fetch.DOCUMENT).asJsoup()
        // An unknown series redirects to the home page
        if (document.location().toHttpUrl().pathSegments.first() != "manhwa") return null
        return document.toSManga().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manhwa/${manga.url}/"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/capitulo/${chapter.url}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapterList = document.select(".lista-capitulos-container a.capitulo-item").map { item ->
            SChapter.create().apply {
                url = item.absUrl("href").toHttpUrl().pathSegments[1]
                name = item.selectFirst(".capitulo-titulo")!!.text()
                date_upload = DATE_FORMAT.tryParseDate(item.selectFirst(".capitulo-data")?.text(), ZONE)
            }
        }
        return SMangaUpdate(document.toSManga(), chapterList)
    }

    private fun Document.toSManga(): SManga {
        val post = selectFirst("[data-elementor-type=single-post]")!!
        val infos = post.select(".elementor-post-info__item--type-custom").eachText()
        val altTitles = post.selectFirst(".elementor-widget-theme-post-title + .elementor-widget-heading")
            ?.text()
            ?.split(ALT_TITLE_SEPARATOR)
            ?.map { it.trim() }
            .orEmpty()

        return SManga.create().apply {
            url = location().toHttpUrl().pathSegments[1]
            title = post.selectFirst(".elementor-widget-theme-post-title h1")!!.text()
            thumbnail_url = post.selectFirst(".elementor-widget-theme-post-featured-image img")?.imageUrl()
            author = infos.valueOf("Roteiro")
            artist = infos.valueOf("Arte")
            genre = post.select("a[href*=/genero/]").eachText().joinToString().ifEmpty { null }
            status = when (post.selectFirst(".status-manhwa")?.text()?.lowercase()) {
                "lançamento" -> SManga.ONGOING
                // The site's tooltip: finished in the original, still being translated
                "em andamento" -> SManga.PUBLISHING_FINISHED
                "hiato" -> SManga.ON_HIATUS
                "concluído" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = MarkdownDescription.Builder()
                .description(post.selectFirst(".elementor-widget-text-editor"))
                .field("Ano de lançamento", infos.valueOf("Ano de Lançamento"))
                .altTitles(altTitles, "Títulos alternativos")
                .build()
        }
    }

    // Details lines are "Label: value"
    private fun List<String>.valueOf(label: String) = firstOrNull { it.startsWith("$label:") }
        ?.substringAfter(':')
        ?.trim()
        ?.ifEmpty { null }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#paginas img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.imageUrl()) }

    // Pages and covers can be resized copies: the original is the widest srcset entry
    private fun Element.imageUrl(): String? {
        val widest = (attrOrNull("data-srcset") ?: attrOrNull("srcset"))
            ?.split(",")
            ?.map { it.trim().split(" ") }
            ?.filter { it.size == 2 }
            ?.maxByOrNull { it[1].removeSuffix("w").toIntOrNull() ?: 0 }
            ?.first()
        return widest?.let { baseUrl.toHttpUrl().resolve(it)!!.toString() }
            ?: attrOrNull("abs:data-src")
            ?: attrOrNull("abs:src")
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genero?per_page=100&hide_empty=true&_fields=id,name", Fetch.XHR)
        .parseAs<List<GenreDto>>()
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()
        return FilterList(listOfNotNull(SortFilter(), genres?.let(::GenreFilter)))
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val ZONE = ZoneId.of("America/Sao_Paulo")
        private val ALT_TITLE_SEPARATOR = Regex("""[/;]""")
    }
}
