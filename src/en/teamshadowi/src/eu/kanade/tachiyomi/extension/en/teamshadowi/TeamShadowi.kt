package eu.kanade.tachiyomi.extension.en.teamshadowi

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import kotlin.time.Instant

@Source
abstract class TeamShadowi : KeiSource() {

    // Reusable headers for fetching raw JSON React Server Component payloads
    private val rscHeaders get() = headers.newBuilder().add("Rsc", "1").build()

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * 20
        val res = client.get("$baseUrl/api/series/popular?timePeriod=all&genre=all&sortBy=rating&offset=$offset&limit=20")
        return parseMangasPage(res)
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val res = response.parseAs<SeriesResponse>()
        val mangas = res.data.map { it.toSManga() }
        return MangasPage(mangas, res.hasMore)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * 20
        val res = client.get("$baseUrl/api/series/popular?timePeriod=all&genre=all&sortBy=created&offset=$offset&limit=20")
        return parseMangasPage(res)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = if (query.isNotBlank()) {
        val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()

        val res = client.get(url).parseAs<SearchResponse>()
        val mangas = res.series.map { it.toSManga() }
        MangasPage(mangas, false)
    } else {
        val offset = (page - 1) * 20

        val url = "$baseUrl/api/series/popular".toHttpUrl().newBuilder().apply {
            addQueryParameter("offset", offset.toString())
            addQueryParameter("limit", "20")
            addQueryParameter("timePeriod", "all")

            var genre = "all"
            var sort = "rating"

            for (filter in filters) {
                when (filter) {
                    is GenreFilter -> genre = filter.toUriPart()
                    is SortFilter -> sort = filter.toUriPart()
                    else -> {}
                }
            }

            addQueryParameter("genre", genre)
            addQueryParameter("sortBy", sort)
        }.build()

        parseMangasPage(client.get(url))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "series") {
            return null
        }

        val mangaUrl = "/series/${url.pathSegments[1]}"
        val manga = SManga.create().apply {
            this.url = mangaUrl
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = mangaUrl
            }
    }

    // =============================== Updates ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = client.get(baseUrl + manga.url, rscHeaders).extractNextJs<PublicDataSeries>()
            ?: throw Exception("Failed to extract data")

        val seriesData = data.series
        val newManga = SManga.create().apply {
            title = seriesData.title
            description = seriesData.description
            thumbnail_url = seriesData.thumbnailUrl
            status = when (seriesData.status?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = (seriesData.genres.orEmpty() + seriesData.tags.orEmpty()).distinct().joinToString()
        }

        val chaptersData = data.chapters
        val slug = manga.url.substringAfterLast('/')
        val chapters = chaptersData.map { chap ->
            val numStr = chap.number.toString().removeSuffix(".0")
            val cleanDate = chap.createdAt
            SChapter.create().apply {
                url = "/read/$slug/$numStr"
                name = if (chap.title.isNullOrBlank()) "Chapter $numStr" else "Chapter $numStr: ${chap.title}"
                date_upload = Instant.tryParse(cleanDate)
                chapter_number = chap.number
            }
        }.sortedByDescending { it.chapter_number }

        return SMangaUpdate(newManga, chapters)
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(baseUrl + chapter.url, rscHeaders)
            .extractNextJs<PublicDataChapter>()
            ?.pages
            ?: return emptyList()

        return data.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        GenreFilter(),
    )
}
