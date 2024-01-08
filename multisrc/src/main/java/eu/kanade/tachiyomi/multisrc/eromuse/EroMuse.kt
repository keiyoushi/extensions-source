package eu.kanade.tachiyomi.multisrc.eromuse

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

@ExperimentalStdlibApi
open class EroMuse(override val name: String, override val baseUrl: String) : HttpSource() {

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    /**
     * Browse, search, and latest all run through an ArrayDeque of requests that acts as a stack we push and pop to/from
     * For the fetch functions, we only need to worry about pushing the first page to the stack because subsequent pages
     * get pushed to the stack during parseManga(). Page 1's URL must include page=1 if the next page would be page=2,
     * if page 2 is path_to/2, nothing special needs to be done.
     */

    // the stack - shouldn't need to touch these except for visibility
    protected data class StackItem(val url: String, val pageType: Int)
    private lateinit var stackItem: StackItem
    protected val pageStack = ArrayDeque<StackItem>()
    companion object {
        const val VARIOUS_AUTHORS = 0
        const val AUTHOR = 1
        const val SEARCH_RESULTS_OR_BASE = 2
    }
    protected lateinit var currentSortingMode: String

    private val albums = getAlbumList()

    // might need to override for new sources
    private val nextPageSelector = ".pagination span.current + span a"
    protected open val albumSelector = "a.c-tile:has(img):not(:has(.members-only))"
    protected open val topLevelPathSegment = "comics/album"
    private val pageQueryRegex = Regex("""page=\d+""")

    private fun Document.nextPageOrNull(): String? {
        val url = this.location()
        return this.select(nextPageSelector).firstOrNull()?.text()?.toIntOrNull()?.let { int ->
            if (url.contains(pageQueryRegex)) {
                url.replace(pageQueryRegex, "page=$int")
            } else {
                val httpUrl = url.toHttpUrlOrNull()!!
                val builder = if (httpUrl.pathSegments.last().toIntOrNull() is Int) {
                    httpUrl.newBuilder().removePathSegment(httpUrl.pathSegments.lastIndex)
                } else {
                    httpUrl.newBuilder()
                }
                builder.addPathSegment(int.toString()).toString()
            }
        }
    }

    private fun Document.addNextPageToStack() {
        this.nextPageOrNull()?.let { pageStack.add(StackItem(it, stackItem.pageType)) }
    }

    protected fun Element.imgAttr(): String = if (this.hasAttr("data-src")) this.attr("abs:data-src") else this.attr("abs:src")

