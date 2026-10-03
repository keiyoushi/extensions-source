package eu.kanade.tachiyomi.extension.es.heavenmanga

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter

@Source
abstract class HeavenManga : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/top?orderby=views&pages=$page").asJsoup()
        return parseMangaGrid(document)
    }

    private fun parseMangaGrid(document: Document): MangasPage {
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

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) baseUrl else "$baseUrl?pages=$page"
        val document = client.get(url).asJsoup()

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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        if (page > 1) url.addQueryParameter("pages", page.toString())

        val requestUrl = url.build()
        val document = client.get(requestUrl).asJsoup()

        if (!requestUrl.pathSegments.contains("buscar")) {
            return parseMangaGrid(document)
        }

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
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { fetchDetails(manga) } else null
        val chapterList = if (fetchChapters) async { fetchChapterList(manga) } else null

        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SManga.create().apply {
            title = manga.title
            document.select("div.tab-summary").let { info ->
                genre = info.select("div.genres-content a").joinToString { it.text() }
                thumbnail_url = info.select("div.summary_image img").attr("abs:data-src")
            }
            description = document.select("div.description-summary p").text()
        }
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val mangaUrl = (baseUrl + manga.url).toHttpUrl()
        val url = mangaUrl.newBuilder()
            .addQueryParameter("columns[0][data]", "number")
            .addQueryParameter("columns[0][orderable]", "true")
            .addQueryParameter("columns[1][data]", "created_at")
            .addQueryParameter("columns[1][searchable]", "true")
            .addQueryParameter("order[0][column]", "1")
            .addQueryParameter("order[0][dir]", "desc")
            .addQueryParameter("start", "0")
            .addQueryParameter("length", CHAPTER_LIST_LIMIT.toString())
            .build()

        val headers = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val result = client.get(url, headers).parseAs<PayloadChaptersDto>()
        val chapterBaseUrl = mangaUrl.toString().removeSuffix("/")

        return result.data
            .sortedByDescending { it.slug.toFloatOrNull() ?: 0f }
            .map {
                SChapter.create().apply {
                    name = "Capítulo: ${it.slug}"
                    setUrlWithoutDomain("$chapterBaseUrl/${it.slug}#${it.id}")
                    date_upload = dateFormat.tryParseDateTime(it.createdAt)
                }
            }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast("#")
        if (chapterId.isBlank()) throw Exception("Error al obtener el id del capítulo. Actualice la lista")

        val document = client.get("$baseUrl/manga/leer/$chapterId").asJsoup()
        val data = document.selectFirst("script:containsData(pUrl)")?.data()
            ?: throw Exception("Script pages no encontrado")
        val jsonString = PAGES_REGEX.find(data)?.groupValues?.get(1)?.removeTrailingComma()
            ?: throw Exception("No se pudo extraer el JSON de las páginas")

        val pages = jsonString.parseAs<List<PageDto>>()
        return pages.mapIndexed { i, dto -> Page(i, imageUrl = dto.imgURL) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("NOTA: Los filtros se ignoran si se utiliza la búsqueda de texto."),
        Filter.Header("Sólo se puede utilizar un filtro a la vez."),
        Filter.Separator(),
        GenreFilter(),
        AlphabeticoFilter(),
        ListaCompletasFilter(),
    )

    private fun String.removeTrailingComma() = replace(TRAILING_COMMA_REGEX, "$1")

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val PAGES_REGEX = """pUrl\s*=\s*(\[[\s\S]*?\])\s*;""".toRegex()
        val TRAILING_COMMA_REGEX = """,\s*(\}|\])""".toRegex()
        private const val CHAPTER_LIST_LIMIT = 10000
    }
}
