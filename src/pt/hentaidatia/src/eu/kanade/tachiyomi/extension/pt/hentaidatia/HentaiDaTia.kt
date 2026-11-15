package eu.kanade.tachiyomi.extension.pt.hentaidatia

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
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class HentaiDaTia : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host

    // The five popular posts of the home page are the site's only popular list
    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
            .select(".lista:has(h2:contains(Populares)) .thumb-conteudo > a[href]")
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int) = fetchList(listUrl("", page))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = listUrl("", page).newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("s", query.trim())
            filters.firstInstanceOrNull<TagFilter>()?.selected?.let { addQueryParameter("tag", it) }
            filters.firstInstanceOrNull<ParodyFilter>()?.selected?.let { addQueryParameter("parodia", it) }
        }.build()
        return fetchList(url)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") == host && segments.size == 2 && segments[0] in LIST_PATHS) {
            return fetchList(listUrl("${segments[0]}/${segments[1]}/", page))
        }
        return super.getMangasByUrl(url, page)
    }

    private fun listUrl(path: String, page: Int) = (if (page > 1) "$baseUrl/${path}page/$page/" else "$baseUrl/$path").toHttpUrl()

    // The first home page also has the popular posts: the listed posts are the last block, which a search without results doesn't have
    private suspend fun fetchList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".lista").lastOrNull()
            ?.select(".thumb-conteudo > a[href]")
            ?.map { it.toSManga() }
            .orEmpty()
        return MangasPage(mangas, document.selectFirst("ul.paginacao li.next") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(absUrl("href"))
        title = selectFirst(".thumb-titulo")!!.text()
        thumbnail_url = selectFirst("img")?.originalImageUrl()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != host) return null
        val document = client.get("$baseUrl${url.encodedPath}", Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst("ul.post-fotos") == null) return null
        return document.toSManga().apply {
            setUrlWithoutDomain(document.location())
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
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Capítulo único"
            date_upload = Instant.tryParse(document.selectFirst("meta[property=article:published_time]")?.attr("content"))
        }
        return SMangaUpdate(document.toSManga().apply { url = manga.url }, listOf(chapter))
    }

    private fun Document.toSManga() = SManga.create().apply {
        title = selectFirst("h1.post-titulo")!!.text()
        thumbnail_url = selectFirst(".post-capa img")?.originalImageUrl()
        author = postItem("Autor").joinToString().ifEmpty { null }
        genre = (postItem("Categorias") + postItem("Tags")).joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(selectFirst(".post-texto > p"))
            .field("Título original", selectFirst("ul.post-itens > li:has(strong:contains(Título Original))")?.ownText())
            .field("Paródia", postItem("Paródia"))
            .field("Tradutor", postItem("Tradutor"))
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun Document.postItem(label: String): List<String> = select("ul.post-itens > li")
        .firstOrNull { it.selectFirst("strong")?.text()?.startsWith(label) == true }
        ?.select("a")
        ?.eachText()
        .orEmpty()

    // The site's own gallery shows these images, not the page count of the details
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("ul.post-fotos img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.originalImageUrl()) }

    // Galleries show resized copies: the original is the widest srcset entry, or the file without its size suffix.
    // Visitors without cookies get the cache's lazy-loading variant, with the URLs in data-srcset and data-src.
    private fun Element.originalImageUrl(): String = (attrOrNull("data-srcset") ?: attr("srcset")).split(",")
        .map { it.trim().split(" ") }
        .filter { it.size == 2 }
        .maxByOrNull { it[1].removeSuffix("w").toIntOrNull() ?: 0 }
        ?.first()
        ?: (attrOrNull("abs:data-src") ?: absUrl("src")).replace(SIZE_SUFFIX, "")

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val tags = async { fetchTaxonomies("/todas-as-tags/") }
        val parodies = async { fetchTaxonomies("/parodias/") }
        FilterDataDto(tags.await(), parodies.await()).toJsonElement()
    }

    private suspend fun fetchTaxonomies(path: String) = client.get("$baseUrl$path", Fetch.DOCUMENT).asJsoup()
        .select("div.tag-item")
        .map { item ->
            TaxonomyDto(
                name = item.selectFirst(".tag-title")!!.text(),
                slug = item.selectFirst("a[href]")!!.absUrl("href").toHttpUrl().pathSegments.filter { it.isNotEmpty() }.last(),
            )
        }
        .distinctBy { it.slug }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterDataDto>() ?: return FilterList()
        return FilterList(TagFilter(filterData.tags), ParodyFilter(filterData.parodies))
    }

    companion object {
        private val LIST_PATHS = setOf("tag", "parodia", "autor", "tradutor")
        private val SIZE_SUFFIX = Regex("""-\d+x\d+(?=\.\w+$)""")
    }
}
