package eu.kanade.tachiyomi.extension.all.freeadultcomix

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class FreeAdultComix : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get(listUrl("", page), Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected()
        val tag = filters.firstInstanceOrNull<TagFilter>()?.selected()
        val url = listUrl(category?.let { "category/$it/" }.orEmpty(), page).newBuilder()
            .apply { if (tag != null) addQueryParameter("tag", tag) }
            .apply { if (query.isNotBlank()) addQueryParameter("s", query) }
            .build()
        return mangaListParse(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    // Spanish comics share one category: the Spanish source keeps to it, the English source leaves it out
    private fun listUrl(path: String, page: Int): HttpUrl = (if (page > 1) "$baseUrl/${path}page/$page/" else "$baseUrl/$path").toHttpUrl()
        .newBuilder()
        .addQueryParameter("cat", if (lang == "es") SPANISH_CATEGORY_ID else "-$SPANISH_CATEGORY_ID")
        .build()

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("ul.videos > li").map { item ->
            SManga.create().apply {
                val link = item.selectFirst("a.titulo")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = item.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("ul.paginacao li.next") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val gallery = document.selectFirst(".dgwt-jg-gallery") ?: return null
        return mangaDetailsParse(document).apply {
            setUrlWithoutDomain(document.location())
            // Posts don't show their cover
            thumbnail_url = gallery.selectFirst("a")?.absUrl("href")
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        // Video posts have no pages to read
        val chapterList = if (document.selectFirst(".dgwt-jg-gallery") == null) {
            emptyList()
        } else {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = if (lang == "es") "Capítulo" else "Chapter"
                },
            )
        }
        return SMangaUpdate(mangaDetailsParse(document).apply { url = manga.url }, chapterList)
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".post-conteudo h1")!!.text()
        genre = document.select(".post-tags a").eachText().joinToString().ifEmpty { null }
        // The text above the gallery starts with the title again
        description = MarkdownDescription.Builder()
            .description(document.select(".post-texto > :not(.dgwt-jg-gallery)").eachText().filterNot { it == title }.joinToString("\n\n"))
            .build()
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".dgwt-jg-gallery figure > a[href]")
        .mapIndexed { index, link -> Page(index, imageUrl = link.absUrl("href")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val categories = document.select(".minimenu option[value]").associate { it.text() to it.absUrl("value").toHttpUrl().pathSegments[1] }
        val tags = document.select(".tagcloud a").associate { it.text() to it.absUrl("href").toHttpUrl().pathSegments[1] }
        return mapOf("categories" to categories, "tags" to tags).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val lists = data?.parseAs<Map<String, Map<String, String>>>() ?: return FilterList()
        return FilterList(
            CategoryFilter(lists["categories"].orEmpty()),
            TagFilter(lists["tags"].orEmpty()),
        )
    }

    companion object {
        private const val SPANISH_CATEGORY_ID = "2217"
    }
}
