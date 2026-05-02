package eu.kanade.tachiyomi.extension.es.heavenmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class HeavenManga : HttpSource() {

    override val name = "HeavenManga"

    override val baseUrl = "https://heavenmanga.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top?orderby=views&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.page-item-detail").map { element ->
            SManga.create().apply {
                title = element.select("div.manga-name").text()
                setUrlWithoutDomain(element.select("a").attr("abs:href"))
                thumbnail_url = element.select("img").attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = if (page == 1) {
        GET(baseUrl, headers)
    } else {
        GET("$baseUrl?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.selectFirst("div.col-lg-8 > div#loop-content")
            ?.select("div.list-group-item:not(:has(div:containsOwn(Novela)))")
            ?.map { element ->
                SManga.create().apply {
                    with(element.selectFirst("a")!!) {
                        val mangaUrl = attr("abs:href").substringBeforeLast("/")
                        setUrlWithoutDomain(mangaUrl)
                        title = selectFirst(".captitle")?.text() ?: text()
                        thumbnail_url = mangaUrl.replace("/manga/", "/uploads/manga/") + "/cover/cover_250x350.jpg"
                    }
                }
            }
            ?.distinctBy { it.url }
            ?: emptyList()

        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            if (query.length < 3) throw Exception("La búsqueda debe tener al menos 3 caracteres")
            url.addPathSegment("buscar")
                .addQueryParameter("query", query)
        } else {
            val ext = ".html"
            var name: String
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        if (filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            url.addPathSegment("genero")
                                .addPathSegment(name + ext)
                        }
                    }

                    is AlphabeticoFilter -> {
                        if (filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            url.addPathSegment("letra")
                                .addPathSegment("manga$ext")
                                .addQueryParameter("alpha", name)
                        }
                    }

                    is ListaCompletasFilter -> {
                        if (filter.toUriPart().isNotBlank() && filter.state != 0) {
                            name = filter.toUriPart()
                            url.addPathSegment(name)
                        }
                    }

                    else -> {}
                }
            }
        }

        if (page > 1) url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = if (response.request.url.pathSegments.contains("buscar")) {
        val document = response.asJsoup()
        val mangas = document.select("div.c-tabs-item__content").map { element ->
            SManga.create().apply {
                element.select("h4 a").let {
                    title = it.text()
                    setUrlWithoutDomain(it.attr("abs:href"))
                }
                thumbnail_url = element.select("img").attr("abs:data-src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null
        MangasPage(mangas, hasNextPage)
    } else {
        popularMangaParse(response)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            document.select("div.tab-summary").let { info ->
                genre = info.select("div.genres-content a").joinToString { it.text() }
                thumbnail_url = info.select("div.summary_image img").attr("abs:data-src")
            }
            description = document.select("div.description-summary p").text()
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaUrl = (baseUrl + manga.url).toHttpUrl().newBuilder()
            .addQueryParameter("columns[0][data]", "number")
            .addQueryParameter("columns[0][orderable]", "true")
            .addQueryParameter("columns[1][data]", "created_at")
            .addQueryParameter("columns[1][searchable]", "true")
            .addQueryParameter("order[0][column]", "1")
            .addQueryParameter("order[0][dir]", "desc")
            .addQueryParameter("start", "0")
            .addQueryParameter("length", CHAPTER_LIST_LIMIT.toString())

        val headers = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return GET(mangaUrl.build(), headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaUrl = response.request.url.toString().substringBefore("?").removeSuffix("/")
        val result = response.parseAs<PayloadChaptersDto>()

        return result.data
            .sortedByDescending { it.slug.toFloatOrNull() ?: 0f }
            .map {
                SChapter.create().apply {
                    name = "Capítulo: ${it.slug}"
                    setUrlWithoutDomain("$mangaUrl/${it.slug}#${it.id}")
                    date_upload = dateFormat.tryParse(it.createdAt)
                }
            }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("#")
        if (chapterId.isBlank()) throw Exception("Error al obtener el id del capítulo. Actualice la lista")
        val url = "$baseUrl/manga/leer/$chapterId"
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val data = document.selectFirst("script:containsData(pUrl)")?.data()
            ?: throw Exception("Script pages no encontrado")
        val jsonString = PAGES_REGEX.find(data)?.groupValues?.get(1)?.removeTrailingComma()
            ?: throw Exception("No se pudo extraer el JSON de las páginas")

        val pages = jsonString.parseAs<List<PageDto>>()
        return pages.mapIndexed { i, dto -> Page(i, imageUrl = dto.imgURL) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        eu.kanade.tachiyomi.source.model.Filter.Header("NOTA: Los filtros se ignoran si se utiliza la búsqueda de texto."),
        eu.kanade.tachiyomi.source.model.Filter.Header("Sólo se puede utilizar un filtro a la vez."),
        eu.kanade.tachiyomi.source.model.Filter.Separator(),
        GenreFilter(),
        AlphabeticoFilter(),
        ListaCompletasFilter(),
    )

    private fun String.removeTrailingComma() = replace(TRAILING_COMMA_REGEX, "$1")

    companion object {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val PAGES_REGEX = """pUrl\s*=\s*(\[[\s\S]*?\])\s*;""".toRegex()
        val TRAILING_COMMA_REGEX = """,\s*(\}|\])""".toRegex()
        private const val CHAPTER_LIST_LIMIT = 10000
    }
}
