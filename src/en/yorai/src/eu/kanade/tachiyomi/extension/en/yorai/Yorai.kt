package eu.kanade.tachiyomi.extension.en.yorai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Yorai : HttpSource() {

    override val name = "Yorai"
    override val baseUrl = "https://yorai.io"

    val apiUrl = "$baseUrl/api"
    override val lang = "en"
    override val supportsLatest = true
    override val versionId = 2

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/comics/browse?page=$page&sort=views", headers)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/comics/browse?page=$page", headers)

    // Search
    override fun getFilterList(): FilterList = getFilters()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/comics/browse".toHttpUrl().newBuilder().apply {
            val tags: MutableList<String> = mutableListOf()
            val genres: MutableList<String> = mutableListOf()
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", filter.selected)
                        addQueryParameter("order", if (filter.state!!.ascending) "asc" else "desc")
                    }

                    is GenreFilter -> {
                        val (activeFilters, _) = filter.state.partition { stIt -> stIt.state }
                        activeFilters.forEach {
                            genres.add(it.name.lowercase().replace(" ", "_"))
                        }
                    }

                    is StatusFilter -> {
                        addQueryParameter("statuses", filter.selected)
                    }

                    is TypeFilter -> {
                        addQueryParameter("types", filter.selected)
                    }

                    else -> {}
                }
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("genres", genres.joinToString(","))
            addQueryParameter("q", query)
        }.build()

        return GET(url, headers)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/comic/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, number) = chapter.url.split("|")
        return "$baseUrl/comic/$slug/chapter/$number"
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<Browse>()

        with(data) {
            val hasNextPage = page <= totalPages
            return MangasPage(
                comics.map {
                    SManga.create().apply {
                        url = it.slug
                        title = it.title
                        thumbnail_url = baseUrl + it.coverUrl
                    }
                },
                hasNextPage,
            )
        }
    }
    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val detailsBody = response.body.string()

        val desc = detailsBody.extractNextJsRsc<Description> {
            it is JsonObject && it["name"]?.jsonPrimitive?.content == "description"
        }

        val tags = detailsBody.extractNextJsRsc<List<Tag>>()?.map { it.name } ?: emptyList()

        return SManga.create().apply {
            status = if (detailsBody.contains("releasing")) SManga.ONGOING else SManga.COMPLETED
            genre = tags.joinToString(", ") { "⟡$it" }
            description = desc?.content
        }
    }
    // Chapters

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.extractNextJs<Chapters>()!!

        return chapters.chapters.filter { it.sourceName == chapters.defaultSource }.map {
            SChapter.create().apply {
                url = "${chapters.slug}|${it.number}"
                name = it.title.takeIf { it.isNotEmpty() } ?: "Chapter ${it.number.toInt()}"
                chapter_number = it.number
                scanlator = chapters.defaultSource
            }
        }
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), rscHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val data = response.extractNextJs<ChapterPages>()
        return data!!.imageUrls.mapIndexed { index, url ->
            Page(index, imageUrl = baseUrl + url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
