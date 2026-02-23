package eu.kanade.tachiyomi.extension.ja.rawdevartart

import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.ChapterResponseDto
import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.MangaListResponseDto
import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.MangaResponseDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class Rawdevartart : HttpSource() {

    override val name = "Rawdevart.art"

    override val lang = "ja"

    override val baseUrl = "https://rawdevart.art"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) = searchMangaRequest(
        page,
        "",
        FilterList(
            SortFilter(1),
            GenreFilter(genres),
        ),
    )

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(
        page,
        "",
        FilterList(
            SortFilter(0),
            GenreFilter(genres),
        ),
    )

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/spa".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())

            if (query.isNotEmpty()) {
                addPathSegment("search")
                addQueryParameter("query", query)

                return@apply
            }

            (if (filters.isEmpty()) getFilterList() else filters).forEach { f ->
                when (f) {
                    is UriFilter -> f.addToUri(this)
                    is GenreFilter -> {
                        addPathSegment("genre")
                        addPathSegment(f.values[f.state].path)
                    }

                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MangaListResponseDto>()

        return data.toMangasPage()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<MangaResponseDto>()

        return data.toSManga()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<MangaResponseDto>()

        return data.toSChapterList()
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ChapterResponseDto>()

        return data.toPageList()
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Filters are ignored when using text search."),
        StatusFilter(),
        SortFilter(),
        GenreFilter(genres),
    )

    private inline fun <reified T> Response.parseAs(): T = json.decodeFromString(body.string())
}
