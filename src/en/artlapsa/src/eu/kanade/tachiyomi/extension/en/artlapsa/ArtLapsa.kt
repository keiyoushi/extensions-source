package eu.kanade.tachiyomi.extension.en.artlapsa

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder

class ArtLapsa : Keyoapp("Art Lapsa", "https://artlapsa.com", "en") {

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = super.popularMangaParse(response).mangas.distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    override fun genresRequest() = GET("$baseUrl/search/", headers)

    override fun parseGenres(document: Document): List<Genre> = document.select("[x-data*=genre] button").map {
        val name = it.text()
        val id = it.attr("wire:key")

        Genre(name, id)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url =
            "$baseUrl/search"
                .toHttpUrl()
                .newBuilder()
                .apply {
                    if (page > 1) {
                        addQueryParameter("page", page.toString())
                    }
                    if (query.isNotBlank()) {
                        addQueryParameter("title", query)
                    }
                    filters.firstOrNull { it is GenreList }?.also {
                        val filter = it as GenreList
                        filter.state.filter { it.state }.forEach { genre ->
                            addQueryParameter("genre", genre.id)
                        }
                    }
                }
                .build()

        return GET(url, headers)
    }

    // Only the cover link per card (has the image inside) so thumbnails are correct
    override fun searchMangaSelector() = "main#main-content a[href*='/series/']:has([style*='background-image'])"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element
        val rawHref = link.attr("href")
        val url =
            link.attr("abs:href").ifBlank {
                if (rawHref.startsWith("http")) {
                    rawHref
                } else {
                    baseUrl + "/" + rawHref.trimStart('/')
                }
            }
        setUrlWithoutDomain(url)
        title = link.attr("title").ifBlank { link.selectFirst("h3")?.text()?.trim() ?: "" }
        thumbnail_url =
            link.getImageUrl("*[style*=background-image]")
                ?: link.parent()
                    ?.parent()
                    ?.parent()
                    ?.parent()
                    ?.getImageUrl("*[style*=background-image]")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        val document = response.asJsoup()
        val main =
            document.selectFirst("main#main-content") ?: return MangasPage(emptyList(), false)
        var links = main.select("a[href*='/series/']:has([style*='background-image'])")
        if (links.isEmpty()) links = main.select("a[href*='/series/']")
        val mangas =
            links.map(::searchMangaFromElement)
                .filter { it.title.isNotBlank() && it.url.isNotBlank() }
                .distinctBy { it.url }
        val hasNextPage = mangas.size >= 20
        return MangasPage(mangas, hasNextPage)
    }

    override val descriptionSelector = "#expand_content"
    override val statusSelector = "[alt=Status]"
    override val typeSelector = "[alt=Type]"

    override fun chapterListSelector(): String {
        if (!preferences.showPaidChapters) {
            return "#chapters > a:not(:has(.text-sm span:matches(Upcoming))):not(:has(img[src*=star-circle]))"
        }
        return "#chapters > a:not(:has(.text-sm span:matches(Upcoming)))"
    }

    override fun pageListParse(document: Document): List<Page> {
        val (pages, baseLink) =
            document.selectFirst("[x-data*=pages]")!!.attr("x-data").replace(spaces, "").let {
                val pages =
                    pagesRegex.find(it)!!.groupValues[1]
                        .let { encoded -> URLDecoder.decode(encoded, "UTF-8") }
                        .parseAs<List<Path>>()

                val baseLink = linkRegex.find(it)!!.groupValues[2]

                pages to baseLink
            }

        return pages.mapIndexed { i, img -> Page(i, document.location(), baseLink + img.path) }
    }
}

private val spaces = Regex("\\s")
private val pagesRegex = Regex("pages:(\\[[^]]+])")
private val linkRegex = """baseLink:(["'])(.+?)\1""".toRegex()

@Serializable
class Path(
    val path: String,
)
