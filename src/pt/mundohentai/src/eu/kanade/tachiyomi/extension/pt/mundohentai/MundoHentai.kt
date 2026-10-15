package eu.kanade.tachiyomi.extension.pt.mundohentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Element
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MundoHentai : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(1, 2.seconds)

    private fun genericMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("span.thumb-titulo").text()
        thumbnail_url = element.select("img.attachment-post-thumbnail").attr("src")
        setUrlWithoutDomain(element.select("a:has(span.thumb-imagem)").attr("href"))
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val newHeaders = headers.newBuilder()
            .set("Referer", if (page == 1) baseUrl else "$baseUrl/category/doujinshi/page/${page - 1}")
            .build()

        val pageStr = if (page != 1) "page/$page" else ""
        val document = client.get("$baseUrl/category/doujinshi/$pageStr", newHeaders).asJsoup()
        val mangas = document
            .select("div.lista > ul > li div.thumb-conteudo:has(a[href^=$baseUrl]):not(:contains(Tufos))")
            .map(::genericMangaFromElement)
        val hasNextPage = document.selectFirst("ul.paginacao li.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val document = if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .build()
            client.get(url).asJsoup()
        } else {
            val tagFilter = filters.firstInstance<TagFilter>()
            val tagSlug = tagFilter.values[tagFilter.state].slug

            if (tagSlug.isEmpty()) {
                return getPopularManga(page)
            }

            val newHeaders = headers.newBuilder()
                .set("Referer", if (page == 1) "$baseUrl/tags" else "$baseUrl/tag/$tagSlug/page/${page - 1}")
                .build()

            val pageStr = if (page != 1) "page/$page" else ""
            client.get("$baseUrl/tag/$tagSlug/$pageStr", newHeaders).asJsoup()
        }

        val mangas = document
            .select("div.lista > ul > li div.thumb-conteudo:has(a[href^=$baseUrl]):not(:contains(Tufos)):not(:has(span.selo-tipo:contains(Legendado)))")
            .map(::genericMangaFromElement)
        val hasNextPage = document.selectFirst("ul.paginacao li.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val post = document.select("div.post-box")
        val multipleChapters = document.select("div.listaImagens div.galeriaTab")

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = manga.title
            author = post.select("ul.post-itens li:contains(Artista:) a").text()
            genre = post.select("ul.post-itens li:contains(Tags:) a").joinToString { it.text() }
            description = post.select("ul.post-itens li:contains(Cor:)").text()
            status = SManga.COMPLETED
            thumbnail_url = post.select("div.post-capa img").attr("src")
            update_strategy = if (multipleChapters.isNotEmpty()) UpdateStrategy.ALWAYS_UPDATE else UpdateStrategy.ONLY_FETCH_ONCE
        }

        val chapterList = if (multipleChapters.isNotEmpty()) {
            multipleChapters.map(::chapterFromElement).reversed()
        } else {
            listOf(
                SChapter.create().apply {
                    name = "Capítulo"
                    chapter_number = 1f
                    setUrlWithoutDomain(document.location())
                },
            )
        }

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val chapterId = element.attr("data-id")
        val title = element.selectFirst("div.galeriaTabTitulo")?.text()

        name = "Capítulo $chapterId" + (if (!title.isNullOrEmpty()) " - $title" else "")
        chapter_number = chapterId.toFloatOrNull() ?: -1f
        setUrlWithoutDomain("${element.ownerDocument()!!.location()}#$chapterId")
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val chapterId = document.location().substringAfterLast("#", "")
        val gallerySelector = when {
            chapterId.isNotEmpty() -> "div.listaImagens #galeria-$chapterId img"
            else -> "div.listaImagens ul.post-fotos img"
        }

        return document.select(gallerySelector)
            .mapIndexed { i, el -> Page(i, url = document.location(), imageUrl = el.attr("src")) }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Referer", page.url)
        .build()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("Os filtros são ignorados na busca!"),
        TagFilter(getTags()),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
}
