package eu.kanade.tachiyomi.extension.pt.mundohentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class MundoHentai : HttpSource() {

    override val name = "Mundo Hentai"

    override val baseUrl = "https://mundohentaioficial.com"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val versionId: Int = 2

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    private fun genericMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("span.thumb-titulo").text()
        thumbnail_url = element.select("img.attachment-post-thumbnail").attr("src")
        setUrlWithoutDomain(element.select("a:has(span.thumb-imagem)").attr("href"))
    }

    override fun popularMangaRequest(page: Int): Request {
        val newHeaders = headersBuilder()
            .set("Referer", if (page == 1) baseUrl else "$baseUrl/category/doujinshi/page/${page - 1}")
            .build()

        val pageStr = if (page != 1) "page/$page" else ""
        return GET("$baseUrl/category/doujinshi/$pageStr", newHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document
            .select("div.lista > ul > li div.thumb-conteudo:has(a[href^=$baseUrl]):not(:contains(Tufos))")
            .map(::genericMangaFromElement)
        val hasNextPage = document.selectFirst("ul.paginacao li.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .toString()
            return GET(url, headers)
        }

        val tagFilter = filters[1] as TagFilter
        val tagSlug = tagFilter.values[tagFilter.state].slug

        if (tagSlug.isEmpty()) {
            return popularMangaRequest(page)
        }

        val newHeaders = headersBuilder()
            .set("Referer", if (page == 1) "$baseUrl/tags" else "$baseUrl/tag/$tagSlug/page/${page - 1}")
            .build()

        val pageStr = if (page != 1) "page/$page" else ""
        return GET("$baseUrl/tag/$tagSlug/$pageStr", newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document
            .select("div.lista > ul > li div.thumb-conteudo:has(a[href^=$baseUrl]):not(:contains(Tufos)):not(:has(span.selo-tipo:contains(Legendado)))")
            .map(::genericMangaFromElement)
        val hasNextPage = document.selectFirst("ul.paginacao li.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val post = document.select("div.post-box")
        val isMultipleChapters = document.selectFirst("div.listaImagens div.galeriaTab") != null

        return SManga.create().apply {
            author = post.select("ul.post-itens li:contains(Artista:) a").text()
            genre = post.select("ul.post-itens li:contains(Tags:) a").joinToString { it.text() }
            description = post.select("ul.post-itens li:contains(Cor:)").text()
            status = SManga.COMPLETED
            thumbnail_url = post.select("div.post-capa img").attr("src")
            update_strategy = if (isMultipleChapters) UpdateStrategy.ALWAYS_UPDATE else UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val multipleChapters = document.select("div.listaImagens div.galeriaTab")

        if (multipleChapters.isNotEmpty()) {
            return multipleChapters.map(::chapterFromElement).reversed()
        }

        val singleChapter = SChapter.create().apply {
            name = "Capítulo"
            chapter_number = 1f
            setUrlWithoutDomain(document.location())
        }

        return listOf(singleChapter)
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val chapterId = element.attr("data-id")
        val title = element.selectFirst("div.galeriaTabTitulo")?.text()

        name = "Capítulo $chapterId" + (if (!title.isNullOrEmpty()) " - $title" else "")
        chapter_number = chapterId.toFloatOrNull() ?: -1f
        setUrlWithoutDomain("${element.ownerDocument()!!.location()}#$chapterId")
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapterId = document.location().substringAfterLast("#", "")
        val gallerySelector = when {
            chapterId.isNotEmpty() -> "div.listaImagens #galeria-$chapterId img"
            else -> "div.listaImagens ul.post-fotos img"
        }

        return document.select(gallerySelector)
            .mapIndexed { i, el -> Page(i, url = document.location(), imageUrl = el.attr("src")) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Os filtros são ignorados na busca!"),
        TagFilter(getTags()),
    )

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
}
