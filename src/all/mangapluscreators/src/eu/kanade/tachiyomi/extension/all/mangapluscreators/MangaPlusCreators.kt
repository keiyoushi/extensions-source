package eu.kanade.tachiyomi.extension.all.mangapluscreators

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class MangaPlusCreators(override val lang: String) : HttpSource() {

    override val name = "MANGA Plus Creators by SHUEISHA"

    override val baseUrl = "https://mangaplus-creators.jp"

    private val apiUrl = "$baseUrl/api"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)
        .add("User-Agent", USER_AGENT)

    // POPULAR Section
    override fun popularMangaRequest(page: Int): Request {
        val popularUrl = "$baseUrl/titles/popular/?p=m&l=$lang".toHttpUrl()
        return GET(popularUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangasPageFromElement(
        response,
        "div.item-recent",
    )

    private fun parseMangasPageFromElement(response: Response, selector: String): MangasPage {
        val result = response.asJsoup()

        val mangas = result.select(selector).map { element ->
            popularElementToSManga(element)
        }

        return MangasPage(mangas, false)
    }

    private fun popularElementToSManga(element: Element): SManga {
        val titleThumbnailUrl = element.selectFirst(".image-area img")!!.attr("src")
        val titleContentId = titleThumbnailUrl.toHttpUrl().pathSegments[2]
        return SManga.create().apply {
            title = element.selectFirst(".title-area .title")!!.text()
            thumbnail_url = titleThumbnailUrl
            setUrlWithoutDomain("/titles/$titleContentId")
        }
    }

    // LATEST Section
    override fun latestUpdatesRequest(page: Int): Request {
        val apiUrl = "$apiUrl/titles/recent/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("l", lang)
            .addQueryParameter("t", "episode")
            .build()

        return GET(apiUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<MpcResponse>()

        val titles = result.titles.orEmpty().map { title -> title.toSManga() }

        // TODO: handle last page of latest
        return MangasPage(titles, result.status != "error")
    }

    private fun MpcTitle.toSManga(): SManga {
        val mTitle = this.title
        val mAuthor = this.author.name // TODO: maybe not required
        return SManga.create().apply {
            title = mTitle
            thumbnail_url = thumbnail
            setUrlWithoutDomain("/titles/${latestEpisode.titleConnectId}")
            author = mAuthor
        }
    }

    // SEARCH Section
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        // TODO: HTTPSource::fetchSearchManga is deprecated? super.getSearchManga
        if (query.startsWith(PREFIX_TITLE_ID_SEARCH)) {
            val titleContentId = query.removePrefix(PREFIX_TITLE_ID_SEARCH)
            val titleUrl = "$baseUrl/titles/$titleContentId"
            return client.newCall(GET(titleUrl, headers))
                .asObservableSuccess()
                .map { response ->
                    val result = response.asJsoup()
                    val bookBox = result.selectFirst(".book-box")!!
                    val title = SManga.create().apply {
                        title = bookBox.selectFirst("div.title")!!.text()
                        thumbnail_url = bookBox.selectFirst("div.cover img")!!.attr("data-src")
                        setUrlWithoutDomain(titleUrl)
                    }
                    MangasPage(listOf(title), false)
                }
        }
        if (query.startsWith(PREFIX_EPISODE_ID_SEARCH)) {
            val episodeId = query.removePrefix(PREFIX_EPISODE_ID_SEARCH)
            return client.newCall(GET("$baseUrl/episodes/$episodeId", headers))
                .asObservableSuccess().map { response ->
                    val result = response.asJsoup()
                    val readerElement = result.selectFirst("div[react=viewer]")!!
                    val dataTitle = readerElement.attr("data-title")
                    val dataTitleResult = dataTitle.parseAs<MpcReaderDataTitle>()
                    val episodeAsSManga = dataTitleResult.toSManga()
                    MangasPage(listOf(episodeAsSManga), false)
                }
        }
        if (query.startsWith(PREFIX_AUTHOR_ID_SEARCH)) {
            val authorId = query.removePrefix(PREFIX_AUTHOR_ID_SEARCH)
            return client.newCall(GET("$baseUrl/authors/$authorId", headers))
                .asObservableSuccess()
                .map { response ->
                    val result = response.asJsoup()
                    val elements = result.select("#works .manga-list li .md\\:block")
                    val smangas = elements.map { element ->
                        val titleThumbnailUrl = element.selectFirst(".image-area img")!!.attr("src")
                        val titleContentId = titleThumbnailUrl.toHttpUrl().pathSegments[2]
                        SManga.create().apply {
                            title = element.selectFirst("p.text-white")!!.text().toString()
                            thumbnail_url = titleThumbnailUrl
                            setUrlWithoutDomain("/titles/$titleContentId")
                        }
                    }
                    MangasPage(smangas, false)
                }
        }
        if (query.isNotBlank()) {
            return super.fetchSearchManga(page, query, filters)
        }

        // nothing to search, filters active -> browsing /genres instead
        // TODO: check if there's a better way (filters is independent of search but part of it)
        val genreUrl = baseUrl.toHttpUrl().newBuilder()
            .apply {
                addPathSegment("genres")
                addQueryParameter("l", lang)
                filters.forEach { filter ->
                    when (filter) {
                        is SortFilter -> {
                            if (filter.selected.isNotEmpty()) {
                                addQueryParameter("s", filter.selected)
                            }
                        }

                        is GenreFilter -> addPathSegment(filter.selected)

                        else -> { /* Nothing else is supported for now */ }
                    }
                }
            }.build()

        return client.newCall(GET(genreUrl, headers))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }

    private fun MpcReaderDataTitle.toSManga(): SManga {
        val mTitle = title
        return SManga.create().apply {
            title = mTitle
            thumbnail_url = thumbnail
            setUrlWithoutDomain("/titles/$contentsId")
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // TODO: maybe this needn't be a new builder and just similar to `popularUrl` above?
        val searchUrl = "$baseUrl/keywords".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("s", "date")
            .addQueryParameter("lang", lang)
            .build()

        return GET(searchUrl, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangasPageFromElement(
        response,
        "div.item-search",
    )

    // MANGA Section
    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.asJsoup()
        val bookBox = result.selectFirst(".book-box")!!

        return SManga.create().apply {
            title = bookBox.selectFirst("div.title")!!.text()
            author = bookBox.selectFirst("div.mod-btn-profile div.name")!!.text()
            description = bookBox.select("div.summary p")
                .joinToString("\n\n") { it.text() }
            status = when (bookBox.selectFirst("div.book-submit-type")!!.text()) {
                "Series" -> SManga.ONGOING
                "One-shot" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = bookBox.select("div.genre-area div.tag-genre")
                .joinToString(", ") { it.text() }
            thumbnail_url = bookBox.selectFirst("div.cover img")!!.attr("data-src")
        }
    }

    // CHAPTER Section
    override fun chapterListRequest(manga: SManga): Request {
        val titleContentId = (baseUrl + manga.url).toHttpUrl().pathSegments[1]
        return chapterListPageRequest(1, titleContentId)
    }

    private fun chapterListPageRequest(page: Int, titleContentId: String): Request = GET("$baseUrl/titles/$titleContentId/?page=$page", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterListResponse = chapterListPageParse(response)
        val chapterListResult = chapterListResponse.chapters.toMutableList()

        var hasNextPage = chapterListResponse.hasNextPage
        val titleContentId = response.request.url.pathSegments[1]
        var page = 1
        while (hasNextPage) {
            page += 1
            val nextPageRequest = chapterListPageRequest(page, titleContentId)
            val nextPageResponse = client.newCall(nextPageRequest).execute()
            val nextPageResult = chapterListPageParse(nextPageResponse)
            if (nextPageResult.chapters.isEmpty()) {
                break
            }
            chapterListResult.addAll(nextPageResult.chapters)
            hasNextPage = nextPageResult.hasNextPage
        }

        return chapterListResult.asReversed()
    }

    private fun chapterListPageParse(response: Response): ChaptersPage {
        val result = response.asJsoup()
        val chapters = result.select(".mod-item-series").map { element ->
            chapterElementToSChapter(element)
        }
        val hasResult = result.select(".mod-pagination .next").isNotEmpty()
        return ChaptersPage(
            chapters,
            hasResult,
        )
    }

    private fun chapterElementToSChapter(element: Element): SChapter {
        val episode = element.attr("href").substringAfterLast("/")
        val latestUpdatedDate = element.selectFirst(".first-update")!!.text()
        val chapterNumberElement = element.selectFirst(".number")!!.text()
        val chapterNumber = chapterNumberElement.substringAfter("#").toFloatOrNull()
        return SChapter.create().apply {
            setUrlWithoutDomain("/episodes/$episode")
            date_upload = CHAPTER_DATE_FORMAT.tryParse(latestUpdatedDate)
            name = chapterNumberElement
            chapter_number = if (chapterNumberElement == "One-shot") {
                0F
            } else {
                chapterNumber ?: -1F
            }
        }
    }

    // PAGES & IMAGES Section
    override fun pageListParse(response: Response): List<Page> {
        val result = response.asJsoup()
        val readerElement = result.selectFirst("div[react=viewer]")!!
        val dataPages = readerElement.attr("data-pages")
        val refererUrl = response.request.url.toString()
        return dataPages.parseAs<MpcReaderDataPages>().pc.map { page ->
            Page(page.pageNo, refererUrl, page.imageUrl)
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .removeAll("Origin")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    companion object {
        private val CHAPTER_DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        }
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36"
        const val PREFIX_TITLE_ID_SEARCH = "title:"
        const val PREFIX_EPISODE_ID_SEARCH = "episode:"
        const val PREFIX_AUTHOR_ID_SEARCH = "author:"
    }

    // FILTERS Section
    override fun getFilterList() = FilterList(
        Filter.Separator(),
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortFilter(),
        GenreFilter(),
        Filter.Separator(),
    )

    private class SortFilter :
        SelectFilter(
            "Sort",
            listOf(
                SelectFilterOption("Popularity", ""),
                SelectFilterOption("Date", "latest_desc"),
                SelectFilterOption("Likes", "like_desc"),
            ),
            0,
        )

    private class GenreFilter :
        SelectFilter(
            "Genres",
            listOf(
                SelectFilterOption("Fantasy", "fantasy"),
                SelectFilterOption("Action", "action"),
                SelectFilterOption("Romance", "romance"),
                SelectFilterOption("Horror", "horror"),
                SelectFilterOption("Slice of Life", "slice_of_life"),
                SelectFilterOption("Comedy", "comedy"),
                SelectFilterOption("Sports", "sports"),
                SelectFilterOption("Sci-Fi", "sf"),
                SelectFilterOption("Mystery", "mystery"),
                SelectFilterOption("Others", "others"),
            ),
            0,
        )

    private abstract class SelectFilter(
        name: String,
        private val options: List<SelectFilterOption>,
        default: Int = 0,
    ) : Filter.Select<String>(
        name,
        options.map { it.name }.toTypedArray(),
        default,
    ) {
        val selected: String
            get() = options[state].value
    }

    private class SelectFilterOption(val name: String, val value: String)
}
