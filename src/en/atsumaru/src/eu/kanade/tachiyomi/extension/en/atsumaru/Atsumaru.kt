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

@Source
abstract class Atsumaru :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    private val prefs by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage = getBrowsePage("popular", page) {
        addQueryParameter("timeframe", "daily")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getBrowsePage("recentlyUpdated", page)

    private suspend fun getBrowsePage(path: String, page: Int, block: HttpUrl.Builder.() -> Unit = {}): MangasPage {
        val url = "$baseUrl/api/home2/$path".toHttpUrl().newBuilder().apply {
            addQueryParameter("offset", ((page - 1) * BROWSE_LIMIT).toString())
            addQueryParameter("limit", BROWSE_LIMIT.toString())
            addQueryParameter("types", "Manga,Manwha,Manhua,OEL")
            addQueryParameter("mediums", "Comic")
            block()
            if (adultMode) addQueryParameter("adult", "1")
            if (excludedGenres.isNotEmpty()) addQueryParameter("excludedTags", excludedGenres.joinToString(","))
        }.build()
        val data = client.get(url, Fetch.XHR).parseAs<BrowseMangaDto>()

        return MangasPage(data.items.map { it.toSManga(baseUrl) }, true)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/collections/manga/documents/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query.ifEmpty { "*" })

            val filterBy = mutableListOf<String>()
            filterBy.add("hidden:!=true")

