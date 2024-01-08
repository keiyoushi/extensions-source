package eu.kanade.tachiyomi.extension.fr.aralosbd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import org.jsoup.parser.Parser
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class AralosBD : HttpSource() {

    companion object {
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE)

        val LINK_REGEX = "\\[([^]]+)\\]\\(([^)]+)\\)".toRegex()
        val BOLD_REGEX = "\\*+\\s*([^\\*]*)\\s*\\*+".toRegex()
        val ITALIC_REGEX = "_+\\s*([^_]*)\\s*_+".toRegex()
        val ICON_REGEX = ":+[a-zA-Z]+:".toRegex()
        val PAGE_REGEX = "page:([0-9]+)".toRegex()
    }

    private fun cleanString(string: String): String {
        return Parser.unescapeEntities(string, false)
            .substringBefore("---")
            .replace(LINK_REGEX, "$2")
            .replace(BOLD_REGEX, "$1")
            .replace(ITALIC_REGEX, "$1")
            .replace(ICON_REGEX, "")
            .trim()
    }

    override val name = "AralosBD"
    override val baseUrl = "https://aralosbd.fr"
    override val lang = "fr"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int): Request {
        // Let's use a request that just query everything by page, sorted by total views (title + chapters)
        return GET("$baseUrl/manga/search?s=sort:allviews;limit:24;-id:3;page:${page - 1};order:desc", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val searchResult = json.decodeFromString<AralosBDSearchResult>(response.body.string())

        var hasNextPage = false
        val pageMatch = PAGE_REGEX.find(response.request.url.toString())
        if (pageMatch != null && pageMatch.groupValues.count() > 1) {
            val currentPage = pageMatch.groupValues[1].toInt()
            hasNextPage = currentPage < searchResult.page_count - 1
        }

        return MangasPage(searchResult.mangas.map(::searchMangaToSManga), hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val searchResult = json.decodeFromString<AralosBDSearchResult>(response.body.string())

        var hasNextPage = false
        val pageMatch = PAGE_REGEX.find(response.request.url.toString())
        if (pageMatch != null && pageMatch.groupValues.count() > 1) {
            val currentPage = pageMatch.groupValues[1].toInt()
            hasNextPage = currentPage < searchResult.page_count - 1
        }

        return MangasPage(searchResult.mangas.map(::searchMangaToSManga), hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        // That's almost exactly the same stuff that the popular request, simply ordered differently
        // A new title will always have a greater ID, so we can sort by ID. Using year would not be
        // accurate because it's the release year
        // It would be better to sort by last updated, but this is not yet in the API
        return GET("$baseUrl/manga/search?s=sort:id;limit:24;-id:3;page:${page - 1};order:desc", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // For a basic search, we call the appropriate endpoint
        return GET("$baseUrl/manga/search?s=page:${page - 1};sort:id;order:desc;text:$query", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchResult = json.decodeFromString<AralosBDSearchResult>(response.body.string())

        var hasNextPage = false
        val pageMatch = PAGE_REGEX.find(response.request.url.toString())
        if (pageMatch != null && pageMatch.groupValues.count() > 1) {
            val currentPage = pageMatch.groupValues[1].toInt()
            hasNextPage = currentPage < searchResult.page_count - 1
        }

        return MangasPage(searchResult.mangas.map(::searchMangaToSManga), hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        // This is a needed call, so the Tachiyomi user behave like a regular user
        // return GET(manga.url.replace("display?", "api?get=manga&"), headers)
        return GET(manga.url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val responseBody = client.newCall(GET(response.request.url.toString().replace("display?", "api?get=manga&"), headers)).execute().body

        val manga = json.decodeFromString<AralosBDManga>(responseBody.string())

        return SManga.create().apply {
            url = "$baseUrl/manga/display?id=${manga.id}"
            title = manga.main_title
            artist = "" // manga.authors.joinToString(", ")
            author = manga.authors?.joinToString(", ", transform = ::authorToString)
            description = cleanString("${manga.description}\n\n" + (manga.fulldescription ?: ""))
            status = 0 // This is not on the website
            genre = manga.tags?.joinToString(", ", transform = ::tagToString)
            thumbnail_url = "$baseUrl/${manga.icon}"
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(manga.url.replace("display?id", "api?get=chapters&manga"), headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val searchResult = json.decodeFromString<List<AralosBDChapter>>(response.body.string())

        val validSearchResults = mutableListOf<AralosBDChapter>()
        searchResult.filterTo(validSearchResults) { it.chapter_released == "1" }

        return validSearchResults.map(::chapterToSChapter)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val responseBody = client.newCall(GET(response.request.url.toString().replace("chapter?id", "api?get=pages&chapter"), headers)).execute().body

        val pageResult = json.decodeFromString<AralosBDPages>(responseBody.string())

        return pageResult.links.mapIndexed { index, link ->
            Page(
                index,
                "$baseUrl/$link",
                "$baseUrl/$link",
            )
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used.")

    private fun authorToString(author: AralosBDAuthor) = author.name
    private fun tagToString(tag: AralosBDTag) = tag.tag

    private fun searchMangaToSManga(manga: AralosBDSearchManga): SManga {
        return SManga.create().apply {
            // No need to trim, it's already done by the server
            title = manga.title

            // Just need to append the base url to the relative link returned
            thumbnail_url = "$baseUrl/${manga.icon}"

            // The url of the manga is simply based on the manga ID for now
            url = "$baseUrl/manga/display?id=${manga.id}"
        }
    }

    private fun chapterToSChapter(chapter: AralosBDChapter): SChapter {
        return SChapter.create().apply {
            url = "$baseUrl/manga/chapter?id=${chapter.chapter_id}"
            name = chapter.chapter_number + " - " + chapter.chapter_title
            date_upload = try { DATE_FORMAT.parse(chapter.chapter_release_time)!!.time } catch (e: Exception) { System.currentTimeMillis() }
            // chapter_number = // This is a string and it can be 2.5.1 for example
            scanlator = chapter.chapter_translator
        }
    }
}

@Serializable
data class AralosBDSearchManga(
    val icon: String = "",
    val title: String = "",
    val id: String = "",
    val read_count: String = "",
    val chapter_count: String = "",
    val is_favorite: Boolean = false,
    val is_liked: Boolean = false,
)

@Serializable
data class AralosBDSearchResult(
    val error: Int = 0,
    val result_count: Int = 0,
    val page_count: Int = 0,
    val mangas: List<AralosBDSearchManga> = emptyList(),
)

@Serializable
data class AralosBDAlternativeTitle(
    val title: String = "",
)

@Serializable
data class AralosBDAuthor(
    val name: String = "",
)

@Serializable
data class AralosBDTranslator(
    val name: String = "",
)

@Serializable
data class AralosBDTag(
    val tag: String = "",
    val color: String = "",
)

@Serializable
data class AralosBDManga(
    val main_title: String = "",
    val fulldescription: String? = "",
    val description: String = "",
    val year: String = "",
    val id: Int = 0,
    val alternative_titles: List<AralosBDAlternativeTitle>? = emptyList(),
    val authors: List<AralosBDAuthor>? = emptyList(),
    val translators: List<AralosBDTranslator>? = emptyList(),
    val tags: List<AralosBDTag>? = emptyList(),
    val banner: String = "",
    val icon: String = "",
    val error: Int = 0,
)

@Serializable
data class AralosBDChapter(
    val chapter_number: String = "",
    val chapter_user: String = "",
    val chapter_title: String = "",
    val chapter_translator: String? = "",
    val chapter_view_count: String = "",
    val chapter_like_count: String = "",
    val chapter_date: String = "",
    val chapter_id: String = "",
    val chapter_read: Boolean = false,
    val chapter_released: String = "0",
    val chapter_release_time: String = "",
)

@Serializable
data class AralosBDPages(
    val error: Int = 0,
    val links: List<String> = emptyList(),
)
