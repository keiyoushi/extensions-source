package eu.kanade.tachiyomi.extension.en.manhwa18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Manhwa18 : HttpSource() {

    override val baseUrl = "https://manhwa18.com"
    private val apiUrl = "https://cdn3.manhwa18.com/api/v1"
    override val lang = "en"
    override val name = "Manhwa18"
    override val supportsLatest = true

    override val versionId = 2

    private val json: Json by injectLazy()

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/get-data-products?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<MangaListBrowse>(response.body.string()).browseList
        return MangasPage(
            result.mangaList.map { manga ->
                manga.toSManga()
            },
            hasNextPage = result.current_page < result.last_page,
        )
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/get-data-products-in-filter?arange=new-updated?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$apiUrl/get-search-suggest/$query", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<List<Manga>>(response.body.string())
        return MangasPage(
            result.map { manga ->
                manga.toSManga()
            },
            hasNextPage = false,
        )
    }

    // manga details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast('/')
        return GET("$apiUrl/get-detail-product/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaDetail = json.decodeFromString<MangaDetail>(response.body.string())
        return mangaDetail.manga.toSManga().apply {
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/${manga.url}"
    }

    // chapter list
    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaDetail = json.decodeFromString<MangaDetail>(response.body.string())
        val mangaSlug = mangaDetail.manga.slug

        return mangaDetail.manga.episodes?.map { chapter ->
            SChapter.create().apply {
                // compatible with old theme
                setUrlWithoutDomain("manga/$mangaSlug/${chapter.slug}")
                name = chapter.name
                date_upload = chapter.created_at?.parseDate() ?: 0L
                chapter_number = chapter.name.toFloat()
            }
        } ?: emptyList()
    }

    private fun String.toFloat(): Float {
        val cleanedString = replace(Regex("[^0-9.]"), "")
        return cleanedString.toFloatOrNull() ?: 0f
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl/${chapter.url}"
    }

    // page list
    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url.substringAfter('/')
        return GET("$apiUrl/get-episode/$slug", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<ChapterDetail>(response.body.string())
        return result.episode.servers?.first()?.images?.mapIndexed { index, image ->
            Page(index = index, imageUrl = image)
        } ?: emptyList()
    }

    // unused
    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    private fun String.parseDate(): Long {
        return runCatching { DATE_FORMATTER.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        }
    }
//
//    override fun getFilterList(): FilterList = FilterList(
//        Status(
//            "Status",
//            "All",
//            "Ongoing",
//            "On hold",
//            "Completed",
//        ),
//        Sort(
//            "Order",
//            "A-Z",
//            "Z-A",
//            "Latest update",
//            "New manhwa",
//            "Most view",
//            "Most like",
//        ),
//        GenreList(getGenreList(), "Genre"),
//    )
//
//    // To populate this list:
//    // console.log([...document.querySelectorAll("div.search-gerne_item")].map(elem => `Genre("${elem.textContent.trim()}", ${elem.querySelector("label").getAttribute("data-genre-id")}),`).join("\n"))
//    override fun getGenreList() = listOf(
//        Genre("Adult", 4),
//        Genre("Doujinshi", 9),
//        Genre("Harem", 17),
//        Genre("Manga", 24),
//        Genre("Manhwa", 26),
//        Genre("Mature", 28),
//        Genre("NTR", 33),
//        Genre("Romance", 36),
//        Genre("Webtoon", 57),
//        Genre("Action", 59),
//        Genre("Comedy", 60),
//        Genre("BL", 61),
//        Genre("Horror", 62),
//        Genre("Raw", 63),
//        Genre("Uncensore", 64),
//    )
//
//    override fun dateUpdatedParser(date: String): Long =
//        runCatching { dateFormatter.parse(date.substringAfter(" - "))?.time }.getOrNull() ?: 0L
}
