package eu.kanade.tachiyomi.extension.pt.hqdesexo

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HqDeSexo : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == host }

    // The site's ranking is a single page with its 10 most viewed comics
    override suspend fun getPopularManga(page: Int): MangasPage = fetchList("$baseUrl/topviews/")

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchList(if (page == 1) "$baseUrl/" else "$baseUrl/$page/")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected
        val url = when {
            query.isNotBlank() ->
                "$baseUrl/pesquisa/".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query.trim())
                    .addQueryParameter("pg", page.toString())
                    .build()
                    .toString()
            category != null -> if (page == 1) "$baseUrl/categoria/$category/" else "$baseUrl/categoria/$category/$page/"
            else -> return getLatestUpdates(page)
        }

        return fetchList(url)
    }

    private suspend fun fetchList(url: String): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // Ads in the grid link to /anuncio/click/ instead of a comic page
        val mangas = document.select(".listamaster .thumb:has(h3.titulo a[href$=.html])").map { card ->
            val link = card.selectFirst("h3.titulo a")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst(".img img")?.absUrl("src")
            }
        }

        return MangasPage(mangas, document.selectFirst(".paginacao li.current + li > a") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != host || url.pathSegments.size != 1 || !url.encodedPath.endsWith(".html")) return null

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return document.toSManga().apply {
            this.url = url.encodedPath
            thumbnail_url = document.selectFirst(PAGE_SELECTOR)?.absUrl("src")
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        // Video posts have no comic pages
        val chapterList = if (document.selectFirst(PAGE_SELECTOR) == null) {
            emptyList()
        } else {
            // A "Continuar" button sends readers to the rest of the comic on another site
            val isPreview = document.selectFirst("article.post a:has(img[src*=continuar])") != null
            SChapter.create().apply {
                url = manga.url
                name = if (isPreview) "Prévia" else "Capítulo Único"
                date_upload = DATE_FORMAT.tryParseDate(document.selectFirst(".datapost")?.text())
            }.let(::listOf)
        }

        return SMangaUpdate(document.toSManga().apply { url = manga.url }, chapterList)
    }

    private fun Document.toSManga() = SManga.create().apply {
        title = selectFirst(".descpost h2")!!.text()
        // Some intros are a paragraph nested in another one, which the parser splits apart
        description = select(".descpost > p").eachText()
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
            .ifEmpty { null }
        genre = (
            select("#breadcrumbs a[href*=/categoria/]").map { it.text().removePrefix("/") } +
                selectFirst("meta[name=keywords]")?.attr("content")?.split(",").orEmpty()
            )
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString()
            .ifEmpty { null }
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(PAGE_SELECTOR)
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override val supportsFilterFetching = true

    // The footer lists every category, the menu only a few of them
    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        .select("footer a[href*=/categoria/]")
        .map { it.text() to it.absUrl("href").toHttpUrl().pathSegments[1] }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<Pair<String, String>>>() ?: return FilterList()
        return FilterList(
            Filter.Header("A busca por texto ignora a categoria"),
            CategoryFilter(categories),
        )
    }

    companion object {
        private const val PAGE_SELECTOR = "article.post img.full"
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    }
}
