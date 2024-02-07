package eu.kanade.tachiyomi.multisrc.gravureblogger

import android.annotation.SuppressLint
import android.os.Build
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

@SuppressLint("ObsoleteSdkInt")
abstract class GravureBlogger(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = false

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json: Json by injectLazy()

    private val dateFormat by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
    }

    override fun popularMangaRequest(page: Int) = GET(apiUrlBuilder(page).build(), headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = json.decodeFromString<BloggerDto>(response.body.string())

        categories = data.feed.category.map { it.term }

        val manga = data.feed.entry.map { entry ->
            val content = Jsoup.parseBodyFragment(entry.content.t, baseUrl)

            SManga.create().apply {
                setUrlWithoutDomain(entry.link.first { it.rel == "alternate" }.href + "#${entry.published.t}")
                title = entry.title.t
                thumbnail_url = content.selectFirst("img")?.absUrl("src")
                genre = entry.category.joinToString { it.term }
                status = SManga.COMPLETED
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                initialized = true
            }
        }
        val hasNextPage = (data.feed.startIndex.t.toInt() + data.feed.itemsPerPage.t.toInt()) <= data.feed.totalResults.t.toInt()

        return MangasPage(manga, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }
        val searchQuery = buildString {
            filterList.filterIsInstance<LabelFilter>().forEach {
                it.state
                    .filter { f -> f.state }
                    .forEach { f ->
                        append(" label:\"")
                        append(f.name)
                        append("\"")
                    }
            }

            if (query.isNotEmpty()) {
                append(" ")
                append(query)
            }
        }.trim()
        val url = apiUrlBuilder(page)
            .addQueryParameter("q", searchQuery)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}".substringBefore("#")

    override fun mangaDetailsRequest(manga: SManga) = throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val date = manga.url.substringAfter("#")

        return Observable.just(
            listOf(
                SChapter.create().apply {
                    url = manga.url.substringBefore("#")
                    name = "Gallery"
                    date_upload = runCatching {
                        dateFormat.parse(date)!!.time
                    }.getOrDefault(0L)
                },
            ),
        )
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("div.post-body img").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // filter name and list of values
    protected open val labelFilters = mapOf<String, List<String>>()

    class LabelFilter(name: String, labels: List<Label>) : Filter.Group<Label>(name, labels)

    class Label(name: String) : Filter.CheckBox(name)

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()

        labelFilters.forEach { (name, filter) ->
            filters.add(LabelFilter(name, filter.map(::Label)))
        }

        if (categories.isEmpty()) {
            filters.add(0, Filter.Header("Press 'Reset' to show extra filters"))
            filters.add(1, Filter.Separator())
        } else {
            val existing = labelFilters.values.flatten()
            val others = categories
                .filterNot { existing.contains(it) }

            filters.add(LabelFilter("Other", others.map(::Label)))
        }

        return FilterList(filters)
    }

    private var categories = emptyList<String>()

    private fun apiUrlBuilder(page: Int) = baseUrl.toHttpUrl().newBuilder().apply {
        // Blogger indices start from 1
        val startIndex = MAX_RESULTS * (page - 1) + 1

        addPathSegments("feeds/posts/default")
        addQueryParameter("alt", "json")
        addQueryParameter("max-results", MAX_RESULTS.toString())
        addQueryParameter("start-index", startIndex.toString())
    }

    companion object {
        private const val MAX_RESULTS = 25
    }
}
