package eu.kanade.tachiyomi.extension.en.hentai2read

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar
import java.util.regex.Pattern

class Hentai2Read : ParsedHttpSource() {

    override val name = "Hentai2Read"

    override val baseUrl = "https://hentai2read.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        const val imageBaseUrl = "https://static.hentaicdn.com/hentai"

        const val PREFIX_ID_SEARCH = "id:"

        val pagesUrlPattern by lazy {
            Pattern.compile("""'images' : \[\"(.*?)[,]?\"\]""")
        }

        lateinit var nextSearchPage: String
    }

    override fun popularMangaSelector() = "div.book-grid-item"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/hentai-list/all/any/all/most-popular/$page/", headers)

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/hentai-list/all/any/all/last-updated/$page/", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            thumbnail_url = element.select("img").attr("abs:src")
            element.select("div.overlay-title a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a#js-linkNext"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(GET("$baseUrl/$id/", headers)).asObservableSuccess()
                .map { MangasPage(listOf(mangaDetailsParse(it).apply { url = "/$id/" }), false) }
        } else {
            val search = requestSearch(page, query, filters)
            client.newCall(search.first).asObservableSuccess()
                .map { parseSearch(it, page, search.second) }
        }
    }

    private fun requestSearch(page: Int, query: String, filters: FilterList): Pair<Request, String?> {
        val searchUrl = "$baseUrl/hentai-list/advanced-search"
        var sortOrder: String? = null

        return if (page == 1) {
            val form = FormBody.Builder().apply {
                add("cmd_wpm_wgt_mng_sch_sbm", "Search")
                add("txt_wpm_wgt_mng_sch_nme", "")
                add("cmd_wpm_pag_mng_sch_sbm", "")
                add("txt_wpm_pag_mng_sch_nme", query)

                for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                    when (filter) {
                        is MangaNameSelect -> add("cbo_wpm_pag_mng_sch_nme", filter.state.toString())
                        is ArtistName -> add("txt_wpm_pag_mng_sch_ats", filter.state)
                        is ArtistNameSelect -> add("cbo_wpm_pag_mng_sch_ats", filter.state.toString())
                        is CharacterName -> add("txt_wpm_pag_mng_sch_chr", filter.state)
                        is CharacterNameSelect -> add("cbo_wpm_pag_mng_sch_chr", filter.state.toString())
                        is ReleaseYear -> add("txt_wpm_pag_mng_sch_rls_yer", filter.state)
                        is ReleaseYearSelect -> add("cbo_wpm_pag_mng_sch_rls_yer", filter.state.toString())
                        is Status -> add("rad_wpm_pag_mng_sch_sts", filter.state.toString())
                        is TagSearchMode -> add("rad_wpm_pag_mng_sch_tag_mde", arrayOf("and", "or").getOrElse(filter.state) { "and" })
                        is TagList -> filter.state.forEach { tag ->
                            when (tag.state) {
                                Filter.TriState.STATE_INCLUDE -> add("chk_wpm_pag_mng_sch_mng_tag_inc[]", tag.id.toString())
                                Filter.TriState.STATE_EXCLUDE -> add("chk_wpm_pag_mng_sch_mng_tag_exc[]", tag.id.toString())
                            }
                        }
                        is SortOrder -> sortOrder = filter.toUriPart()
                        else -> {}
                    }
                }
            }
            Pair(POST(searchUrl, headers, form.build()), sortOrder)
        } else {
            Pair(GET(nextSearchPage, headers), sortOrder)
        }
    }

    // If the user wants to search by a sort order other than alphabetical, we have to make another call
    private fun parseSearch(response: Response, page: Int, sortOrder: String?): MangasPage {
        val document = if (page == 1 && sortOrder != null) {
            response.asJsoup().select("li.dropdown li:contains($sortOrder) a").first()!!.attr("abs:href")
                .let { client.newCall(GET(it, headers)).execute().asJsoup() }
        } else {
            response.asJsoup()
        }

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = document.select(searchMangaNextPageSelector()).firstOrNull()?.let {
            nextSearchPage = it.attr("abs:href")
            true
        } ?: false

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("ul.list-simple-mini").first()!!

        val manga = SManga.create()
        manga.author = infoElement.select("li:contains(Author) > a").text()
        manga.artist = infoElement.select("li:contains(Artist) > a").text()
        manga.genre = infoElement.select("li:contains(Category) > a, li:contains(Content) > a").joinToString(", ") { it.text() }
        manga.description = buildDescription(infoElement)
        manga.status = infoElement.select("li:contains(Status) > a").text().orEmpty().let { parseStatus(it) }
        manga.thumbnail_url = document.select("a#js-linkNext img").attr("src")
        manga.title = document.select("h3.block-title > a").first()!!.ownText().trim()
        return manga
    }

    private fun buildDescription(infoElement: Element): String {
        val topDescriptions = listOf(
            Pair(
                "Alternative Title",
                infoElement.select("li").first()!!.text().let {
                    if (it.trim() == "-") {
                        emptyList()
                    } else {
                        it.split(", ")
                    }
                },
            ),
            Pair(
                "Storyline",
                listOf(infoElement.select("li:contains(Storyline) > p").text()),
            ),
        )

        val descriptions = listOf(
            "Parody",
            "Page",
            "Character",
            "Language",
        ).map { it to infoElement.select("li:contains($it) a").map { v -> v.text() } }
            .let { topDescriptions + it } // start with topDescriptions
            .filter { !it.second.isEmpty() && it.second[0] != "-" }
            .map { "${it.first}:\n${it.second.joinToString()}" }

        return descriptions.joinToString("\n\n")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.nav-chapters > li > div.media > a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            val time = element.select("div > small").text().substringAfter("about").substringBefore("ago")
            name = element.ownText().trim()
            if (time != "") {
                date_upload = parseChapterDate(time)
            }
        }
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.replace(Regex("[^\\d]"), "").toInt()

        return when {
            "second" in date -> Calendar.getInstance().apply {
                add(Calendar.SECOND, -value)
            }.timeInMillis
            "minute" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, -value)
            }.timeInMillis
            "hour" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, -value)
            }.timeInMillis
            "day" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, -value)
            }.timeInMillis
            "week" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, -value * 7)
            }.timeInMillis
            "month" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, -value)
            }.timeInMillis
            "year" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, -value)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val pages = mutableListOf<Page>()
        val m = pagesUrlPattern.matcher(response.body.string())
        var i = 0
        while (m.find()) {
            m.group(1)?.split(",")?.forEach {
                pages.add(Page(i++, "", imageBaseUrl + it.trim('"').replace("""\/""", "/")))
            }
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()


    override fun getFilterList() = FilterList(
        SortOrder(getSortOrder()),
        MangaNameSelect(),
        Filter.Separator(),
        ArtistName(),
        ArtistNameSelect(),
        Filter.Separator(),
        CharacterName(),
        CharacterNameSelect(),
        Filter.Separator(),
        ReleaseYear(),
        ReleaseYearSelect(),
        Filter.Separator(),
        Status(),
        Filter.Separator(),
        TagSearchMode(),
        Filter.Separator(),
        TagList("Categories", getCategoryList()),
        Filter.Separator(),
        TagList("Tags", getTagList()),
        Filter.Separator(),
        TagList("Doujins", getDoujinList()),
    )

}
