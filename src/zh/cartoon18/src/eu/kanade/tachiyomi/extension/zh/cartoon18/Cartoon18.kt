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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLDecoder

class Cartoon18 : HttpSource(), ConfigurableSource {
    override val name = "Cartoon18"
    override val lang = "zh"
    override val supportsLatest = true

    override val baseUrl = "https://www.cartoon18.com"

    private val baseUrlWithLang get() = if (useTrad) baseUrl else "$baseUrl/zh-hans"

    override val client = network.client.newBuilder().followRedirects(false).build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrlWithLang?sort=hits&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#videos div.card").map { card ->
            val cardBody = card.select(".card-body")
            val link = cardBody.select("a")
            val genres = cardBody.select("div a.badge")
            SManga.create().apply {
                url = link.attr("href")
                title = link.text()
                thumbnail_url = card.select("img").attr("data-src")
                genre = genres.joinToString { elm -> elm.text() }
            }
        }
        val isLastPage = document.selectFirst("nav .pagination .next").run {
            this == null || hasClass("disabled")
        }
        return MangasPage(mangas, !isLastPage)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrlWithLang?sort=created&page=$page", headers)

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
        val images = document.select("div#app > div > a img")
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
                keywordsList = client.newCall(GET("$baseUrlWithLang/category", headers)).execute()
                    .use { response ->
                        val document = response.asJsoup()
                        val items = document.select("div.content a.btn")
                        buildList(items.size + 1) {
                            add(Keyword("None", ""))
                            items.mapTo(this) { keyword ->
                                val queryValue = URLDecoder.decode(
                                    keyword.attr("href")
                                        .substringAfterLast('/'),
                                    "UTF-8",
                                )
                                Keyword(keyword.text(), queryValue)
                            }
                        }
                    }
            } catch (_: Exception) {
            } finally {
                fetchKeywordsAttempts++
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private fun launchIO(block: () -> Unit) = scope.launch { block() }

    private val preferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)!!

    private val useTrad get() = preferences.getBoolean("ZH_HANT", false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = "ZH_HANT"
            title = "Use Traditional Chinese"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }
}
