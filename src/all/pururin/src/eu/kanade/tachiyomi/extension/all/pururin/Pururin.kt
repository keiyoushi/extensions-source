package eu.kanade.tachiyomi.extension.all.pururin

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy

abstract class Pururin(
    override val lang: String = "all",
    private val searchLang: Pair<String, String>? = null,
    private val langPath: String = "",
) : ParsedHttpSource() {
    override val name = "Pururin"

    final override val baseUrl = "https://pururin.me"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val json: Json by injectLazy()

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/browse$langPath?sort=most-popular&page=$page", headers)
    }

    override fun popularMangaSelector(): String = "a.card"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.attr("title")
            setUrlWithoutDomain(element.attr("abs:href"))
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector(): String = ".page-item [rel=next]"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/browse$langPath?page=$page", headers)
    }

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    // Search

    private fun List<Pair<String, String>>.toValue(): String {
        return "[${this.joinToString(",") { "{\"id\":${it.first},\"name\":\"${it.second}\"}" }}]"
    }

    private fun parsePageRange(query: String, minPages: Int = 1, maxPages: Int = 9999): Pair<Int, Int> {
        val num = query.filter(Char::isDigit).toIntOrNull() ?: -1
        fun limitedNum(number: Int = num): Int = number.coerceIn(minPages, maxPages)

        if (num < 0) return minPages to maxPages
        return when (query.firstOrNull()) {
            '<' -> 1 to if (query[1] == '=') limitedNum() else limitedNum(num + 1)
            '>' -> limitedNum(if (query[1] == '=') num else num + 1) to maxPages
            '=' -> when (query[1]) {
                '>' -> limitedNum() to maxPages
                '<' -> 1 to limitedNum(maxPages)
                else -> limitedNum() to limitedNum()
            }
            else -> limitedNum() to limitedNum()
        }
    }

    @Serializable
    class Tag(
        val id: Int,
        val name: String,
    )

    private fun findTagByNameSubstring(tags: List<Tag>, substring: String): Pair<String, String>? {
        val tag = tags.find { it.name.contains(substring, ignoreCase = true) }
        return tag?.let { Pair(tag.id.toString(), tag.name) }
    }

    private fun tagSearch(tag: String, type: String): Pair<String, String>? {
        val requestBody = FormBody.Builder()
            .add("text", tag)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/api/get/tags/search")
            .headers(headers)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        return findTagByNameSubstring(response.parseAs<List<Tag>>(), type)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val includeTags = mutableListOf<Pair<String, String>>()
        val excludeTags = mutableListOf<Pair<String, String>>()
        var pagesMin = 1
        var pagesMax = 9999
        var sortBy = "newest"

        if (searchLang != null) includeTags.add(searchLang)

        filters.forEach {
            when (it) {
                is SelectFilter -> sortBy = it.getValue()

                is TypeFilter -> {
                    val (_, inactiveFilters) = it.state.partition { stIt -> stIt.state }
                    excludeTags += inactiveFilters.map { fil -> Pair(fil.value, "${fil.name} [Category]") }
                }

                is PageFilter -> {
                    if (it.state.isNotEmpty()) {
                        val (min, max) = parsePageRange(it.state)
                        pagesMin = min
                        pagesMax = max
                    }
                }

                is TextFilter -> {
                    if (it.state.isNotEmpty()) {
                        it.state.split(",").filter(String::isNotBlank).map { tag ->
                            val trimmed = tag.trim()
                            if (trimmed.startsWith('-')) {
                                tagSearch(trimmed.lowercase().removePrefix("-"), it.type)?.let { tagInfo ->
                                    excludeTags.add(tagInfo)
                                }
                            } else {
                                tagSearch(trimmed.lowercase(), it.type)?.let { tagInfo ->
                                    includeTags.add(tagInfo)
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        // Searching with just one tag usually gives wrong results
        if (query.isEmpty()) {
            when {
                excludeTags.size == 1 && includeTags.isEmpty() -> excludeTags.addAll(excludeTags)
                includeTags.size == 1 && excludeTags.isEmpty() -> {
                    val url = baseUrl.toHttpUrl().newBuilder().apply {
                        addPathSegment("browse")
                        addPathSegment("tags")
                        addPathSegment("content")
                        addPathSegment(includeTags[0].first)
                        addQueryParameter("sort", sortBy)
                        addQueryParameter("start_page", pagesMin.toString())
                        addQueryParameter("last_page", pagesMax.toString())
                        if (page > 1) addQueryParameter("page", page.toString())
                    }.build()
                    return GET(url, headers)
                }
            }
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("q", query)
            addQueryParameter("sort", sortBy)
            addQueryParameter("start_page", pagesMin.toString())
            addQueryParameter("last_page", pagesMax.toString())
            if (includeTags.isNotEmpty()) addQueryParameter("included_tags", includeTags.toValue())
            if (excludeTags.isNotEmpty()) addQueryParameter("excluded_tags", excludeTags.toValue())
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select(".box-gallery").let { e ->
                initialized = true
                title = e.select(".title").text()
                author = e.select("a[href*=/circle/]").eachText().joinToString().ifEmpty { e.select("[itemprop=author]").text() }
                artist = e.select("[itemprop=author]").eachText().joinToString()
                genre = e.select("a[href*=/content/]").eachText().joinToString()
                description = e.select(".box-gallery .table-info tr")
                    .filter { tr ->
                        tr.select("td").let { td ->
                            td.isNotEmpty() &&
                                td.none { it.text().contains("content", ignoreCase = true) || it.text().contains("ratings", ignoreCase = true) }
                        }
                    }
                    .joinToString("\n") { tr ->
                        tr.select("td").let { td ->
                            var a = td.select("a").toList()
                            if (a.isEmpty()) a = td.drop(1)
                            td.first()!!.text() + ": " + a.joinToString { it.text() }
                        }
                    }
                status = SManga.COMPLETED
                thumbnail_url = e.select("img").attr("abs:src")
            }
        }
    }

    // Chapters

    override fun chapterListSelector(): String = ".table-collection tbody tr a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.text()
            setUrlWithoutDomain(element.attr("abs:href"))
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().select(chapterListSelector())
            .map { chapterFromElement(it) }
            .reversed()
            .let { list ->
                list.ifEmpty {
                    listOf(
                        SChapter.create().apply {
                            setUrlWithoutDomain(response.request.url.toString())
                            name = "Chapter"
                        },
                    )
                }
            }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".gallery-preview a img")
            .mapIndexed { i, img ->
                Page(i, "", (if (img.hasAttr("abs:src")) img.attr("abs:src") else img.attr("abs:data-src")).replace("t.", "."))
            }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }
    override fun getFilterList() = getFilters()
}
