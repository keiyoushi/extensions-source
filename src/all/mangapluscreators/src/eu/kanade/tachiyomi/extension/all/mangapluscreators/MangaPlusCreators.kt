package eu.kanade.tachiyomi.extension.all.mangapluscreators

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaPlusCreators : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("User-Agent", USER_AGENT)

    // POPULAR Section
    override suspend fun getPopularManga(page: Int): MangasPage {
        val popularUrl = "$baseUrl/titles/popular/?p=m&l=$lang".toHttpUrl()
        return parseMangasPageFromElement(client.get(popularUrl), "div.item-recent")
    }

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
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val apiUrl = "$apiUrl/titles/recent/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("l", lang)
            .addQueryParameter("t", "episode")
            .build()

        val result = client.get(apiUrl).parseAs<MpcResponse>()

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
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host !in listOf("mangaplus-creators.jp", "medibang.com")) {
            return MangasPage(emptyList(), false)
        }
        val pathIndex = if (url.host == "medibang.com") 1 else 0
        val idIndex = pathIndex + 1
        if (url.pathSegments.size <= idIndex) {
            return MangasPage(emptyList(), false)
        }
        val id = url.pathSegments[idIndex]
        return when (url.pathSegments[pathIndex]) {
            "episodes" -> {
                val result = client.get("$baseUrl/episodes/$id").asJsoup()
                val readerElement = result.selectFirst("div[react=viewer]")!!
                val dataTitle = readerElement.attr("data-title")
                val dataTitleResult = dataTitle.parseAs<MpcReaderDataTitle>()
                MangasPage(listOf(dataTitleResult.toSManga()), false)
            }
            "authors" -> {
                val result = client.get("$baseUrl/authors/$id").asJsoup()
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
            "titles" -> {
                val titleUrl = "$baseUrl/titles/$id"
                val result = client.get(titleUrl).asJsoup()
                val bookBox = result.selectFirst(".book-box")!!
                val title = SManga.create().apply {
                    title = bookBox.selectFirst("div.title")!!.text()
                    thumbnail_url = bookBox.selectFirst("div.cover img")!!.attr("data-src")
                    setUrlWithoutDomain(titleUrl)
                }
                MangasPage(listOf(title), false)
            }
            else -> MangasPage(emptyList(), false)
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            // TODO: maybe this needn't be a new builder and just similar to `popularUrl` above?
            val searchUrl = "$baseUrl/keywords".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("s", "date")
                .addQueryParameter("lang", lang)
                .build()

            return parseMangasPageFromElement(client.get(searchUrl), "div.item-search")
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

        return parseMangasPageFromElement(client.get(genreUrl), "div.item-recent")
    }

    private fun MpcReaderDataTitle.toSManga(): SManga {
        val mTitle = title
        return SManga.create().apply {
            title = mTitle
            thumbnail_url = thumbnail
            setUrlWithoutDomain("/titles/$contentsId")
        }
    }

    // MANGA Section
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) mangaDetailsParse(client.get(getMangaUrl(manga)).asJsoup()) else manga
        }
        val chapterList = async {
            if (fetchChapters) getChapterList(manga) else chapters
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private fun mangaDetailsParse(result: Document): SManga {
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
    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val titleContentId = (baseUrl + manga.url).toHttpUrl().pathSegments[1]
        val chapterListResponse = chapterListPageParse(client.get(chapterListPageUrl(1, titleContentId)))
        val chapterListResult = chapterListResponse.chapters.toMutableList()

        var hasNextPage = chapterListResponse.hasNextPage
        var page = 1
        while (hasNextPage) {
            page += 1
            val nextPageResult = chapterListPageParse(client.get(chapterListPageUrl(page, titleContentId)))
            if (nextPageResult.chapters.isEmpty()) {
                break
            }
            chapterListResult.addAll(nextPageResult.chapters)
            hasNextPage = nextPageResult.hasNextPage
        }

        return chapterListResult.asReversed()
    }

    private fun chapterListPageUrl(page: Int, titleContentId: String) = "$baseUrl/titles/$titleContentId/?page=$page"

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
            date_upload = CHAPTER_DATE_FORMAT.tryParseDate(latestUpdatedDate)
            name = chapterNumberElement
            chapter_number = if (chapterNumberElement == "One-shot") {
                0F
            } else {
                chapterNumber ?: -1F
            }
        }
    }

    // PAGES & IMAGES Section
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val refererUrl = getChapterUrl(chapter)
        val result = client.get(refererUrl).asJsoup()
        val readerElement = result.selectFirst("div[react=viewer]")!!
        val dataPages = readerElement.attr("data-pages")
        return dataPages.parseAs<MpcReaderDataPages>().pc.map { page ->
            Page(page.pageNo, refererUrl, page.imageUrl)
        }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .removeHeader("Origin")
        .header("Referer", page.url)
        .build()

    companion object {
        private val CHAPTER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/104.0.0.0 Safari/537.36"
    }

    // FILTERS Section
    override fun getFilterList(data: JsonElement?) = FilterList(
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
