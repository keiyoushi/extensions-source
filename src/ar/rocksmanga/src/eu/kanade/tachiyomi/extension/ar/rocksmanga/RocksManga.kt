package eu.kanade.tachiyomi.extension.ar.rocksmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class RocksManga :
    Madara(
        "Rocks Manga",
        "https://rocksmanga.com",
        "ar",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("ar")),
    ) {

    override fun popularMangaSelector() = ".unit .inner"
    override val popularMangaUrlSelector = ".info a"
    override fun popularMangaNextPageSelector() = "li.page-item:not(.disabled) > a.page-link[rel=next]"

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override val mangaDetailsSelectorTitle = ".info h1"
    override val mangaDetailsSelectorAuthor = "div.meta span:contains(المؤلف:) + a"
    override val mangaDetailsSelectorArtist = "div.meta span:contains(الرسام:) + a"
    override val mangaDetailsSelectorStatus = ".info p"
    override val mangaDetailsSelectorDescription = "div.description"
    override val mangaDetailsSelectorThumbnail = ".manga-poster img"
    override val mangaDetailsSelectorGenre = "div.meta span:contains(التصنيفات:) ~ a"
    override val altNameSelector = ".info h6"
    override fun chapterListSelector() = "div.list-body-hh ul li"
    override fun chapterDateSelector() = "span.time"
    override val pageListParseSelector = "#ch-images .img"
    override val chapterUrlSuffix = ""

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
    override val filterNonMangaItems = false

    override fun searchRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/".toHttpUrl().newBuilder()
        return if (query.isNotBlank()) {
            urlBuilder.addPathSegment(searchPage(page))
            urlBuilder.addQueryParameter("s", query)
            GET(urlBuilder.build(), headers)
        } else {
            var pathSegments: String? = null

            filters.forEach { filter ->
                val segment = when (filter) {
                    is TypeFilter -> filter.toUriPart()
                    is GenreFilter -> filter.toUriPart()
                    else -> ""
                }

                if (segment.isNotBlank()) {
                    pathSegments = when (filter) {
                        is TypeFilter -> "manga-type/$segment/${searchPage(page)}"
                        is GenreFilter -> "manga-genre/$segment/${searchPage(page)}"
                        else -> pathSegments
                    }
                }
            }

            pathSegments?.let { urlBuilder.addPathSegments(it) }
            GET(urlBuilder.build(), headers)
        }
    }
    private val typeFilters: Array<Pair<String, String>> = arrayOf(
        "ALL" to "",
        "كوميك" to "comic",
        "مانجا" to "manga",
        "مانها" to "manhua",
        "مانهوا" to "manhwa",
        "ون شوت" to "one-shot",
    )

    private class GenreFilter(vals: Array<Pair<String, String>>) : UriPartFilter("Category", vals)
    private class TypeFilter(vals: Array<Pair<String, String>>) : UriPartFilter("Type", vals)

    override fun parseGenres(document: Document): List<Genre> = document
        .selectFirst("#nav-menu li:contains(التصنيفات) ul")
        ?.select("li a")
        .orEmpty()
        .map { a ->
            Genre(
                a!!.text(),
                a.attr("href")
                    .substringAfter("/manga-genre/")
                    .substringBefore("/"),
            )
        }

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        return FilterList(
            if (genresList.isNotEmpty()) {
                listOf(
                    Filter.Header("NOTE: Filters are ignored when using text search."),
                    Filter.Header("NOTE: Type and Genre cannot be used together. Genre will take priority."),
                    Filter.Separator(),
                    TypeFilter(typeFilters),
                    GenreFilter(arrayOf(Pair("ALL", "")) + genresList.map { Pair(it.name, it.id) }.toTypedArray()),
                )
            } else {
                listOf(
                    TypeFilter(typeFilters),
                    Filter.Header(intl["genre_missing_warning"]),
                )
            },
        )
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = super.chapterFromElement(element)
        chapter.name = element.selectFirst("zebi")!!.text()
        chapter.scanlator = element.selectFirst(".username span")?.text()
        return chapter
    }
}
