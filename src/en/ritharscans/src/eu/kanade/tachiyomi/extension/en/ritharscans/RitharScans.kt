package eu.kanade.tachiyomi.extension.en.ritharscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy

class RitharScans : Keyoapp("RitharScans", "https://ritharscans.com", "en") {

    private val json: Json by injectLazy()

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = super.popularMangaParse(response).mangas
            .distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    override fun genresRequest() = GET("$baseUrl/search", headers)

    override fun parseGenres(document: Document): List<Genre> = document.select("[x-data*=genre] button").map {
        val name = it.text()
        val id = it.attr("wire:key")

        Genre(name, id)
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
    override val typeSelector = "[alt=Type]"

    override fun pageListParse(document: Document): List<Page> {
        val data = json.parseToJsonElement(document.selectFirst("script[type=\"application/ld+json\"]")!!.data()).jsonObject
        val seriesURL = data["isPartOf"]!!.jsonObject["url"]!!.jsonPrimitive.content
        val seriesID = seriesURL.substring(seriesURL.lastIndexOf('/') + 1)
        val chapterURL = data["url"]!!.jsonPrimitive.content
        val chapterID = chapterURL.substring(chapterURL.lastIndexOf('/') + 1)
        val numberOfPages = data["numberOfPages"]!!.jsonPrimitive.int

        return (1..numberOfPages).mapIndexed { i, page ->
            Page(
                i,
                document.location(),
                "$baseUrl/storage/series/webtoon/$seriesID/chapters/$chapterID/${page.toString().padStart(3, '0')}.jpg",
            )
        }
    }
}
