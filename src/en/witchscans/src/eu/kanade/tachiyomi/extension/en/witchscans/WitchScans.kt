package eu.kanade.tachiyomi.extension.en.witchscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class WitchScans :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val rscHeaders: Headers get() = headersBuilder().set("rsc", "1").build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF
            title = "Hide locked chapters"
            summary = "Hide chapters that require coins to read"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF = "pref_hide_locked_chapters"
    }

    // ------------------------- Browse (Popular) -------------------------

    override suspend fun getPopularManga(page: Int): MangasPage = getMangasPage(client.get("$baseUrl/series?sort=popular&page=$page", headers = rscHeaders))

    // ------------------------- Latest -------------------------

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangasPage(client.get("$baseUrl/series?page=$page", headers = rscHeaders))

    private suspend fun getMangasPage(response: Response): MangasPage {
        val dto = response.extractNextJs<BrowseDto> { it is JsonObject && "initialSeries" in it }
        val mangas = dto?.initialSeries?.map { it.toSManga(baseUrl) } ?: emptyList()
        return MangasPage(mangas, dto?.initialHasMore == true)
    }

    // ------------------------- Search -------------------------

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .apply {
                if (query.isNotEmpty()) {
                    addQueryParameter("q", query)
                }
                filters.filterIsInstance<UriQueryFilter>().forEach { it.addToQuery(this) }
                addQueryParameter("page", page.toString())
            }
            .build()
        return getMangasPage(client.get(url, headers = rscHeaders))
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
        val document = client.get(getMangaUrl(manga), headers = rscHeaders).extractNextJs<DetailDto> { it is JsonObject && "series" in it && "chapters" in it }

        val updatedManga = if (fetchDetails) document?.series?.toSManga(baseUrl, manga) ?: manga else manga
        val updatedChapters = if (fetchChapters) fetchAllChapters(manga, document) else chapters

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun fetchAllChapters(manga: SManga, document: DetailDto?): List<SChapter> {
        if (document == null) return emptyList()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF, false)
        val totalPages = document.totalPages.coerceAtLeast(1)
        val extraChapters = if (totalPages > 1) {
            (2..totalPages).map { page ->
                client.get("${getMangaUrl(manga)}?page=$page", headers = rscHeaders)
                    .extractNextJs<DetailDto> { it is JsonObject && "series" in it && "chapters" in it }
                    ?.chapters.orEmpty()
            }.flatten()
        } else {
            emptyList()
        }
        return (document.chapters + extraChapters)
            .filter { !(it.isLocked && hideLocked) }
            .map { it.toSChapter(manga) }
            .sortedByDescending { it.chapter_number }
    }

    // ------------------------- Pages -------------------------

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo["isLocked"]?.jsonPrimitive?.booleanOrNull == true) {
            throw Exception("This chapter is locked and requires coins to read")
        }
        val response = client.get(getChapterUrl(chapter), headers = rscHeaders)
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
        val path = chapter.url.substringBeforeLast("/chapter/")
        val number = chapter.url.substringAfterLast("/")
        return "$baseUrl/series/$path/chapter/$number"
    }
}