    private fun mangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            title = element.text()
            thumbnail_url = element.select("img").firstOrNull()?.imgAttr()
        }
    }

    protected fun getAlbumType(url: String, default: Int = AUTHOR): Int {
        return albums.filter { it.third != SEARCH_RESULTS_OR_BASE && url.contains(it.second, true) }
            .getOrElse(0) { Triple(null, null, default) }.third
    }

    protected fun parseManga(document: Document): MangasPage {
        fun internalParse(internalDocument: Document): List<SManga> {
            val authorDocument = if (stackItem.pageType == VARIOUS_AUTHORS) {
                internalDocument.select(albumSelector).let {
                        elements ->
                    elements.reversed().map { pageStack.addLast(StackItem(it.attr("abs:href"), AUTHOR)) }
                }
                client.newCall(stackRequest()).execute().asJsoup()
            } else {
                internalDocument
            }
            authorDocument.addNextPageToStack()
            return authorDocument.select(albumSelector).map { mangaFromElement(it) }
        }

        if (stackItem.pageType in listOf(VARIOUS_AUTHORS, SEARCH_RESULTS_OR_BASE)) document.addNextPageToStack()
        val mangas = when (stackItem.pageType) {
            VARIOUS_AUTHORS -> {
                document.select(albumSelector).let {
                        elements ->
                    elements.reversed().map { pageStack.addLast(StackItem(it.attr("abs:href"), AUTHOR)) }
                }
                internalParse(document)
            }
            AUTHOR -> {
                internalParse(document)
            }
            SEARCH_RESULTS_OR_BASE -> {
                val searchMangas = mutableListOf<SManga>()
                document.select(albumSelector)
                    .map { element ->
                        val url = element.attr("abs:href")
                        val depth = url.removePrefix("$baseUrl/$topLevelPathSegment/").split("/").count()

                        when (getAlbumType(url)) {
                            VARIOUS_AUTHORS -> {
                                when (depth) {
                                    1 -> { // eg. /comics/album/Fakku-Comics
                                        pageStack.addLast(StackItem(url, VARIOUS_AUTHORS))
                                        if (searchMangas.isEmpty()) searchMangas += internalParse(client.newCall(stackRequest()).execute().asJsoup()) else null
                                    }
                                    2 -> { // eg. /comics/album/Fakku-Comics/Bosshi
                                        pageStack.addLast(StackItem(url, AUTHOR))
                                        if (searchMangas.isEmpty()) searchMangas += internalParse(client.newCall(stackRequest()).execute().asJsoup()) else null
                                    }
                                    else -> {
                                        // eg. 3 -> /comics/album/Fakku-Comics/Bosshi/After-Summer-After
                                        // eg. 5 -> /comics/album/Various-Authors/Firollian/Reward/Reward-22/ElfAlfie
                                        // eg. 6 -> /comics/album/Various-Authors/Firollian/Area69/Area69-no_1/SamusAran/001_Dialogue
                                        searchMangas.add(mangaFromElement(element))
                                    }
                                }
                            }
                            AUTHOR -> {
                                if (depth == 1) { // eg. /comics/album/ShadBase-Comics
                                    pageStack.addLast(StackItem(url, AUTHOR))
                                    if (searchMangas.isEmpty()) searchMangas += internalParse(client.newCall(stackRequest()).execute().asJsoup()) else null
                                } else {
                                    // eg. 2 -> /comics/album/ShadBase-Comics/RickMorty
                                    // eg. 3 -> /comics/album/Incase-Comics/Comic/Alfie
                                    searchMangas.add(mangaFromElement(element))
                                }
                            }
                            else -> null // SEARCH_RESULTS_OR_BASE shouldn't be a case
                        }
                    }
                searchMangas
            }
            else -> emptyList()
        }
        return MangasPage(mangas, pageStack.isNotEmpty())
    }

    protected fun stackRequest(): Request {
        stackItem = pageStack.removeLast()
        val url = if (stackItem.pageType == AUTHOR && currentSortingMode.isNotEmpty() && !stackItem.url.contains("sort")) {
            stackItem.url.toHttpUrlOrNull()!!.newBuilder().addQueryParameter("sort", currentSortingMode).toString()
        } else {
            stackItem.url
        }
        return GET(url, headers)
    }

    // Popular

    protected fun fetchManga(url: String, page: Int, sortingMode: String): Observable<MangasPage> {
        if (page == 1) {
            pageStack.clear()
            pageStack.addLast(StackItem(url, VARIOUS_AUTHORS))
            currentSortingMode = sortingMode
        }

        return client.newCall(stackRequest())
            .asObservableSuccess()
            .map { response -> parseManga(response.asJsoup()) }
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = fetchManga("$baseUrl/comics/album/Various-Authors", page, "")

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException("Not used")
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Latest

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = fetchManga("$baseUrl/comics/album/Various-Authors?sort=date", page, "date")

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (page == 1) {
            pageStack.clear()

            val filterList = if (filters.isEmpty()) getFilterList() else filters
            currentSortingMode = filterList.filterIsInstance<SortFilter>().first().toQueryValue()

            if (query.isNotBlank()) {
                val url = "$baseUrl/search?q=$query".toHttpUrlOrNull()!!.newBuilder().apply {
                    if (currentSortingMode.isNotEmpty()) addQueryParameter("sort", currentSortingMode)
                    addQueryParameter("page", "1")
                }
                pageStack.addLast(StackItem(url.toString(), SEARCH_RESULTS_OR_BASE))
            } else {
                val albumFilter = filterList.filterIsInstance<AlbumFilter>().first().selection()
                val url = "$baseUrl/comics/${albumFilter.pathSegments}".toHttpUrlOrNull()!!.newBuilder().apply {
                    if (currentSortingMode.isNotEmpty()) addQueryParameter("sort", currentSortingMode)
                    if (albumFilter.pageType != AUTHOR) addQueryParameter("page", "1")
                }
                pageStack.addLast(StackItem(url.toString(), albumFilter.pageType))
            }
        }

        return client.newCall(stackRequest())
            .asObservableSuccess()
            .map { response -> parseManga(response.asJsoup()) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        return SManga.create().apply {
            with(response.asJsoup()) {
                setUrlWithoutDomain(response.request.url.toString())
                thumbnail_url = select("$albumSelector img").firstOrNull()?.imgAttr()
                author = when (getAlbumType(url)) {
                    AUTHOR -> {
                        // eg. https://comics.8muses.com/comics/album/ShadBase-Comics/RickMorty
                        // eg. https://comics.8muses.com/comics/album/Incase-Comics/Comic/Alfie
                        select("div.top-menu-breadcrumb li:nth-child(2)").text()
                    }
                    VARIOUS_AUTHORS -> {
                        // eg. https://comics.8muses.com/comics/album/Various-Authors/NLT-Media/A-Sunday-Schooling
                        select("div.top-menu-breadcrumb li:nth-child(3)").text()
                    }
                    else -> null
                }
            }
        }
    }

    // Chapters

    protected open val linkedChapterSelector = "a.c-tile:has(img)[href*=/comics/album/]"
    protected open val pageThumbnailSelector = "a.c-tile:has(img)[href*=/comics/picture/] img"

    override fun chapterListParse(response: Response): List<SChapter> {
        fun parseChapters(document: Document, isFirstPage: Boolean, chapters: ArrayDeque<SChapter>): List<SChapter> {
            // Linked chapters
            document.select(linkedChapterSelector)
                .mapNotNull {
                    chapters.addFirst(
                        SChapter.create().apply {
                            name = it.text()
                            setUrlWithoutDomain(it.attr("href"))
                        },
                    )
                }

            if (isFirstPage) {
                // Self
                document.select(pageThumbnailSelector).firstOrNull()?.let {
                    chapters.add(
                        SChapter.create().apply {
                            name = "Chapter"
                            setUrlWithoutDomain(response.request.url.toString())
                        },
                    )
                }
            }

            document.nextPageOrNull()?.let { url -> parseChapters(client.newCall(GET(url, headers)).execute().asJsoup(), false, chapters) }
            return chapters
        }

        return parseChapters(response.asJsoup(), true, ArrayDeque())
    }

    // Pages

    protected open val pageThumbnailPathSegment = "/th/"
    protected open val pageFullSizePathSegment = "/fl/"

    override fun pageListParse(response: Response): List<Page> {
        fun parsePages(
            document: Document,
            nestedChapterDocuments: ArrayDeque<Document> = ArrayDeque(),
            pages: ArrayList<Page> = ArrayList(),
        ): List<Page> {
            // Nested chapters aka folders
            document.select(linkedChapterSelector)
                .mapNotNull {
                    nestedChapterDocuments.add(
                        client.newCall(GET(it.attr("abs:href"), headers)).execute().asJsoup(),
                    )
                }

            var lastPage: Int = pages.size
            pages.addAll(
                document.select(pageThumbnailSelector).mapIndexed { i, img ->
                    Page(lastPage + i, "", img.imgAttr().replace(pageThumbnailPathSegment, pageFullSizePathSegment))
                },
            )

            document.nextPageOrNull()?.let {
                    url ->
                pages.addAll(parsePages(client.newCall(GET(url, headers)).execute().asJsoup(), nestedChapterDocuments, pages))
            }

            while (!nestedChapterDocuments.isEmpty()) {
                pages.addAll(parsePages(nestedChapterDocuments.removeFirst()))
            }

            return pages
        }

        return parsePages(response.asJsoup())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("Text search only combines with sort!"),
            Filter.Separator(),
            AlbumFilter(getAlbumList()),
            SortFilter(getSortList()),
        )
    }

    protected class AlbumFilter(private val vals: Array<Triple<String, String, Int>>) : Filter.Select<String>("Album", vals.map { it.first }.toTypedArray()) {
        fun selection() = AlbumFilterData(vals[state].second, vals[state].third)
        data class AlbumFilterData(val pathSegments: String, val pageType: Int)
    }
    protected open fun getAlbumList() = arrayOf(
        Triple("All Authors", "", SEARCH_RESULTS_OR_BASE),
        Triple("Various Authors", "album/Various-Authors", VARIOUS_AUTHORS),
        Triple("Fakku Comics", "album/Fakku-Comics", VARIOUS_AUTHORS),
        Triple("Hentai and Manga English", "album/Hentai-and-Manga-English", VARIOUS_AUTHORS),
        Triple("Fake Celebrities Sex Pictures", "album/Fake-Celebrities-Sex-Pictures", AUTHOR),
        Triple("MilfToon Comics", "album/MilfToon-Comics", AUTHOR),
        Triple("BE Story Club Comics", "album/BE-Story-Club-Comics", AUTHOR),
        Triple("ShadBase Comics", "album/ShadBase-Comics", AUTHOR),
        Triple("ZZZ Comics", "album/ZZZ-Comics", AUTHOR),
        Triple("PalComix Comics", "album/PalComix-Comics", AUTHOR),
        Triple("MCC Comics", "album/MCC-Comics", AUTHOR),
        Triple("Expansionfan Comics", "album/Expansionfan-Comics", AUTHOR),
        Triple("JAB Comics", "album/JAB-Comics", AUTHOR),
        Triple("Giantess Fan Comics", "album/Giantess-Fan-Comics", AUTHOR),
        Triple("Renderotica Comics", "album/Renderotica-Comics", AUTHOR),
        Triple("IllustratedInterracial.com Comics", "album/IllustratedInterracial_com-Comics", AUTHOR),
        Triple("Giantess Club Comics", "album/Giantess-Club-Comics", AUTHOR),
        Triple("Innocent Dickgirls Comics", "album/Innocent-Dickgirls-Comics", AUTHOR),
        Triple("Locofuria Comics", "album/Locofuria-Comics", AUTHOR),
        Triple("PigKing - CrazyDad Comics", "album/PigKing-CrazyDad-Comics", AUTHOR),
        Triple("Cartoon Reality Comics", "album/Cartoon-Reality-Comics", AUTHOR),
        Triple("Affect3D Comics", "album/Affect3D-Comics", AUTHOR),
        Triple("TG Comics", "album/TG-Comics", AUTHOR),
        Triple("Melkormancin.com Comics", "album/Melkormancin_com-Comics", AUTHOR),
        Triple("Seiren.com.br Comics", "album/Seiren_com_br-Comics", AUTHOR),
        Triple("Tracy Scops Comics", "album/Tracy-Scops-Comics", AUTHOR),
        Triple("Fred Perry Comics", "album/Fred-Perry-Comics", AUTHOR),
        Triple("Witchking00 Comics", "album/Witchking00-Comics", AUTHOR),
        Triple("8muses Comics", "album/8muses-Comics", AUTHOR),
        Triple("KAOS Comics", "album/KAOS-Comics", AUTHOR),
        Triple("Vaesark Comics", "album/Vaesark-Comics", AUTHOR),
        Triple("Fansadox Comics", "album/Fansadox-Comics", AUTHOR),
        Triple("DreamTales Comics", "album/DreamTales-Comics", AUTHOR),
        Triple("Croc Comics", "album/Croc-Comics", AUTHOR),
        Triple("Jay Marvel Comics", "album/Jay-Marvel-Comics", AUTHOR),
        Triple("JohnPersons.com Comics", "album/JohnPersons_com-Comics", AUTHOR),
        Triple("MuscleFan Comics", "album/MuscleFan-Comics", AUTHOR),
        Triple("Taboolicious.xxx Comics", "album/Taboolicious_xxx-Comics", AUTHOR),
        Triple("MongoBongo Comics", "album/MongoBongo-Comics", AUTHOR),
        Triple("Slipshine Comics", "album/Slipshine-Comics", AUTHOR),
        Triple("Everfire Comics", "album/Everfire-Comics", AUTHOR),
        Triple("PrismGirls Comics", "album/PrismGirls-Comics", AUTHOR),
        Triple("Abimboleb Comics", "album/Abimboleb-Comics", AUTHOR),
        Triple("Y3DF - Your3DFantasy.com Comics", "album/Y3DF-Your3DFantasy_com-Comics", AUTHOR),
        Triple("Grow Comics", "album/Grow-Comics", AUTHOR),
        Triple("OkayOkayOKOk Comics", "album/OkayOkayOKOk-Comics", AUTHOR),
        Triple("Tufos Comics", "album/Tufos-Comics", AUTHOR),
        Triple("Cartoon Valley", "album/Cartoon-Valley", AUTHOR),
        Triple("3DMonsterStories.com Comics", "album/3DMonsterStories_com-Comics", AUTHOR),
        Triple("Kogeikun Comics", "album/Kogeikun-Comics", AUTHOR),
        Triple("The Foxxx Comics", "album/The-Foxxx-Comics", AUTHOR),
        Triple("Theme Collections", "album/Theme-Collections", AUTHOR),
        Triple("Interracial-Comics", "album/Interracial-Comics", AUTHOR),
        Triple("Expansion Comics", "album/Expansion-Comics", AUTHOR),
        Triple("Moiarte Comics", "album/Moiarte-Comics", AUTHOR),
        Triple("Incognitymous Comics", "album/Incognitymous-Comics", AUTHOR),
        Triple("DizzyDills Comics", "album/DizzyDills-Comics", AUTHOR),
        Triple("DukesHardcoreHoneys.com Comics", "album/DukesHardcoreHoneys_com-Comics", AUTHOR),
        Triple("Stormfeder Comics", "album/Stormfeder-Comics", AUTHOR),
        Triple("Bimbo Story Club Comics", "album/Bimbo-Story-Club-Comics", AUTHOR),
        Triple("Smudge Comics", "album/Smudge-Comics", AUTHOR),
        Triple("Dollproject Comics", "album/Dollproject-Comics", AUTHOR),
        Triple("SuperHeroineComixxx", "album/SuperHeroineComixxx", AUTHOR),
        Triple("Karmagik Comics", "album/Karmagik-Comics", AUTHOR),
        Triple("Blacknwhite.com Comics", "album/Blacknwhite_com-Comics", AUTHOR),
        Triple("ArtOfJaguar Comics", "album/ArtOfJaguar-Comics", AUTHOR),
        Triple("Kirtu.com Comics", "album/Kirtu_com-Comics", AUTHOR),
        Triple("UberMonkey Comics", "album/UberMonkey-Comics", AUTHOR),
        Triple("DarkSoul3D Comics", "album/DarkSoul3D-Comics", AUTHOR),
        Triple("Markydaysaid Comics", "album/Markydaysaid-Comics", AUTHOR),
        Triple("Central Comics", "album/Central-Comics", AUTHOR),
        Triple("Frozen Parody Comics", "album/Frozen-Parody-Comics", AUTHOR),
        Triple("Blacknwhitecomics.com Comix", "album/Blacknwhitecomics_com-Comix", AUTHOR),
    )

    protected class SortFilter(private val vals: Array<Pair<String, String>>) : Filter.Select<String>("Sort Order", vals.map { it.first }.toTypedArray()) {
        fun toQueryValue() = vals[state].second
    }
    protected open fun getSortList() = arrayOf(
        Pair("Views", ""),
        Pair("Likes", "like"),
        Pair("Date", "date"),
        Pair("A-Z", "az"),
    )
}
