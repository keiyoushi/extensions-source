package eu.kanade.tachiyomi.multisrc.vinetheme

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

abstract class VineTheme :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val rscHeaders: Headers get() = headersBuilder().set("rsc", "1").build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF
            title = "Hide locked chapters"
            summary = "Hide chapters that require coins to read"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF = "pref_hide_locked_chapters"
    }

    // ------------------------- Browse (Popular) -------------------------

    override suspend fun getPopularManga(page: Int): MangasPage = getApiMangasPage(page, "popular")

    // ------------------------- Latest -------------------------

    override suspend fun getLatestUpdates(page: Int): MangasPage = getApiMangasPage(page, "updated")

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) error("Unsupported url")
        val slug = url.pathSegments.getOrNull(2) ?: return null
        if (url.pathSegments.getOrNull(3) == "chapter") return null
        val detail = client.get("$baseUrl/series/comic/$slug", headers = rscHeaders)
            .extractNextJs<DetailDto> { it is JsonObject && "series" in it && "chapters" in it }
            ?: return null
        return detail.series.toSManga(baseUrl)
    }

    private suspend fun getApiMangasPage(page: Int, sort: String): MangasPage {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("contentMode", "comics")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .build()
        val data = client.get(url).parseAs<ApiSeriesResponse>()
        return MangasPage(data.data.map { it.toSManga(baseUrl) }, data.meta?.hasMore == true)
    }

    // ------------------------- Search -------------------------

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
            .apply {
                addQueryParameter("limit", "24")
                addQueryParameter("contentMode", "comics")
                if (query.isNotEmpty()) {
                    addQueryParameter("q", query)
                }
                filters.filterIsInstance<UriQueryFilter>().forEach { it.addToQuery(this) }
                addQueryParameter("page", page.toString())
            }
            .build()
        val data = client.get(url).parseAs<ApiSeriesResponse>()
        return MangasPage(data.data.map { it.toSManga(baseUrl) }, data.meta?.hasMore == true)
    }

    // ------------------------- Filter -------------------------

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/genres").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<GenreListDto>()?.genres
        return FilterList(
            buildList {
                add(SortFilter())
                add(StatusFilter())
                add(TypeFilter())
                add(OriginFilter())
                if (!genres.isNullOrEmpty()) {
                    add(GenreFilter(genres.map { it.name.stripEmoji() to it.genreSlug }.toTypedArray()))
                }
            },
        )
    }

    // ------------------------- Details + Chapters -------------------------

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detailsUrl = getMangaUrl(manga).toHttpUrl().newBuilder()
            .addQueryParameter("sort", "desc")
            .build()
        val details = client.get(detailsUrl, rscHeaders).extractNextJs<DetailDto> { it is JsonObject && "series" in it && "chapters" in it }

        val updatedManga = details?.series?.toSManga(baseUrl, manga) ?: manga
        val updatedChapters = if (fetchChapters) fetchAllChapters(manga, details, chapters) else chapters

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override val supportsRelatedMangas: Boolean get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val detailsUrl = getMangaUrl(manga).toHttpUrl().newBuilder()
            .addQueryParameter("sort", "desc")
            .build()
        val series = client.get(detailsUrl, headers = rscHeaders)
            .extractNextJs<JsonObject> { it is JsonObject && "similarSeries" in it }
        return series?.get("similarSeries")
            ?.parseAs<List<MangaDto>>()
            .orEmpty()
            .map { it.toSManga(baseUrl) }
    }

    private suspend fun fetchAllChapters(manga: SManga, details: DetailDto?, existing: List<SChapter>): List<SChapter> {
        if (details == null) return emptyList()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF, true)
        val allChapters = if (existing.isEmpty() && details.totalPages > 1) {
            details.chapters + (2..details.totalPages).flatMap { page ->
                client.get(
                    getMangaUrl(manga).toHttpUrl().newBuilder()
                        .addQueryParameter("sort", "desc")
                        .addQueryParameter("page", page.toString())
                        .build(),
                    headers = rscHeaders,
                ).extractNextJs<DetailDto> { it is JsonObject && "series" in it && "chapters" in it }
                    ?.chapters.orEmpty()
            }
        } else {
            details.chapters
        }
        val fetched = allChapters
            .filter { !(it.isLocked && hideLocked) }
            .map { it.toSChapter(manga) }
        val retained = existing.filter {
            !(hideLocked && it.memo["isLocked"]?.booleanOrNull == true)
        }
        return (fetched + retained)
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
    }

    // ------------------------- Pages -------------------------

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo["isLocked"]?.booleanOrNull == true) {
            throw Exception("This chapter is locked and requires coins to read")
        }
        val response = client.get(getChapterUrl(chapter), rscHeaders)
        val dto = response.extractNextJs<ChapterDetailDto> { it is JsonObject && "chapter" in it }
        return dto?.chapter?.pages.orEmpty().mapIndexedNotNull { index, pageDto ->
            pageDto.imageUrl?.let { Page(index, imageUrl = it.toAbsoluteUrl(baseUrl)) }
        }
    }

    // ------------------------- URL helpers -------------------------

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.memo["slug"]?.string.orEmpty()
        return "$baseUrl/series/comic/$slug"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]!!.string
        val number = chapter.memo["number"]!!.string
        return "$baseUrl/series/comic/$slug/chapter/$number"
    }
}
