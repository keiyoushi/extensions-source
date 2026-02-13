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

    override fun parseGenres(document: Document): List<Genre> = document.select("[x-data*=genre] button").map { Genre(it.text(), it.attr("wire:key")) }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            if (page > 1) addQueryParameter("page", page.toString())
            if (query.isNotBlank()) addQueryParameter("title", query)
            (filters.firstOrNull { it is GenreList } as? GenreList)?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("genre", it.id) }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = "main#main-content a[href*='/series/']:has([style*='background-image'])"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val href = element.attr("abs:href").ifBlank {
            element.attr("href").let { h -> if (h.startsWith("http")) h else baseUrl + "/" + h.trimStart('/') }
        }
        setUrlWithoutDomain(href)
        title = element.attr("title").ifBlank { element.selectFirst("h3")?.text()?.trim() ?: "" }
        thumbnail_url = element.getImageUrl("*[style*=background-image]")
            ?: element.parent()?.parent()?.parent()?.parent()?.getImageUrl("*[style*=background-image]")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        val main = response.asJsoup().selectFirst("main#main-content") ?: return MangasPage(emptyList(), false)
        val links = main.select("a[href*='/series/']:has([style*='background-image'])").ifEmpty {
            main.select("a[href*='/series/']")
        }
        val mangas = links.map(::searchMangaFromElement)
            .filter { it.title.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.url }
        return MangasPage(mangas, hasNextPage = mangas.size >= 20)
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
        val xData = document.selectFirst("[x-data*=pages]")!!.attr("x-data").replace(spaces, "")
        val pages = URLDecoder.decode(pagesRegex.find(xData)!!.groupValues[1], "UTF-8").parseAs<List<Path>>()
        val baseLink = linkRegex.find(xData)!!.groupValues[2]
        return pages.mapIndexed { i, img -> Page(i, document.location(), baseLink + img.path) }
    }
}

private val spaces = Regex("\\s")
private val pagesRegex = Regex("pages:(\\[[^]]+])")
private val linkRegex = """baseLink:(["'])(.+?)\1""".toRegex()

@Serializable
class Path(val path: String)
