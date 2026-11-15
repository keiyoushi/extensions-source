package eu.kanade.tachiyomi.extension.es.mangavault

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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class MangaVault : KeiSource() {

    override val supportsFilterFetching = true

    // The site's only ranking is the home page's popular series of the week
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val section = document.selectFirst("h2:containsOwn(Popular de esta semana)")!!.closest("section")!!
        val mangas = section.select("a[href^=/manga/]").map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.absUrl("href"))
                title = card.selectFirst("h3")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/mangas/search".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim().take(100))
            filters.firstInstanceOrNull<GenreFilter>()?.value?.let { addQueryParameter("genres", it) }
            filters.firstInstanceOrNull<TypeFilter>()?.value?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<DemographicFilter>()?.value?.let { addQueryParameter("demographic", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.value?.let { addQueryParameter("status", it) }
            addQueryParameter("nsfwMode", filters.firstInstanceOrNull<NsfwFilter>()?.value ?: "include")
            addQueryParameter("page", page.toString())
        }.build()
        val result = client.get(url, Fetch.XHR.from("/explore")).parseAs<SearchResponse>()
        // Novels are text
        val mangas = result.results.filter { it.contentType != "novel" }.map { it.toSManga() }
        return MangasPage(mangas, result.hasMore)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host || segments.first() != "manga" || segments.size < 2) return null
        val manga = SManga.create().apply { this.url = "/manga/${segments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val list = document.island<ChapterListProps>("ChapterList").chapters.map { it.toSChapter(manga.url) }
        return SMangaUpdate(document.toSManga(manga.url), list)
    }

    private fun Document.toSManga(url: String) = SManga.create().apply {
        this.url = url
        title = selectFirst("main h1")!!.text()
        thumbnail_url = selectFirst("main img[fetchpriority=high]")?.absUrl("src")
        val byline = selectFirst("main p:containsOwn(por)")
        author = byline?.selectFirst("span")?.text()
        // Type, status and demographic, then the genres
        val tags = selectFirst("main details")?.previousElementSibling()?.select("span")?.map { it.text() }.orEmpty()
        status = when {
            "En emisión" in tags -> SManga.ONGOING
            "Finalizado" in tags -> SManga.COMPLETED
            "Pausado" in tags -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        genre = tags.filterNot { it in STATUSES }.joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(selectFirst("main details > p"))
            .altTitles(byline?.nextElementSibling()?.text()?.split(" · ").orEmpty())
            .build()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.island<ReaderProps>("MangaReader").imageUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/explore", Fetch.DOCUMENT).asJsoup().islandProps("MangaExplorer")

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<ExplorerProps>()?.genreOptions)

    private inline fun <reified T> Document.island(component: String): T = islandProps(component).parseAs<T>()

    // An Astro component's server-side props, in Astro's own encoding: [0, value] or [1, array]
    private fun Document.islandProps(component: String): JsonElement = selectFirst("astro-island[component-url*=/$component.]")!!.attr("props").parseAs<JsonElement>().fromAstro()

    private fun JsonElement.fromAstro(): JsonElement = when {
        this is JsonArray && size == 2 && this[0] is JsonPrimitive -> when (this[0].jsonPrimitive.int) {
            1 -> JsonArray(this[1].let { it as JsonArray }.map { it.fromAstro() })
            else -> this[1].fromAstro()
        }
        this is JsonObject -> JsonObject(mapValues { it.value.fromAstro() })
        else -> this
    }

    companion object {
        private val STATUSES = setOf("En emisión", "Finalizado", "Pausado")
    }
}
