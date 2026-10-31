package eu.kanade.tachiyomi.extension.es.eternalmangas

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class EternalMangas : KeiSource() {

    override val supportRelatedMangasBySearch = true

    // Images sent with Origin miss the Cloudflare cache the site reader fills
    override fun Headers.Builder.configureHeaders() = removeAll("Origin")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("a.em-popular-card").map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/series".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
            filters.filterIsInstance<SelectFilter>().forEach { it.addTo(this) }
            addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asJsoup()
        val mangas = document.select("a.em-series-catalog-card")
            // Text-only novels; "Novela visual" has image chapters
            .filterNot { it.selectFirst(".em-series-type-badge")?.text() == NOVEL_TYPE }
            .map { it.toSManga() }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        url = absUrl("href").toHttpUrl().pathSegments[1]
        val image = selectFirst("img")!!
        title = image.attrOrNull("alt")!!
        thumbnail_url = image.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("series")
        .addPathSegment(manga.url)
        .build()
        .toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(document.toSManga(manga), document.toChapters())
    }

    private fun Document.toSManga(manga: SManga) = SManga.create().apply {
        url = manga.url
        title = selectFirst("h1")!!.text()
        thumbnail_url = selectFirst("img.em-cover-lg")?.absUrl("src")
        genre = buildList {
            selectFirst("li:has(strong:containsOwn(Tipo:)) .badge")?.textOrNull()?.let(::add)
            select("#tab-sinopsis .em-badge").mapTo(this) { it.text() }
        }.joinToString().ifEmpty { null }
        status = when (selectFirst("li:has(strong:containsOwn(Estado:))")?.ownText()?.lowercase()) {
            "en emisión" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "pausado" -> SManga.ON_HIATUS
            "cancelado", "abandonado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = buildString {
            selectFirst("#resume-content")?.textOrNull()?.let(::append)
            selectFirst("p:has(+ h1)")?.textOrNull()?.let { altTitle ->
                if (isNotEmpty()) append("\n\n")
                append("Título alternativo: ", altTitle)
            }
        }.ifEmpty { null }
    }

    private fun Document.toChapters(): List<SChapter> {
        val groups = select("#tab-sinopsis a[href*=/teams/]").joinToString { it.text() }.ifEmpty { null }
        val series = selectFirst("script#em-v2-payload")!!.data().parseAs<SeriesPayload>()
        return series.content.chapters.map { chapter ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapter.url.url)
                name = chapter.title
                date_upload = Instant.tryParse(chapter.publishedAt)
                scanlator = groups
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#em-reader-pages img.em-reader-img").mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl("data-src").ifEmpty { image.absUrl("src") })
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/series").asJsoup()
        return FILTER_TITLES.keys.associateWith { param ->
            document.select("form select[name=$param] option")
                .map { FilterOption(it.text(), it.attr("value")) }
                .filterNot { param == "type" && it.value == NOVEL_TYPE_VALUE }
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val options = data?.parseAs<Map<String, List<FilterOption>>>().orEmpty()
        return FilterList(
            FILTER_TITLES.mapNotNull { (param, title) ->
                options[param]?.takeIf { it.isNotEmpty() }?.let { SelectFilter(title, param, it) }
            },
        )
    }

    companion object {
        private const val NOVEL_TYPE = "Novela"
        private const val NOVEL_TYPE_VALUE = "novel"
        private val FILTER_TITLES = mapOf("type" to "Tipo", "status" to "Estado", "genre" to "Género")
    }
}
