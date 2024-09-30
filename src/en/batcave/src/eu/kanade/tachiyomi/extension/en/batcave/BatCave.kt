package eu.kanade.tachiyomi.extension.en.batcave

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class BatCave : HttpSource() {

    override val name = "BatCave"
    override val lang = "en"
    override val supportsLatest = true
    override val baseUrl = "https://batcave.biz"

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", SortFilter.POPULAR)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortFilter.LATEST)
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
                addPathSegment(query.trim())
                if (page > 1) {
                    addPathSegments("page/$page/")
                }
            }.build()

            return GET(url, headers)
        }

        var filtersApplied = false

        val url = "$baseUrl/comix/".toHttpUrl().newBuilder().apply {
            filters.get<YearFilter>()?.addFilterToUrl(this)
                ?.also { filtersApplied = it }
            filters.get<PublisherFilter>()?.addFilterToUrl(this)
                ?.also { filtersApplied = filtersApplied || it }
            filters.get<GenreFilter>()?.addFilterToUrl(this)
                ?.also { filtersApplied = filtersApplied || it }

            if (filtersApplied) {
                setPathSegment(0, "ComicList")
            }
            if (page > 1) {
                addPathSegments("page/$page/")
            }
        }.build().toString()

        val sort = filters.get<SortFilter>()!!

        return if (sort.getSort() == "") {
            GET(url, headers)
        } else {
            val form = FormBody.Builder().apply {
                add("dlenewssortby", sort.getSort())
                add("dledirection", sort.getDirection())
                if (filtersApplied) {
                    add("set_new_sort", "dle_sort_xfilter")
                    add("set_direction_sort", "dle_direction_xfilter")
                } else {
                    add("set_new_sort", "dle_sort_cat_1")
                    add("set_direction_sort", "dle_direction_cat_1")
                }
            }.build()

            POST(url, headers, form)
        }
    }

    private var publishers: List<Pair<String, Int>> = emptyList()
    private var genres: List<Pair<String, Int>> = emptyList()
    private var filterParseFailed = false

    override fun getFilterList(): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            Filter.Header("Doesn't work with text search"),
            SortFilter(),
            YearFilter(),
        )
        if (publishers.isNotEmpty()) {
            filters.add(
                PublisherFilter(publishers),
            )
        }
        if (genres.isNotEmpty()) {
            filters.add(
                GenreFilter(genres),
            )
        }
        if (filters.size < 5) {
            filters.add(
                Filter.Header(
                    if (filterParseFailed) {
                        "Unable to load more filters"
                    } else {
                        "Press 'reset' to load more filters"
                    },
                ),
            )
        }

        return FilterList(filters)
    }

    private fun parseFilters(documented: Document) {
        val script = documented.selectFirst("script:containsData(__XFILTER__)")

        if (script == null) {
            filterParseFailed = true
            return
        }

        val data = try {
            script.data()
                .substringAfter("=")
                .trim()
                .removeSuffix(";")
                .parseAs<XFilters>()
        } catch (e: SerializationException) {
            Log.e(name, "filters", e)
            filterParseFailed = true
            return
        }

        publishers = data.filterItems.publisher.values.map { it.value to it.id }
        genres = data.filterItems.genre.values.map { it.value to it.id }
        filterParseFailed = false

        return
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (response.request.url.pathSegments[0] != "search") {
            parseFilters(document)
        }
        val entries = document.select("#dle-content > .readed").map { element ->
            SManga.create().apply {
                with(element.selectFirst(".readed__title > a")!!) {
                    setUrlWithoutDomain(absUrl("href"))
                    title = ownText()
                }
                thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst("div.pagination__pages")
            ?.children()?.last()?.tagName() == "a"

        return MangasPage(entries, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("header.page__header h1")!!.text()
            thumbnail_url = document.selectFirst("div.page__poster img")?.absUrl("src")
            description = document.selectFirst("div.page__text")?.wholeText()
            author = document.selectFirst(".page__list > li:has(> div:contains(Publisher))")?.ownText()
            status = when (document.selectFirst(".page__list > li:has(> div:contains(release type))")?.ownText()?.trim()) {
                "Ongoing" -> SManga.ONGOING
                "Complete" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val data = document.selectFirst(".page__chapters-list script:containsData(__DATA__)")!!.data()
            .substringAfter("=")
            .trim()
            .removeSuffix(";")
            .parseAs<Chapters>()

        return data.chapters.map { chap ->
            SChapter.create().apply {
                url = "/reader/${data.comicId}/${chap.id}${data.xhash}"
                name = chap.title
                chapter_number = chap.number
                date_upload = try {
                    dateFormat.parse(chap.date)?.time ?: 0
                } catch (_: ParseException) {
                    0
                }
            }
        }
    }

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val data = document.selectFirst("script:containsData(__DATA__)")!!.data()
            .substringAfter("=")
            .trim()
            .removeSuffix(";")
            .parseAs<Images>()

        return data.images.mapIndexed { idx, img ->
            Page(idx, imageUrl = baseUrl + img.trim())
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    private inline fun <reified T> FilterList.get(): T? {
        return filterIsInstance<T>().firstOrNull()
    }

    private inline fun <reified T> String.parseAs(): T {
        return json.decodeFromString(this)
    }
}
