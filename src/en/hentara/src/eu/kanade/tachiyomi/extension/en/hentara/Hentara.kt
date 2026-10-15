package eu.kanade.tachiyomi.extension.en.hentara

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import kotlin.time.Instant

@Source
abstract class Hentara : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    private object Api {
        const val BASE = "https://hentara.com/r2-data"

        fun index() = "$BASE/index.json"
        fun comic(slug: String) = "$BASE/comics/$slug.json"
        fun episode(slug: String, ep: Int) = "$BASE/episodes/$slug/$ep.json"
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = searchIndex("", sortIdx = 1, genreIdx = 0)

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchIndex("", sortIdx = 0, genreIdx = 0)

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortIdx = filters.firstInstanceOrNull<SortFilter>()?.state ?: 0
        val genreIdx = filters.firstInstanceOrNull<GenreFilter>()?.state ?: 0

        return searchIndex(query, sortIdx, genreIdx)
    }

    private suspend fun searchIndex(query: String, sortIdx: Int, genreIdx: Int): MangasPage {
        val data = client.get(Api.index()).parseAs<HentaraIndexDto>()

        val genre = GENRES.getOrNull(genreIdx) ?: "Any"

        val mangas = data.comics
            .asSequence()
            .filter { comic ->
                (query.isBlank() || comic.title.contains(query, ignoreCase = true)) &&
                    (genre == "Any" || comic.genres.any { it.name.equals(genre, ignoreCase = true) })
            }
            .let { filtered ->
                when (sortIdx) {
                    0 -> filtered.sortedByDescending { Instant.tryParse(it.latestEpisodeDate) }
                    1 -> filtered.sortedByDescending { it.viewCount }
                    2 -> filtered.sortedBy { it.title }
                    else -> filtered
                }
            }
            .map { it.toSManga() }
            .toList()

        return MangasPage(mangas, false)
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast("/")
        val data = client.get(Api.comic(slug)).parseAs<HentaraMangaDto>()
        val comicSlug = data.comic.slug

        val chapterList = data.episodes.map { ep ->
            SChapter.create().apply {
                url = "/manhwa/$comicSlug/chapter-${ep.episodeNumber}"
                name = ep.chapterName()
                chapter_number = ep.episodeNumber.toFloat()
                date_upload = Instant.tryParse(ep.createdAt)
            }
        }.sortedByDescending { it.chapter_number }

        return SMangaUpdate(data.comic.toSManga(), chapterList)
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pathSegments = chapter.url.trim('/').split("/")
        if (pathSegments.size < 3) {
            throw IllegalArgumentException("Malformed chapter URL: ${chapter.url}")
        }
        val slug = pathSegments[1]
        val ep = pathSegments[2].substringAfter("chapter-").toIntOrNull()
            ?: throw IllegalArgumentException("Malformed chapter URL: ${chapter.url}")

        val data = client.get(Api.episode(slug, ep)).parseAs<HentaraEpisodeDto>()

        return data.pages.map {
            Page(it.pageNumber - 1, imageUrl = it.imageUrl)
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        GenreFilter(),
    )

    private class GenreFilter : Filter.Select<String>("Genre", GENRES)

    private class SortFilter : Filter.Select<String>("Sort", arrayOf("Latest", "Popular", "Alphabetical"))

    // ============================= Utilities =============================

    companion object {
        private val GENRES = arrayOf(
            "Any", "Action", "BL", "Cheating", "Detective", "Drama", "Harem",
            "In-Law", "MILF", "Married", "Office", "Romance", "Spin-Off",
            "Thriller", "University", "College", "Nerd",
        )
    }
}
