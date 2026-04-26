package eu.kanade.tachiyomi.extension.en.artlapsa

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

class ArtLapsa : Keyoapp("Art Lapsa", "https://artlapsa.com", "en") {

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = super.popularMangaParse(response).mangas.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override fun genresRequest() = GET("$baseUrl/search", headers)

    override fun parseGenres(document: Document): List<Genre> = document.select("[wire:model.live=genre] option:not(:contains(All))").map { Genre(it.text(), it.attr("value")) }

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

    override fun searchMangaSelector() = "main#main-content [wire:key*='serie']"

    override fun searchMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map(::searchMangaFromElement)
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
        val data = document.selectFirst("script[type=\"application/ld+json\"]")!!.data().parseAs<ChapterLD>()
        val seriesID = data.url.substring(data.url.lastIndexOf('/') + 1)
        val chapterID = data.isPartOf.url.substring(data.isPartOf.url.lastIndexOf('/') + 1)

        return (1..data.numberOfPages).mapIndexed { i, page ->
            Page(
                i,
                document.location(),
                "$baseUrl/storage/series/webtoon/$seriesID/chapters/$chapterID/${page.toString().padStart(3, '0')}.jpg",
            )
        }
    }
}

@Serializable
internal class ChapterLD(
    val isPartOf: SeriesLD,
    val numberOfPages: Int,
    val url: String,
)

@Serializable
internal class SeriesLD(
    val url: String,
)
