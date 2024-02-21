package eu.kanade.tachiyomi.extension.zh.cartoon18

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.select.Evaluator
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLDecoder

@Suppress("unused")
class Cartoon18 : HttpSource(), ConfigurableSource {
    override val name = "Cartoon18"
    override val lang = "zh"
    override val supportsLatest = true

    override val baseUrl = "https://www.cartoon18.com"

    private val baseUrlWithLang get() = if (useTrad) baseUrl else "$baseUrl/zh-hans"

    override val client = network.client.newBuilder().followRedirects(false).build()

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", baseUrl)
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrlWithLang?sort=hits&page=$page".toHttpUrl(), headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.selectFirst(Evaluator.Id("videos"))!!.children().map {
            val cardBody = it.selectFirst(Evaluator.Class("card-body"))!!
            val link = cardBody.selectFirst(Evaluator.Tag("a"))!!
            val genres = cardBody.select("div a.badge")
            SManga.create().apply {
                url = link.attr("href")
                title = link.ownText()
                thumbnail_url = it.selectFirst(Evaluator.Tag("img"))!!.attr("data-src")
                genre = genres.joinToString { elm -> elm.text() }
            }
        }
        val isLastPage = document.selectFirst("nav .pagination .next").run {
            this == null || hasClass("disabled")
        }
        return MangasPage(mangas, !isLastPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrlWithLang?sort=created&page=$page".toHttpUrl(), headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrlWithLang.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addQueryParameter("q", query.trim())
        }
        url.addQueryParameter("page", page.toString())

        filters.forEach {
            when (it) {
                is KeywordFilter -> if (query.isBlank()) it.addQueryTo(url)
                is QueryFilter -> it.addQueryTo(url)
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val genres = document.select("div.content h1.title ~ div.row div.my-2:has(i.fa-tag) span:has(a)")
        val authors = document.select("div.content h1.title ~ div.row div.my-2:has(i.fa-user) span")
        val descriptions = document.select("div.content h1.title ~ div.row div.my-2:has(i.fa-list) span")
        return SManga.create().apply {
            title = document.selectFirst("div.content h1.title")!!.ownText()
            thumbnail_url = document.selectFirst("div.content h1.title ~ div.row a img")!!.attr("src")
            genre = genres.text()
            if (authors.size > 1) {
                author = authors[1].text().replace(",(\\S)".toRegex(), ", $1")
            }
            if (descriptions.size > 1) {
                description = descriptions[1].text()
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select("div.content h1.title + div a")
        return chapters.map {
            SChapter.create().apply {
                url = it.attr("href")
                name = it.text()
            }
        }.reversed()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select("div#lightgallery a img")
        return images.mapIndexed { index, image ->
            Page(index, imageUrl = image.attr("src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        launchIO { fetchKeywords() }
        return FilterList(
            SortFilter(),
            if (keywordsList.isEmpty()) {
                Filter.Header("Tap 'Reset' to load keywords")
            } else {
                KeywordFilter(keywordsList)
            },
        )
    }

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
        arrayOf("Latest", "Popular", "Recommended", "Best"),
        "sort",
        arrayOf("created", "hits", "score", "likes"),
        state = 2,
    )

    class Keyword(val name: String, val value: String)

    private var keywordsList: List<Keyword> = emptyList()

    private class KeywordFilter(keywords: List<Keyword>) : QueryFilter(
        "Keyword",
        keywords.map { it.name }.toTypedArray(),
        "q",
        keywords.map { it.value }.toTypedArray(),
    )

    /**
     * Inner variable to control how much tries the keywords request was called.
     */
    private var fetchKeywordsAttempts: Int = 0

    /**
     * Fetch the keywords from the source to be used in the filters.
     */
    private fun fetchKeywords() {
        if (fetchKeywordsAttempts < 3 && keywordsList.isEmpty()) {
            try {
                keywordsList = client.newCall(keywordsRequest()).execute()
                    .use { parseKeywords(it.asJsoup()) }
            } catch (_: Exception) {
            } finally {
                fetchKeywordsAttempts++
            }
        }
    }

    /**
     * The request to the search page (or another one) that have the keywords list.
     */
    private fun keywordsRequest(): Request {
        return GET("$baseUrlWithLang/category".toHttpUrl(), headers)
    }

    /**
     * Get the keywords from the search page document.
     *
     * @param document The search page document
     */
    private fun parseKeywords(document: Document): List<Keyword> {
        val items = document.select("div.content a.btn")
        return buildList(items.size + 1) {
            add(Keyword("None", ""))
            items.mapTo(this) {
                val value = it.text()
                val queryValue = URLDecoder.decode(it.attr("href").substringAfterLast('/'), "UTF-8")
                Keyword(value, queryValue)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun launchIO(block: () -> Unit) = scope.launch { block() }

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
