package eu.kanade.tachiyomi.extension.en.kuramanga

import eu.kanade.tachiyomi.network.GET
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
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class KuraManga : HttpSource() {

    override val name = "KuraManga"
    override val baseUrl = "https://kuramanga.com"
    override val lang = "en"
    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = if (page == 1) {
        GET(baseUrl, headers)
    } else {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("ajax", "1")
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .build()
        GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (response.request.url.queryParameter("ajax") == "1") {
            return searchMangaParse(response)
        }

        val document = response.asJsoup()
        val mangas = document.select(".popular-glide .manga-card").mapNotNull { element ->
            val titleEl = element.selectFirst(".manga-title") ?: return@mapNotNull null
            SManga.create().apply {
                title = titleEl.text().trim()
                url = "/" + element.attr("href").removePrefix("/")
                thumbnail_url = element.selectFirst("img.manga-thumb")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, true)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".update-list .update-row").mapNotNull { element ->
            val link = element.selectFirst("a.update-series-link") ?: return@mapNotNull null
            SManga.create().apply {
                title = link.text().trim()
                url = "/" + link.attr("href").removePrefix("/")
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("ajax", "1")
            if (query.isNotEmpty()) {
                addQueryParameter("name", query)
            }
            addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        val genres = filter.state
                            .filter { it.state }
                            .map { it.name }
                        if (genres.isNotEmpty()) {
                            addQueryParameter("genre", genres.joinToString(","))
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state != 0) {
                            addQueryParameter("status", filter.vals[filter.state].lowercase())
                        }
                    }
                    is AdultFilter -> {
                        if (!filter.state) {
                            addQueryParameter("adult", "0")
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchResponse = response.parseAs<SearchResponse>()
        val mangas = searchResponse.data.map { dto ->
            SManga.create().apply {
                title = dto.title
                url = "/${dto.normalized_title}"
                thumbnail_url = dto.cover_image_url
            }
        }
        val offset = response.request.url.queryParameter("offset")?.toInt() ?: 0
        val hasNextPage = (searchResponse.data.size == PAGE_SIZE) && (offset + searchResponse.data.size < searchResponse.total)
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.manga-title")!!.text().trim()
            description = document.selectFirst(".summary-inner")?.text()?.trim()
            author = document.selectFirst(".meta-grid div:contains(Author:)")?.text()?.substringAfter("Author:")?.trim()
            artist = document.selectFirst(".meta-grid div:contains(Artist:)")?.text()?.substringAfter("Artist:")?.trim()
            genre = document.select(".genre-list a.genre-chip").joinToString { it.text().trim() }
            status = document.selectFirst(".meta-grid div:contains(Status:)")?.text()?.substringAfter("Status:")?.trim()?.lowercase().parseStatus()
            thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
        }
    }

    private fun String?.parseStatus(): Int = when (this) {
        "ongoing", "upcoming" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "on_hold", "on hold" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapter-list .chapter-item").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            SChapter.create().apply {
                name = link.text().trim()
                url = "/" + link.attr("href").removePrefix("/")
                date_upload = dateFormat.tryParse(element.selectFirst("time")?.text()?.trim())
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script[type='application/ld+json']:containsData(ComicStory)")
            ?: throw Exception("Images not found")

        return script.data().parseAs<ChapterPageDto>().image.mapIndexed { index, url ->
            Page(index, "", url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList(): FilterList = FilterList(
        StatusFilter("Status", statusList),
        AdultFilter("Include Adult Content"),
        GenreFilter("Genres", genreNames.map { Genre(it) }),
    )

    // ============================== Private ===============================
    private val dateFormat by lazy {
        SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
    }

    companion object {
        private const val PAGE_SIZE = 10
    }

    // =============================== DTOs =================================
    @Serializable
    private data class SearchResponse(
        val data: List<MangaDto>,
        val total: Int,
    )

    @Serializable
    private data class MangaDto(
        val id: Int,
        val title: String,
        val cover_image_url: String? = null,
        val normalized_title: String,
    )

    @Serializable
    private data class ChapterPageDto(
        val image: List<String>,
    )

    // =========================== Filter Classes ===========================
    private abstract class SelectFilter(name: String, val vals: Array<String>) : Filter.Select<String>(name, vals)

    private class StatusFilter(name: String, vals: Array<String>) : SelectFilter(name, vals)

    private class GenreFilter(name: String, genres: List<Genre>) : Filter.Group<Genre>(name, genres)

    private class Genre(name: String) : Filter.CheckBox(name)

    private class AdultFilter(name: String) : Filter.CheckBox(name, true)

    private val statusList = arrayOf("All", "Ongoing", "Completed", "On_hold", "Upcoming")

    private val genreNames = listOf(
        "Action", "Adaptation", "Adult", "Adventure", "BL", "Borderline H", "College life", "Comedy", "Crime", "Drama", "Ecchi", "Explicit Sex", "Fantasy", "GL", "Gender Bender", "Harem", "Historical", "Horror", "Isekai", "Josei", "Loli", "Magic", "Magical", "Manhua", "Manhwa", "Martial Arts", "Mature", "Mystery", "Office Workers", "Psychological", "Reincarnation", "Revenge", "Romance", "School Life", "Sci-Fi", "Seinen", "Shoujo", "Shounen", "Slice of Life", "Smut", "Sport", "Supernatural", "Survival", "Thriller", "Time travel", "Tragedy", "Uncensored", "Vampire", "Violence", "Webtoons", "Yuri",
    )
}
