package eu.kanade.tachiyomi.extension.pt.superhentai

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
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class SuperHentai : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get(listUrl("", page), Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val path = filters.firstInstanceOrNull<CategoryFilter>()?.selectedPath()
            ?: filters.firstInstanceOrNull<ParodyFilter>()?.selectedPath()

        val url = if (query.isNotBlank() || path == null) {
            listUrl("", page).toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .build()
        } else {
            listUrl("$path/", page).toHttpUrl()
        }

        return mangaListParse(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private fun listUrl(path: String, page: Int) = if (page > 1) "$baseUrl/${path}page/$page/" else "$baseUrl/$path"

    private fun mangaListParse(document: Document): MangasPage {
        val host = baseUrl.toHttpUrl().host
        val mangas = document.select("div.thumb > a[href][title]")
            .filter { it.absUrl("href").toHttpUrlOrNull()?.host == host }
            .map { anchor ->
                SManga.create().apply {
                    setUrlWithoutDomain(anchor.absUrl("href"))
                    title = anchor.attr("title")
                    thumbnail_url = anchor.selectFirst(".image")?.attr("style")
                        ?.let { BACKGROUND_IMAGE_REGEX.find(it)?.groupValues?.get(1) }
                }
            }
        return MangasPage(mangas, document.selectFirst("a.next.page-numbers") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst("#cn-article .description .gallery") == null) return null
        return mangaDetailsParse(document).apply { setUrlWithoutDomain(url.toString()) }
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
            date_upload = Instant.tryParse(
                document.selectFirst("script.yoast-schema-graph")?.data()
                    ?.let { DATE_PUBLISHED_REGEX.find(it)?.groupValues?.get(1) },
            )
        }
        return SMangaUpdate(mangaDetailsParse(document).apply { url = manga.url }, listOf(chapter))
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val article = document.selectFirst("#cn-article")!!
        title = article.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
        genre = article.select(".category a").eachText().joinToString()
        description = article.selectFirst(".description > p")?.text()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#cn-article .description .gallery img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val categories = async { taxonomyLinks("$baseUrl/categorias/") }
        val parodies = async { taxonomyLinks("$baseUrl/parodias/") }
        mapOf("categories" to categories.await(), "parodies" to parodies.await()).toJsonElement()
    }

    private suspend fun taxonomyLinks(url: String): Map<String, String> = client.get(url, Fetch.DOCUMENT).asJsoup()
        .select("#cn-list .thumb > a[href][title]")
        .associate { it.attr("title") to it.absUrl("href").toHttpUrl().encodedPath.trim('/') }

    override fun getFilterList(data: JsonElement?): FilterList {
        val lists = data?.parseAs<Map<String, Map<String, String>>>() ?: return FilterList()
        return FilterList(
            Filter.Header("Os filtros são ignorados na busca por texto"),
            CategoryFilter(lists["categories"].orEmpty()),
            ParodyFilter(lists["parodies"].orEmpty()),
        )
    }

    companion object {
        private val BACKGROUND_IMAGE_REGEX = Regex("""url\((.+?)\)""")
        private val DATE_PUBLISHED_REGEX = Regex(""""datePublished":"([^"]+)"""")
    }
}
