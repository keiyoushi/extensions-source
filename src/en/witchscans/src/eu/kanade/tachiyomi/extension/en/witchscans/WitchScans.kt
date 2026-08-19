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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class WitchScans :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

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

    override suspend fun getPopularManga(page: Int): MangasPage = getMangasPage(client.get("$baseUrl/series?sort=popular&page=$page"))

    // ------------------------- Latest -------------------------

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangasPage(client.get("$baseUrl/series?page=$page"))

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
        return getMangasPage(client.get(url))
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
        val document = client.get(getMangaUrl(manga)).extractNextJs<DetailDto> { it is JsonObject && "series" in it && "chapters" in it }

        val updatedManga = if (fetchDetails) document?.series?.toSManga(baseUrl, manga) ?: manga else manga
        val updatedChapters = if (fetchChapters) fetchAllChapters(manga, document) else chapters

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // ponytail: detail page cuma serve 100 chapters per page (totalPages dari response),
    // jadi loop semua halaman chapter & gabung. Kalau totalPages nggak ada, ambil 1 halaman.
    private suspend fun fetchAllChapters(manga: SManga, document: DetailDto?): List<SChapter> {
        if (document == null) return emptyList()
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF, false)
        val totalPages = document.totalPages.coerceAtLeast(1)
        val pages = (1..totalPages).map { page ->
            val dto = client.get("${getMangaUrl(manga)}?page=$page")
                .extractNextJs<DetailDto> { it is JsonObject && "series" in it && "chapters" in it }
            dto?.chapters.orEmpty()
        }
        return pages.flatten()
            .filter { !(it.isLocked && hideLocked) }
            .map { it.toSChapter(manga) }
            .sortedByDescending { it.chapter_number }
    }

    // ------------------------- Pages -------------------------

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo["isLocked"]?.jsonPrimitive?.booleanOrNull == true) {
            throw Exception("This chapter is locked and requires coins to read")
        }
        val response = client.get(getChapterUrl(chapter))
        val dto = response.extractNextJs<ChapterDetailDto> { it is JsonObject && "chapter" in it }
        return dto?.chapter?.pages.orEmpty().mapIndexedNotNull { index, pageDto ->
            pageDto.imageUrl?.let { Page(index, imageUrl = it.toAbsoluteUrl(baseUrl)) }
        }
    }

    // ------------------------- URL helpers -------------------------

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.memo["slug"]?.string ?: manga.url.substringAfterLast('/')
        return "$baseUrl/series/comic/$slug"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val memo = chapter.memo
        val type = memo["type"]?.string ?: "comic"
        val slug = memo["slug"]?.string ?: ""
        val number = memo["number"]?.string ?: ""
        return "$baseUrl/series/$type/$slug/chapter/$number"
    }
}
