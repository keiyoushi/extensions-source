package eu.kanade.tachiyomi.extension.all.photos18

import android.app.Application
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.select.Evaluator
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Photos18 : HttpSource(), ConfigurableSource {
    override val name = "Photos18"
    override val lang = "all"
    override val supportsLatest = true

    override val baseUrl = "https://www.photos18.com"

    private val baseUrlWithLang get() = if (useTrad) baseUrl else "$baseUrl/zh-hans"
    private fun String.stripLang() = removePrefix("/zh-hans")

    override val client = network.client.newBuilder().followRedirects(false).build()

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", baseUrl)
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrlWithLang/sort/views?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        parseCategories(document)
        val mangas = document.selectFirst(Evaluator.Id("videos"))!!.children().map {
            val cardBody = it.selectFirst(Evaluator.Class("card-body"))!!
            val link = cardBody.selectFirst(Evaluator.Tag("a"))!!
            SManga.create().apply {
                url = link.attr("href").stripLang()
                title = link.ownText()
                thumbnail_url = baseUrl + it.selectFirst(Evaluator.Tag("img"))!!.attr("data-src")
                genre = cardBody.selectFirst(Evaluator.Tag("label"))!!.ownText()
                status = SManga.COMPLETED
                initialized = true
            }
        }
        val isLastPage = document.selectFirst(Evaluator.Class("next")).run {
            this == null || hasClass("disabled")
        }
        return MangasPage(mangas, !isLastPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrlWithLang/?page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrlWithLang.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())

        for (filter in filters) {
            if (filter is QueryFilter) filter.addQueryTo(url)
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = Observable.just(manga)

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException("Not used.")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url
            name = manga.title
            chapter_number = -2f
        }
        return Observable.just(listOf(chapter))
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException("Not used.")

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.selectFirst(Evaluator.Id("content"))!!.select(Evaluator.Tag("img"))
        return images.mapIndexed { index, image ->
            Page(index, imageUrl = image.attr("data-src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used.")

    override fun getFilterList() = FilterList(
        SortFilter(),
        if (categories.isEmpty()) {
            Filter.Header("Tap 'Reset' to load categories")
        } else {
            CategoryFilter(categories)
        },
    )

    private open class QueryFilter(
        name: String,
        values: Array<String>,
        private val queryName: String,
        private val queryValues: Array<String>,
        state: Int = 0,
    ) : Filter.Select<String>(name, values, state) {
        fun addQueryTo(builder: HttpUrl.Builder) =
            builder.addQueryParameter(queryName, queryValues[state])
    }

    private class SortFilter : QueryFilter(
        "Sort by",
        arrayOf("Latest", "Popular", "Trend", "Recommended", "Best"),
        "sort",
        arrayOf("created", "hits", "views", "score", "likes"),
        state = 2,
    )

    private class CategoryFilter(categories: List<Pair<String, String>>) : QueryFilter(
        "Category",
        categories.map { it.first }.toTypedArray(),
        "category_id",
        categories.map { it.second }.toTypedArray(),
    )

    private var categories: List<Pair<String, String>> = emptyList()

    private fun parseCategories(document: Document) {
        if (categories.isNotEmpty()) return
        val items = document.selectFirst(Evaluator.Id("w3"))!!.children()
        categories = buildList(items.size + 1) {
            add(Pair("All", ""))
            items.mapTo(this) {
                val value = it.text().substringBefore(" (")
                val queryValue = it.selectFirst(Evaluator.Tag("a"))!!.attr("href").substringAfterLast('/')
                Pair(value, queryValue)
            }
        }
    }

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)!!
    }

    private val useTrad get() = preferences.getBoolean("ZH_HANT", false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = "ZH_HANT"
            title = "Use Traditional Chinese"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }
}
