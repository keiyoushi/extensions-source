package eu.kanade.tachiyomi.extension.en.keroscans

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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Keroscans :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences()

    private val showLockedChapters
        get() = preferences.getBoolean(SHOW_LOCKED_CHAPTERS_PREF, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_CHAPTERS_PREF
            title = "Display unpurchased paid chapters"
            summaryOn = "Unpurchased paid chapters will appear."
            summaryOff = "Only free and purchased chapters will appear."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/api/series"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("type", COMIC_TYPES)
            .addQueryParameter("sort", "popular")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE)
            .addQueryParameter("_v", V)
            .build()

        return getMangaList(url)
    }

    // =============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/series"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("type", COMIC_TYPES)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE)
            .addQueryParameter("_v", V)
            .build()

        return getMangaList(url)
    }

    // ============================== Filters ==============================

    override suspend fun fetchFilterData(): JsonElement {
        val (genres, tags) = client.get("$baseUrl/series").extractNextJs<GenresTagsContainerDto>()
            ?: throw Exception("Genre and tags not found")
        if (genres.isEmpty()) throw Exception("Genres not found")
        if (tags.isEmpty()) throw Exception("Tags not found")
        return mapOf(
            "genres" to genres.groupBy({ it.name }, { it.slug }),
            "tags" to tags.groupBy({ it.name }, { it.slug }),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.jsonObject?.get("genres")?.parseAs<Map<String, String>>()
        val tags = data?.jsonObject?.get("tags")?.parseAs<Map<String, String>>()

        return FilterList(
            listOfNotNull(
                genres?.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
                tags?.takeIf { it.isNotEmpty() }?.let(::TagFilter),
                TypeFilter(),
                StatusFilter(),
                OriginFilter(),
                SaleFilter(),
                ChapterFilter(),
                SortFilter(),
            ),
        )
    }

    // ============================== Search ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = client
        .get(url)
        .extractNextJs<SeriesContainerDto>()
        ?.series
        ?.toSManga(baseUrl)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/series"
            .toHttpUrl()
            .newBuilder()
            .also {
                for (filter in filters) {
                    (filter as? QueryParamFilter)?.addQueryParameter(it)
                }
            }
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE)
            .addQueryParameter("_v", V)
            .build()

        return getMangaList(url)
    }

    private suspend fun getMangaList(url: HttpUrl) = with(client.get(url).parseAs<DataDto<List<SeriesSummaryDto>>>()) {
        MangasPage(
            data.map { it.toSManga(baseUrl) },
            meta.hasMore,
        )
    }

    // ========================= Details + Chapters ========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = if (fetchDetails || fetchChapters) {
        val (m, c) = client.get(getMangaUrl(manga)).extractNextJs<SeriesContainerDto>() ?: return SMangaUpdate(manga, chapters)
        val newManga = if (fetchDetails) m.toSManga(baseUrl) else manga
        val newChapters = if (fetchChapters) {
            c
                .asSequence()
                .let { sequence -> if (showLockedChapters) sequence else sequence.filter { it.accessible } }
                .map { it.toSChapter(newManga) }
                .toMutableList()
                .apply { reverse() }
        } else {
            chapters
        }
        SMangaUpdate(newManga, newChapters)
    } else {
        SMangaUpdate(manga, chapters)
    }

    // ============================== Related ===============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client
        .get(getMangaUrl(manga))
        .extractNextJs<SeriesContainerDto>()
        ?.similarSeries
        .orEmpty()
        .map { it.toSManga(getMangaUrl(manga)) }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client
        .get(chapter.url)
        .extractNextJs<ChapterContainerDto>()
        ?.apply {
            if (!isUnlocked) throw Error("Log in via WebView and purchase this chapter to read.")
        }
        ?.chapter
        ?.pages
        .orEmpty()
        .map {
            if (it.isRedacted) throw Error("Log in via WebView and purchase this chapter to read.")
            if (it.isEncrypted) throw Error("Encrypted pages not supported.")
            if (it.hasFragments) throw Error("Pages with fragments not supported.")
            if (it.hasStrips) throw Error("Pages with strips not supported.")
            Page(it.pageNumber - 1, imageUrl = "$baseUrl${it.imageUrl}")
        }

    companion object {
        const val V = "d286975f1e6488600d79273134ab23314eba4419"

        const val PAGE_SIZE = "24"

        // This filters out novels
        const val COMIC_TYPES = "Manga,Manhwa,Manhua,Webtoon"

        const val SHOW_LOCKED_CHAPTERS_PREF = "pref_show_locked_chap"
    }
}
