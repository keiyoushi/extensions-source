package eu.kanade.tachiyomi.extension.en.ritharscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class RitharScans : Keyoapp("RitharScans", "https://ritharscans.com", "en") {

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = super.popularMangaParse(response).mangas
            .distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    override fun genresRequest() = GET("$baseUrl/search", headers)

    override fun parseGenres(document: Document): List<Genre> {
        return document.select("[x-data*=genre] button").map {
            val name = it.text()
            val id = it.attr("wire:key")

            Genre(name, id)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            if (query.isNotBlank()) {
                addQueryParameter("title", query)
            }
            filters.firstOrNull { it is GenreList }?.also {
                val filter = it as GenreList
                filter.state
                    .filter { it.state }
                    .forEach { genre ->
                        addQueryParameter("genre", genre.id)
                    }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "[wire:snapshot*=pages.search] button[tags]"

    override fun searchMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }

        val mangas = response.asJsoup()
            .select(searchMangaSelector())
            .map(::searchMangaFromElement)

        return MangasPage(mangas, false)
    }

    override val descriptionSelector = "#expand_content"
    override val statusSelector = "[alt=Status]"
    override val genreSelector = "[alt=Type]"

    override fun pageListParse(document: Document): List<Page> {
        val (pages, baseLink) = document.selectFirst("[x-data]")!!.attr("x-data")
            .replace(spaces, "")
            .let {
                val pages = pagesRegex.find(it)!!.groupValues[1]
                    .replace("&quot;", "\"")
                    .parseAs<List<Path>>()

                val baseLink = linkRegex.find(
                    it.replace("\"", "'"),
                )!!.groupValues[1]

                pages to baseLink
            }

        return pages.mapIndexed { i, img ->
            Page(i, document.location(), baseLink + img.path)
        }
    }
}

private val spaces = Regex("\\s")
private val pagesRegex = Regex("pages:(\\[[^]]+])")
private val linkRegex = Regex("baseLink:'([^']+)'")

@Serializable
class Path(
    val path: String,
)
