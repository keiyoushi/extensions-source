package eu.kanade.tachiyomi.extension.all.imhentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class IMHentai(override val lang: String, private val imhLang: String) : ParsedHttpSource() {

    override val baseUrl: String = "https://imhentai.xxx"
    override val name: String = "IMHentai"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient
        .newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder()
                .removeAll("Accept-Encoding")
                .build()
            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .addInterceptor(
            fun(chain): Response {
                val response = chain.proceed(chain.request())
                if (!response.headers("Content-Type").toString().contains("text/html")) return response

                val responseContentType = response.body.contentType()
                val responseString = response.body.string()

                if (responseString.contains("Overload... Please use the advanced search")) {
                    response.close()
                    throw IOException("IMHentai search is overloaded try again later")
                }

                return response.newBuilder()
                    .body(responseString.toResponseBody(responseContentType))
                    .build()
            },
        ).build()

    // Popular

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            thumbnail_url = element.selectFirst(".inner_thumb img")?.let {
                it.absUrl(if (it.hasAttr("data-src")) "data-src" else "src")
            }
            with(element.select(".caption a")) {
                url = this.attr("href")
                title = this.text()
            }
        }
    }

    override fun popularMangaNextPageSelector(): String = ".pagination li a:contains(Next):not([tabindex])"

    override fun popularMangaSelector(): String = ".thumbs_container .thumb"

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", getFilterList(SORT_ORDER_POPULAR))

    // Latest

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", getFilterList(SORT_ORDER_LATEST))

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("id:")) {
            val id = query.substringAfter("id:")
            return client.newCall(GET("$baseUrl/gallery/$id/"))
                .asObservableSuccess()
                .map { response ->
                    val manga = mangaDetailsParse(response)
                    manga.url = "/gallery/$id/"
                    MangasPage(listOf(manga), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    private fun toBinary(boolean: Boolean) = if (boolean) "1" else "0"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (filters.any { it is LanguageFilters && it.state.any { it.name == LANGUAGE_SPEECHLESS && it.state } }) { // edge case for language = speechless
            val url = "$baseUrl/language/speechless/".toHttpUrlOrNull()!!.newBuilder()

            if ((if (filters.isEmpty()) getFilterList() else filters).filterIsInstance<SortOrderFilter>()[0].state == 0) {
                url.addPathSegment("popular")
            }
            return GET(url.toString())
        } else {
            val url = "$baseUrl/search".toHttpUrlOrNull()!!.newBuilder()
                .addQueryParameter("key", query)
                .addQueryParameter("page", page.toString())
                .addQueryParameter(getLanguageURIByName(imhLang).uri, toBinary(true)) // main language always enabled

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is LanguageFilters -> {
                        filter.state.forEach {
                            url.addQueryParameter(it.uri, toBinary(it.state))
                        }
                    }
                    is CategoryFilters -> {
                        filter.state.forEach {
                            url.addQueryParameter(it.uri, toBinary(it.state))
                        }
                    }
                    is SortOrderFilter -> {
                        getSortOrderURIs().forEachIndexed { index, pair ->
                            url.addQueryParameter(pair.second, toBinary(filter.state == index))
                        }
                    }
                    else -> {}
                }
            }
            return GET(url.toString())
        }
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    // Details

    private fun Elements.csvText(splitTagSeparator: String = ", "): String {
        return this.joinToString {
            listOf(
                it.ownText(),
                it.select(".split_tag").text()
                    .trim()
                    .removePrefix("| "),
            )
                .filter { s -> !s.isNullOrBlank() }
                .joinToString(splitTagSeparator)
        }
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.right_details > h1")!!.text()

        thumbnail_url = document.selectFirst("div.left_cover img")?.let {
            it.absUrl(if (it.hasAttr("data-src")) "data-src" else "src")
        }

        val mangaInfoElement = document.select(".galleries_info")
        val infoMap = mangaInfoElement.select("li:not(.pages)").associate {
            it.select("span.tags_text").text().removeSuffix(":") to it.select(".tag")
        }

        artist = infoMap["Artists"]?.csvText(" | ")

        author = artist

        genre = infoMap["Tags"]?.csvText()

        status = SManga.COMPLETED

        val pages = mangaInfoElement.select("li.pages").text().substringAfter("Pages: ")
        val altTitle = document.select(".subtitle").text().ifBlank { null }

        description = listOf(
            "Parodies",
            "Characters",
            "Groups",
            "Languages",
            "Category",
        ).map { it to infoMap[it]?.csvText() }
            .let { listOf(Pair("Alternate Title", altTitle)) + it + listOf(Pair("Pages", pages)) }
            .filter { !it.second.isNullOrEmpty() }
            .joinToString("\n\n") { "${it.first}:\n${it.second}" }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(response.request.url.toString().replace("gallery", "view") + "1")
                name = "Chapter"
                chapter_number = 1f
            },
        )
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    override fun chapterListSelector(): String = throw UnsupportedOperationException("Not used")

    // Pages

    private val json: Json by injectLazy()

    override fun pageListParse(document: Document): List<Page> {
        val imageDir = document.select("#image_dir").`val`()
        val galleryId = document.select("#gallery_id").`val`()
        val uId = document.select("#u_id").`val`().toInt()

        val randomServer = when (uId) {
            in 1..274825 -> "m1.imhentai.xxx"
            in 274826..403818 -> "m2.imhentai.xxx"
            in 403819..527143 -> "m3.imhentai.xxx"
            in 527144..632481 -> "m4.imhentai.xxx"
            in 632482..816010 -> "m5.imhentai.xxx"
            in 816011..970098 -> "m6.imhentai.xxx"
            in 970099..1121113 -> "m7.imhentai.xxx"
            else -> "m8.imhentai.xxx"
        }

        val images = json.parseToJsonElement(
            document.selectFirst("script:containsData(var g_th)")!!.data()
                .substringAfter("$.parseJSON('").substringBefore("');").trim(),
        ).jsonObject
        val pages = mutableListOf<Page>()

        for (image in images) {
            val iext = image.value.toString().replace("\"", "").split(",")[0]
            val iextPr = when (iext) {
                "p" -> "png"
                "b" -> "bmp"
                "g" -> "gif"
                else -> "jpg"
            }
            pages.add(Page(image.key.toInt() - 1, "", "https://$randomServer/$imageDir/$galleryId/${image.key}.$iextPr"))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    private class SortOrderFilter(sortOrderURIs: List<Pair<String, String>>, state: Int) :
        Filter.Select<String>("Sort By", sortOrderURIs.map { it.first }.toTypedArray(), state)

    private open class SearchFlagFilter(name: String, val uri: String, state: Boolean = true) : Filter.CheckBox(name, state)
    private class LanguageFilter(name: String, uri: String = name) : SearchFlagFilter(name, uri, false)
    private class LanguageFilters(flags: List<LanguageFilter>) : Filter.Group<LanguageFilter>("Other Languages", flags)
    private class CategoryFilters(flags: List<SearchFlagFilter>) : Filter.Group<SearchFlagFilter>("Categories", flags)

    override fun getFilterList() = getFilterList(SORT_ORDER_DEFAULT)

    private fun getFilterList(sortOrderState: Int) = FilterList(
        SortOrderFilter(getSortOrderURIs(), sortOrderState),
        CategoryFilters(getCategoryURIs()),
        LanguageFilters(getLanguageURIs().filter { it.name != imhLang }), // exclude main lang
        Filter.Header("Speechless language: ignores all filters except \"Popular\" and \"Latest\" in Sorting Filter"),
    )

    private fun getCategoryURIs() = listOf(
        SearchFlagFilter("Manga", "manga"),
        SearchFlagFilter("Doujinshi", "doujinshi"),
        SearchFlagFilter("Western", "western"),
        SearchFlagFilter("Image Set", "imageset"),
        SearchFlagFilter("Artist CG", "artistcg"),
        SearchFlagFilter("Game CG", "gamecg"),
    )

    // update sort order indices in companion object if order is changed
    private fun getSortOrderURIs() = listOf(
        Pair("Popular", "pp"),
        Pair("Latest", "lt"),
        Pair("Downloads", "dl"),
        Pair("Top Rated", "tr"),
    )

    private fun getLanguageURIs() = listOf(
        LanguageFilter(LANGUAGE_ENGLISH, "en"),
        LanguageFilter(LANGUAGE_JAPANESE, "jp"),
        LanguageFilter(LANGUAGE_SPANISH, "es"),
        LanguageFilter(LANGUAGE_FRENCH, "fr"),
        LanguageFilter(LANGUAGE_KOREAN, "kr"),
        LanguageFilter(LANGUAGE_GERMAN, "de"),
        LanguageFilter(LANGUAGE_RUSSIAN, "ru"),
        LanguageFilter(LANGUAGE_SPEECHLESS, ""),
    )

    private fun getLanguageURIByName(name: String): LanguageFilter {
        return getLanguageURIs().first { it.name == name }
    }

    companion object {

        // references to sort order indices
        private const val SORT_ORDER_POPULAR = 0
        private const val SORT_ORDER_LATEST = 1
        private const val SORT_ORDER_DEFAULT = SORT_ORDER_POPULAR

        // references to be used in factory
        const val LANGUAGE_ENGLISH = "English"
        const val LANGUAGE_JAPANESE = "Japanese"
        const val LANGUAGE_SPANISH = "Spanish"
        const val LANGUAGE_FRENCH = "French"
        const val LANGUAGE_KOREAN = "Korean"
        const val LANGUAGE_GERMAN = "German"
        const val LANGUAGE_RUSSIAN = "Russian"
        const val LANGUAGE_SPEECHLESS = "Speechless"
    }
}
