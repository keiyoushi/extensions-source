package eu.kanade.tachiyomi.extension.pt.hentaiyaoi

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
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class HentaiYaoi : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get(listUrl("", page), Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            listUrl("", page).newBuilder().addQueryParameter("s", query).build()
        } else {
            val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected()
            val tag = filters.firstInstanceOrNull<TagFilter>()?.selected()
            listUrl(category?.let { "category/$it/" }.orEmpty(), page).newBuilder()
                .apply { if (tag != null) addQueryParameter("tag", tag) }
                .build()
        }
        return mangaListParse(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private fun listUrl(path: String, page: Int): HttpUrl = (if (page > 1) "$baseUrl/${path}page/$page/" else "$baseUrl/$path").toHttpUrl()

    // The listings mix in cards for a partner site
    private fun mangaListParse(document: Document): MangasPage {
        val host = baseUrl.toHttpUrl().host
        val mangas = document.select(".lista .thumb-conteudo > a[href]")
            .filter { it.absUrl("href").toHttpUrlOrNull()?.host == host }
            .map { anchor ->
                SManga.create().apply {
                    setUrlWithoutDomain(anchor.absUrl("href"))
                    title = anchor.selectFirst(".thumb-titulo")!!.text()
                    thumbnail_url = anchor.selectFirst("img")?.absUrl("src")
                }
            }
        return MangasPage(mangas, document.selectFirst("ul.paginacao li.next") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst("ul.post-fotos") == null) return null
        return mangaDetailsParse(document).apply { setUrlWithoutDomain(document.location()) }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        // Video posts (anime episodes, films) have no pages to read
        val chapterList = if (document.selectFirst("ul.post-fotos") == null) {
            emptyList()
        } else {
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Capítulo único"
                    date_upload = Instant.tryParse(document.selectFirst("meta[property=article:published_time]")?.attr("content"))
                },
            )
        }
        return SMangaUpdate(mangaDetailsParse(document).apply { url = manga.url }, chapterList)
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.post-titulo")!!.text()
        thumbnail_url = document.selectFirst(".post-capa img")?.absUrl("src")
        genre = (document.postItem("Categorias") + document.postItem("Tags")).joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".post-texto > p"))
            .field("Paródia", document.postItem("Paródia"))
            .field("Cor", document.postItem("Cor"))
            .field("Tradutor", document.postItem("Tradutor"))
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun Document.postItem(label: String): List<String> = select("ul.post-itens > li")
        .firstOrNull { it.selectFirst("strong")?.text()?.startsWith(label) == true }
        ?.select("a")?.eachText()
        .orEmpty()

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("ul.post-fotos img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    override fun getFilterList(data: JsonElement?) = getFilters()
}
