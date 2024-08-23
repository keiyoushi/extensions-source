package eu.kanade.tachiyomi.extension.es.leercomicsonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

class LeerComicsOnline : ParsedHttpSource() {

    override val name = "Leer Comics Online"
    override val baseUrl = "https://leercomicsonline.com"
    override val lang = "es"
    override val supportsLatest = false

    private val json: Json by injectLazy()

    private val comicsPerPage = 20

    override fun popularMangaRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("api")
            addPathSegments("series")
            addQueryParameter("page", page.toString())
        }.build(),
        headers,
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("api")
                addPathSegments("series")
                addQueryParameter("page", page.toString())
                addQueryParameter("search", query)
            }.build(),
            headers,
        )

    override fun popularMangaParse(response: Response): MangasPage {
        val page = response.request.url.queryParameter("page")!!.toInt()
        val comics = json.decodeFromString<List<Comic>>(response.body.string())
        return MangasPage(
            comics.subList((page - 1) * comicsPerPage, page * comicsPerPage).map { comic ->
                SManga.create().apply {
                    title = comic.title
                    thumbnail_url = baseUrl.toHttpUrl().newBuilder().apply {
                        addPathSegment("images")
                        addPathSegment("${comic.url}-300x461.jpg")
                    }.build().toString()
                    setUrlWithoutDomain(
                        baseUrl.toHttpUrl().newBuilder().apply {
                            addPathSegment("api")
                            addPathSegments("comics")
                            addQueryParameter("serieId", comic.id.toString())
                        }.build().toString(),
                    )
                }
            },
            comics.count() > (page - 1) * comicsPerPage,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangasPage = popularMangaParse(response)
        val searchQuery = response.request.url.queryParameter("search").toString()
        return MangasPage(
            mangasPage.mangas.filter {
                it.title.lowercase().contains(searchQuery, ignoreCase = true)
            },
            mangasPage.hasNextPage,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> =
        json.decodeFromString<List<Comic>>(response.body.string()).reversed().map {
            SChapter.create().apply {
                name = it.title
                setUrlWithoutDomain(
                    baseUrl.toHttpUrl().newBuilder().apply {
                        addPathSegment("api")
                        addPathSegment("pages")
                        addQueryParameter("id", it.id.toString())
                        addQueryParameter(
                            "letter",
                            it.url.first().toString(),
                        )
                    }.build().toString(),
                )
            }
        }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create()

    override fun pageListParse(response: Response): List<Page> {
        return try { // some chapters are just empty
            json.decodeFromString<List<String>>(response.body.string()).mapIndexed { index, url ->
                Page(index, imageUrl = url)
            }
        } catch (exception: Exception) {
            emptyList()
        }
    }

    @Serializable
    class Comic(
        val title: String,
        val url: String,
        val id: Int,
    )

    override fun popularMangaSelector() = throw UnsupportedOperationException()
    override fun searchMangaSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()
    override fun popularMangaFromElement(response: Element): SManga = throw UnsupportedOperationException()
    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()
    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException()
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()
}