            val includedGenres = mutableListOf<String>()
            val excludedGenres = mutableListOf<String>()
            val includedTags = mutableListOf<String>()
            val excludedTags = mutableListOf<String>()
            val typesList = mutableListOf<String>()
            val statuses = mutableListOf<String>()
            var year: Int? = null
            var minChapters: Int? = null
            var showAdult = false
            var officialTranslation = false
            var sortBy: String? = null

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.state.forEachIndexed { index, state ->
                            when (state.state) {
                                Filter.TriState.STATE_INCLUDE -> includedGenres.add(filter.genreIds[index])
                                Filter.TriState.STATE_EXCLUDE -> excludedGenres.add(filter.genreIds[index])
                            }
                        }
                    }

                    is TagFilters -> {
                        filter.state.forEach { letter ->
                            letter.state.forEachIndexed { index, state ->
                                when (state.state) {
                                    Filter.TriState.STATE_INCLUDE -> includedTags.add(letter.tagIds[index])
                                    Filter.TriState.STATE_EXCLUDE -> excludedTags.add(letter.tagIds[index])
                                }
                            }
                        }
                    }

                    is TypeFilter -> {
                        filter.state.forEachIndexed { index, checkBox ->
                            if (checkBox.state) {
                                typesList.add(filter.ids[index])
                            }
                        }
                    }

                    is StatusFilter -> {
                        filter.state.forEachIndexed { index, checkBox ->
                            if (checkBox.state) {
                                statuses.add(filter.ids[index])
                            }
                        }
                    }

                    is YearFilter -> {
                        if (filter.state.isNotEmpty()) year = filter.state.toIntOrNull()
                    }

                    is MinChaptersFilter -> {
                        if (filter.state.isNotEmpty()) minChapters = filter.state.toIntOrNull()
                    }

                    is SortFilter -> {
                        val selection = filter.state!!
                        sortBy = SortFilter.VALUES[selection.index] + if (selection.ascending) ":asc" else ":desc"
                    }

                    is AdultFilter -> {
                        showAdult = filter.state || adultMode
                    }

                    is OfficialFilter -> {
                        officialTranslation = filter.state
                    }

                    else -> {}
                }
            }

            if (includedGenres.isNotEmpty()) {
                filterBy.add(includedGenres.joinToString(" && ") { "genreIds:=`$it`" })
            }
            if (excludedGenres.isNotEmpty()) {
                filterBy.add("genreIds:!=[${excludedGenres.joinToString(",") { "`$it`" }}]")
            }

            if (includedTags.isNotEmpty()) {
                filterBy.add(includedTags.joinToString(" && ") { "tagIds:=`$it`" })
            }
            if (excludedTags.isNotEmpty()) {
                filterBy.add("tagIds:!=[${excludedTags.joinToString(",") { "`$it`" }}]")
            }

            if (typesList.isNotEmpty()) {
                filterBy.add("type:=[${typesList.joinToString(",") { "`$it`" }}]")
            }

            if (statuses.isNotEmpty()) {
                filterBy.add("status:=[${statuses.joinToString(",") { "`$it`" }}]")
            }

            year?.let {
                filterBy.add("releaseYear:=[$it]")
            }

            minChapters?.let {
                filterBy.add("chapterCount:>=$it")
            }

            if (!showAdult) {
                filterBy.add("isAdult:=$showAdult")
            }

            if (officialTranslation) {
                filterBy.add("officialTranslation:=$officialTranslation")
            }

            filterBy.add("mbContentRating:!=[`Pornographic`]")
            filterBy.add("medium:!=[`Novel`]")
            filterBy.add("views:>0")

            addQueryParameter("filter_by", filterBy.joinToString(" && "))

            sortBy?.let { addQueryParameter("sort_by", it) }

            if (query.isNotEmpty()) {
                addQueryParameter("query_by", "title,englishTitle,otherNames,authors")
                addQueryParameter("query_by_weights", "4,3,2,1")
                addQueryParameter("num_typos", "4,3,2,1")
            }

            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "40")
        }.build()

        val data = client.get(url, Fetch.XHR).parseAs<SearchResultsDto>()

        return MangasPage(data.hits.map { it.document.toSManga(baseUrl) }, data.hasNextPage())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "manga") return null
        val id = url.pathSegments.getOrNull(1) ?: return null

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
            val scanlatorMap = details.scanlators?.associate { it.id to it.name }.orEmpty()
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
                val scores = details.scanlators?.associate { it.id to it.score }.orEmpty()
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

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = fetchMangaPage(manga).recommendations(baseUrl)

    private suspend fun fetchMangaPage(manga: SManga): MangaDto {
        val url = "$baseUrl/api/manga/page".toHttpUrl().newBuilder()
            .addQueryParameter("id", manga.url)
            .build()
        return client.get(url, Fetch.XHR).parseAs<MangaObjectDto>().mangaPage
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, name) = chapter.url.split("/")
        return "$baseUrl/read/$slug/$name"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, name) = chapter.url.split("/")
        val url = "$baseUrl/api/read/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("mangaId", slug)
            .addQueryParameter("chapterId", name)
            .build()

        return client.get(url, Fetch.XHR).parseAs<PageObjectDto>().readChapter.pages.mapIndexed { index, page ->
            val imageUrl = when {
                page.image.startsWith("http") -> page.image
                page.image.startsWith("//") -> "https:${page.image}"
                else -> "$baseUrl/static/${page.image.removePrefix("/").removePrefix("static/")}"
            }
            Page(index, imageUrl = imageUrl.replaceFirst(PROTOCOL_REGEX, "https://cdn."))
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val filters = client.get("$baseUrl/api/explore/availableFilters".toHttpUrl(), Fetch.XHR).parseAs<FilterData>()
        return filters.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = data?.parseAs<FilterData>()?.getFilterList(excludedGenres).orEmpty()

        return FilterList(
            filters + listOf(
                YearFilter(),
                MinChaptersFilter(),
                SortFilter(),
                AdultFilter(adultMode),
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
            summaryOn = "+18"
        }.let(screen::addPreference)

        val genreFilter = getFilterList().firstInstanceOrNull<GenreFilter>()
        val genres = genreFilter?.state.orEmpty()
        val genreIds = genreFilter?.genreIds.orEmpty()

        MultiSelectListPreference(screen.context).apply {
            key = PREF_EXCLUDE_GENRES
            title = "Exclude Genres from Browse"
            entries = genres.map { it.name }.toTypedArray()
            entryValues = genreIds.toTypedArray()
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
                val updated = pref as MultiSelectListPreference

                @Suppress("UNCHECKED_CAST")
                val newSet = newValue as Set<String>

                updateSummary(updated, newSet)
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

        private val PROTOCOL_REGEX = Regex("^https?:?//")
    }
}
