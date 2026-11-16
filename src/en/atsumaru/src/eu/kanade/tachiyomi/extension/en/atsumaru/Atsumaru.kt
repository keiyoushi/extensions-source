package eu.kanade.tachiyomi.extension.en.atsumaru

import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.random.Random

@Source
abstract class Atsumaru :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    private val prefs by getPreferencesLazy()

    private var randomSeed = 1

    override suspend fun getPopularManga(page: Int): MangasPage = getBrowsePage("popular", page, timeframe = "daily")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getBrowsePage("recentlyUpdated", page)

    private suspend fun getBrowsePage(
        path: String,
        page: Int,
        types: List<String> = BROWSE_TYPES,
        includedTags: List<String> = emptyList(),
        excludedTags: Collection<String> = excludedGenres,
        timeframe: String? = null,
    ): MangasPage {
        val url = "$baseUrl/api/home2/$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("offset", ((page - 1) * BROWSE_LIMIT).toString())
            addQueryParameter("limit", BROWSE_LIMIT.toString())
            if (types.isNotEmpty()) addQueryParameter("types", types.joinToString(","))
            addQueryParameter("mediums", "Comic")
            timeframe?.let { addQueryParameter("timeframe", it) }
            if (adultMode) addQueryParameter("adult", "1")
            if (includedTags.isNotEmpty()) addQueryParameter("includedTags", includedTags.joinToString(","))
            if (excludedTags.isNotEmpty()) addQueryParameter("excludedTags", excludedTags.joinToString(","))
        }.build()
        val data = client.get(url, Fetch.XHR).parseAs<BrowseMangaDto>()

        return MangasPage(data.items.map { it.toSManga(baseUrl) }, data.items.size == BROWSE_LIMIT)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected ?: SortOption.POPULARITY
        val timeframe = filters.firstInstanceOrNull<TimeframeFilter>()?.selected ?: "all"
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val tagFilters = filters.firstInstanceOrNull<TagFilters>()
        val includedGenres = genreFilter?.ids(Filter.TriState.STATE_INCLUDE).orEmpty()
        val filterExcludedGenres = genreFilter?.ids(Filter.TriState.STATE_EXCLUDE).orEmpty()
        val includedTags = tagFilters?.ids(Filter.TriState.STATE_INCLUDE).orEmpty()
        val excludedTags = tagFilters?.ids(Filter.TriState.STATE_EXCLUDE).orEmpty()
        val types = filters.firstInstanceOrNull<TypeFilter>()?.selectedIds.orEmpty()

        val home2Path = sort.home2Path(timeframe)
        if (query.isEmpty() && home2Path != null) {
            return getBrowsePage(
                home2Path,
                page,
                types = types,
                includedTags = includedGenres + includedTags,
                excludedTags = filterExcludedGenres + excludedTags,
                timeframe = timeframe.takeIf { sort.supportsTimeframe },
            )
        }

        val statuses = filters.firstInstanceOrNull<StatusFilter>()?.selectedIds.orEmpty()
        val years = filters.firstInstanceOrNull<YearFilter>()?.state.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
        val minChapters = filters.firstInstanceOrNull<MinChaptersFilter>()?.state?.trim()?.toIntOrNull()
        val maxChapters = filters.firstInstanceOrNull<MaxChaptersFilter>()?.state?.trim()?.toIntOrNull()
        val showAdult = filters.firstInstanceOrNull<AdultFilter>()?.state == true
        val official = filters.firstInstanceOrNull<OfficialFilter>()?.state == true

        val filterBy = buildList {
            includedGenres.forEach { add("genreIds:=`$it`") }
            if (filterExcludedGenres.isNotEmpty()) add("genreIds:!=[${filterExcludedGenres.quoted()}]")
            includedTags.forEach { add("tagIds:=`$it`") }
            if (excludedTags.isNotEmpty()) add("tagIds:!=[${excludedTags.quoted()}]")
            // The site's data spells Manhwa "Manwha"
            if (types.isNotEmpty()) add("type:=[${types.flatMap { if (it == "Manhwa") listOf(it, "Manwha") else listOf(it) }.quoted()}]")
            add("medium:!=[`Novel`]")
            if (statuses.isNotEmpty()) add("status:=[${statuses.quoted()}]")
            if (years.isNotEmpty()) add("releaseYear:=[${years.joinToString(",")}]")
            minChapters?.let { add("chapterCount:>=$it") }
            maxChapters?.let { add("chapterCount:<=$it") }
            if (adultMode) {
                add("isAdult:=true")
            } else {
                if (!showAdult) add("isAdult:=false")
                if (query.isEmpty()) add("mbContentRating:!=[`Pornographic`]")
            }
            if (official) add("officialTranslation:=true")
            if (sort == SortOption.TOP_RATED) add("mbRating:>0")
            if (sort == SortOption.POPULARITY) add("views:>0")
            add("hidden:!=true")
        }

        val url = "$baseUrl/collections/manga/documents/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query.ifEmpty { "*" })
            addQueryParameter("query_by", "title,englishTitle,otherNames,authors,acronyms")
            addQueryParameter("query_by_weights", "4,3,2,2,1")
            addQueryParameter("num_typos", "4,3,2,1,0")
            addQueryParameter("prefix", "true,true,true,true,false")
            addQueryParameter("include_fields", "id,title,posterMedium")
            addQueryParameter("filter_by", filterBy.joinToString(" && "))
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", BROWSE_LIMIT.toString())
            if (query.isNotEmpty()) {
                addQueryParameter("infix", "off,off,fallback,off,off")
            } else {
                if (sort == SortOption.RANDOM && page == 1) randomSeed = Random.nextInt(1, Int.MAX_VALUE)
                addQueryParameter("sort_by", sort.typesenseSort(randomSeed))
            }
        }.build()

        val data = client.get(url, Fetch.XHR).parseAs<SearchResultsDto>()

        return MangasPage(data.hits.map { it.document.toSManga(baseUrl) }, data.hasNextPage())
    }

    private fun List<String>.quoted() = joinToString(",") { "`$it`" }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) !in listOf("manga", "read")) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null

        return getMangaUpdate(SManga.create().apply { this.url = id }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        // Details carry the scanlator names the chapter list needs
        val detailsDeferred = async { fetchMangaPage(manga) }
        val chaptersDeferred = async {
            if (fetchChapters) {
                val url = "$baseUrl/api/manga/allChapters".toHttpUrl().newBuilder()
                    .addQueryParameter("mangaId", manga.url)
                    .build()
                client.get(url, Fetch.XHR).parseAs<AllChaptersDto>()
            } else {
                null
            }
        }

        val details = detailsDeferred.await()
        val chaptersDto = chaptersDeferred.await()

        val updatedChapters = chaptersDto?.let { dto ->
            val scanlatorMap = details.scanlators.associate { it.id to it.name }
            val blacklist = scanlatorBlacklist
            val filteredChapters = if (blacklist.isEmpty()) {
                dto.chapters
            } else {
                dto.chapters.filter { chapter ->
                    val name = chapter.scanlationMangaId?.let(scanlatorMap::get)
                    name == null || name.trim().lowercase() !in blacklist
                }
            }
            val chapterDtos = if (deduplicateChapters) {
                val scores = details.scanlators.associate { it.id to it.score }
                filteredChapters.groupBy { it.number }.map { (_, uploads) ->
                    uploads.maxWith(
                        compareBy<ChapterDto> { scores[it.scanlationMangaId] ?: Int.MIN_VALUE }
                            .thenBy { it.dateUploadMillis() },
                    )
                }
            } else {
                filteredChapters
            }
            chapterDtos.map {
                it.toSChapter(manga.url, it.scanlationMangaId?.let { id -> scanlatorMap[id] })
            }.sortedWith(
                compareByDescending<SChapter> { it.chapter_number }
                    .thenBy { it.scanlator }
                    .thenByDescending { it.date_upload },
            )
        } ?: chapters

        SMangaUpdate(details.toSManga(baseUrl), updatedChapters)
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = fetchMangaPage(manga).relatedManga(baseUrl)

    private suspend fun fetchMangaPage(manga: SManga): MangaPageDto {
        val url = "$baseUrl/api/manga/page".toHttpUrl().newBuilder()
            .addQueryParameter("id", manga.url)
            .build()
        return client.get(url, Fetch.XHR).parseAs<MangaObjectDto>().mangaPage
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, name) = chapter.url.split("/")
        val url = "$baseUrl/api/read/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("mangaId", slug)
            .addQueryParameter("chapterId", name)
            .build()

        return client.get(url, Fetch.XHR).parseAs<PageObjectDto>().readChapter.pages.mapIndexed { index, page ->
            Page(index, imageUrl = cdnUrl(baseUrl, page.image)!!)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val filters = client.get("$baseUrl/api/explore/availableFilters", Fetch.XHR).parseAs<FilterData>()
        return filters.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = data?.parseAs<FilterData>()?.getFilterList(excludedGenres).orEmpty()

        return FilterList(
            filters + listOf(
                YearFilter(),
                MinChaptersFilter(),
                MaxChaptersFilter(),
                SortFilter(),
                TimeframeFilter(),
                Filter.Header("Hot Updates and the sorts below it, and Popularity with a timeframe, ignore status, year, chapter, adult and official filters"),
                AdultFilter(),
                OfficialFilter(),
            ),
        )
    }

    private val adultMode: Boolean
        get() = prefs.getBoolean(PREF_SHOW_18, false)

    private val excludedGenres: Set<String>
        get() = prefs.getStringSet(PREF_EXCLUDE_GENRES, emptySet()).orEmpty()

    private val deduplicateChapters: Boolean
        get() = prefs.getBoolean(PREF_DEDUPLICATE_CHAPTERS, false)

    private val scanlatorBlacklist: Set<String>
        get() = prefs.getString(PREF_SCANLATOR_BLACKLIST, "").orEmpty()
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_18
            title = "Toggle adult mode"
            summaryOff = "Safe (default)"
            summaryOn = "18+: adult titles only"
        }.let(screen::addPreference)

        val genres = getFilterList().firstInstanceOrNull<GenreFilter>()?.state.orEmpty()

        MultiSelectListPreference(screen.context).apply {
            key = PREF_EXCLUDE_GENRES
            title = "Exclude Genres from Browse"
            entries = genres.map { it.name }.toTypedArray()
            entryValues = genres.map { it.id }.toTypedArray()
            setDefaultValue(emptySet<String>())
            setEnabled(genres.isNotEmpty())

            fun updateSummary(pref: MultiSelectListPreference, selected: Set<String>?) {
                pref.summary = if (selected.isNullOrEmpty()) {
                    "None"
                } else {
                    val entryMap = pref.entryValues.zip(pref.entries).toMap()
                    selected.joinToString { entryMap[it] ?: it }
                }
            }

            updateSummary(this, prefs.getStringSet(PREF_EXCLUDE_GENRES, emptySet()))

            setOnPreferenceChangeListener { pref, newValue ->
                @Suppress("UNCHECKED_CAST")
                updateSummary(pref as MultiSelectListPreference, newValue as Set<String>)
                true
            }
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_DEDUPLICATE_CHAPTERS
            title = "Deduplicate chapters"
            summary = "Show one upload per chapter, from the highest rated group that has it"
            setDefaultValue(false)
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_SCANLATOR_BLACKLIST
            title = "Excluded scanlators"
            summary = "Comma-separated group names to exclude from the chapter list"
            dialogTitle = "Excluded scanlators"
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_SHOW_18 = "pref_18_mode"
        private const val PREF_EXCLUDE_GENRES = "pref_exclude_genres"
        private const val PREF_DEDUPLICATE_CHAPTERS = "pref_deduplicate_chapters"
        private const val PREF_SCANLATOR_BLACKLIST = "pref_scanlator_blacklist"
        private const val BROWSE_LIMIT = 40
        private val BROWSE_TYPES = listOf("Manga", "Manwha", "Manhua", "OEL")
    }
}
