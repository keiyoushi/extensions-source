package eu.kanade.tachiyomi.extension.es.komiwa

import android.util.Base64
import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.InjectionEvent
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Komiwa : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host.removePrefix("www.")

    override suspend fun getPopularManga(page: Int) = fetchCatalog(CatalogBodyDto(page, PAGE_SIZE, "views", "desc"))

    override suspend fun getLatestUpdates(page: Int) = fetchCatalog(CatalogBodyDto(page, PAGE_SIZE, "updatedAt", "desc"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        return fetchCatalog(
            CatalogBodyDto(
                page = page,
                limit = PAGE_SIZE,
                sortBy = sort?.sortBy ?: "views",
                sortOrder = sort?.sortOrder ?: "desc",
                q = query.trim().ifEmpty { null },
                genres = filters.firstInstanceOrNull<GenreFilter>()?.checked,
                types = filters.firstInstanceOrNull<TypeFilter>()?.checked,
                demographics = filters.firstInstanceOrNull<DemographyFilter>()?.checked,
                status = filters.firstInstanceOrNull<StatusFilter>()?.selected,
                ageRating = filters.firstInstanceOrNull<AgeRatingFilter>()?.selected,
            ),
        )
    }

    private suspend fun fetchCatalog(body: CatalogBodyDto): MangasPage {
        val data = client.post("$API_URL/manga/catalog", Fetch.XHR.from("/catalog"), body.toJsonRequestBody())
            .parseAs<ResponseDto<CatalogDto>>()
            .data
        return MangasPage(data.items.map { it.toSManga() }, data.hasMore)
    }

    // Genre, type and demography pages are the catalog filtered by that value
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") != host || segments.size != 2 || segments[0] !in LIST_PATHS) {
            return super.getMangasByUrl(url, page)
        }
        val filters = fetchFilters()
        val slug = segments[1]
        val body = when (segments[0]) {
            "genero" -> CatalogBodyDto(page, PAGE_SIZE, "views", "desc", genres = filters.genres.idOf(slug) ?: return MangasPage(emptyList(), false))
            "tipo" -> CatalogBodyDto(page, PAGE_SIZE, "views", "desc", types = filters.types.idOf(slug) ?: return MangasPage(emptyList(), false))
            else -> CatalogBodyDto(page, PAGE_SIZE, "views", "desc", demographics = filters.demographics.idOf(slug) ?: return MangasPage(emptyList(), false))
        }
        return fetchCatalog(body)
    }

    private fun List<TaxonomyDto>.idOf(slug: String) = firstOrNull { it.slug == slug }?.id?.let(::listOf)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") != host || segments.size != 2) return null
        val slug = when (segments[0]) {
            "manga" -> segments[1]
            "chapter" -> fetchChapterSeries(segments[1])
            else -> return null
        }
        return fetchMangaPage(slug).toSMangaDetails().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/chapter/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = fetchMangaPage(manga.url)
        return SMangaUpdate(
            if (fetchDetails) details.toSMangaDetails() else manga,
            if (fetchChapters) fetchChapters(details.id, manga) else chapters,
        )
    }

    private suspend fun fetchMangaPage(slug: String) = client.get("$baseUrl/manga/$slug", Fetch.DOCUMENT)
        .extractNextJs<MangaDto> { it is JsonObject && "slug" in it && "synopsis" in it }!!

    // The series page's own request for its full chapter list
    private suspend fun fetchChapters(id: String, manga: SManga): List<SChapter> {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("manga/$id/chapters")
            .addQueryParameter("page", "1")
            .addQueryParameter("limit", "99999")
            .addQueryParameter("sort", "desc")
            .build()
        return client.get(url.toString(), Fetch.XHR.from(getMangaUrl(manga)))
            .parseAs<ResponseDto<ChapterListDto>>()
            .data.chapters.map { it.toSChapter() }
    }

    private suspend fun fetchChapterSeries(chapterId: String) = client.get("$baseUrl/chapter/$chapterId", Fetch.DOCUMENT)
        .asJsoup()
        .select("script[type=application/ld+json]")
        .map { it.data().parseAs<JsonElement>() }
        .filterIsInstance<JsonObject>()
        .firstNotNullOf { it.parseAs<LinkedDataDto>().isPartOf }
        .url.substringAfterLast('/')

    // The site's reader decodes its pages in its own script; this reads the images it renders
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val origins = setOf(baseUrl)
        val script = javaClass.getResource("/assets/pages.js")!!.readText()
        val pages = try {
            runWebView<List<String>>(READER_TIMEOUT) {
                webMessageBridge(BRIDGE, origins, WORLD) { message, _ -> resolve(message.parseAs()) }
                addJavaScriptOnEvent("($script)('$BRIDGE')", InjectionEvent.DOCUMENT_START, WORLD, origins)
                // In place of the app's package name, which tells the site the page is open in the app
                setOriginMatchedHeader("X-Requested-With", "", origins)
                // A loaded pixel for each page image, which the app downloads itself
                interceptRequest { request ->
                    if (request.requestHeaders["Accept"]?.startsWith("image/") == true) pixel() else null
                }
                onReceivedError { request, error ->
                    if (request.isForMainFrame) reject(IOException("No se pudo abrir el capítulo: ${error.errorCode}"))
                }
                loadUrl(getChapterUrl(chapter))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: UnsupportedOperationException) {
            throw IOException("Actualiza Android System WebView para leer este capítulo", e)
        } catch (e: WebViewTimeoutException) {
            throw IOException("El capítulo no cargó a tiempo", e)
        } catch (e: Throwable) {
            throw IOException(e.message, e)
        }
        if (pages.isEmpty()) throw IOException("No se encontraron páginas en el capítulo")
        return pages.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private fun pixel() = WebResourceResponse("image/gif", null, ByteArrayInputStream(PIXEL))

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = fetchFilters().toJsonElement()

    private suspend fun fetchFilters() = client.get("$baseUrl/catalog", Fetch.DOCUMENT)
        .extractNextJs<FiltersDto>()!!

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = data?.parseAs<FiltersDto>()
        return FilterList(
            listOfNotNull(
                SortFilter(),
                AgeRatingFilter(),
                filters?.let { StatusFilter(it.statuses) },
                filters?.let { TypeFilter(it.types) },
                filters?.let { DemographyFilter(it.demographics) },
                filters?.let { GenreFilter(it.genres) },
            ),
        )
    }

    companion object {
        private const val API_URL = "https://b78sk.komiwa.net"
        private const val PAGE_SIZE = 24
        private val LIST_PATHS = setOf("genero", "tipo", "demografia")
        private const val BRIDGE = "komiwaReader"
        private const val WORLD = "komiwa"
        private val READER_TIMEOUT = 30.seconds

        // A 1x1 transparent GIF, served for the reader's images instead of downloading them
        private val PIXEL = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)
    }
}
