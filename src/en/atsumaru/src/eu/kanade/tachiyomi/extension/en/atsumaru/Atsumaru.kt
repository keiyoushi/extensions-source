package eu.kanade.tachiyomi.extension.en.atsumaru

import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Source
abstract class Atsumaru :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    override fun Headers.Builder.configureHeaders() = apply {
        add("Accept", "*/*")
        add("Content-Type", "application/json")
    }

    private val prefs by getPreferencesLazy()

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * BROWSE_LIMIT
        val data = client.get(
            "$baseUrl/api/home2/popular?offset=$offset&limit=$BROWSE_LIMIT" +
                "&types=Manga,Manwha,Manhua,OEL&mediums=Comic&timeframe=daily${get18Mode()}${excludedGenresQuery()}",
        ).parseAs<BrowseMangaDto>()

        return MangasPage(data.items.map { it.toSManga(baseUrl) }, true)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * BROWSE_LIMIT
        val data = client.get(
            "$baseUrl/api/home2/recentlyUpdated?offset=$offset&limit=$BROWSE_LIMIT" +
                "&types=Manga,Manwha,Manhua,OEL&mediums=Comic${get18Mode()}${excludedGenresQuery()}",
        ).parseAs<BrowseMangaDto>()

        return MangasPage(data.items.map { it.toSManga(baseUrl) }, true)
    }

    // =============================== Search ===============================

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
            var sortBy = ""

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
                        val direction = if (filter.state!!.ascending) "asc" else "desc"
                        sortBy = SortFilter.VALUES[filter.state!!.index] + ':' + direction
                    }

                    is AdultFilter -> {
                        showAdult = filter.state || get18Mode().isNotEmpty()
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

            filterBy.add("(mbContentRating:=[`Safe`,`Suggestive`,`Erotica`] || mbContentRating:!=*)")
            filterBy.add("medium:!=[`Novel`]")
            filterBy.add("views:>0")

            addQueryParameter("filter_by", filterBy.joinToString(" && "))

            if (sortBy.isNotEmpty()) {
                addQueryParameter("sort_by", sortBy)
            }

            if (query.isNotEmpty()) {
                addQueryParameter("query_by", "title,englishTitle,otherNames,authors")
                addQueryParameter("query_by_weights", "4,3,2,1")
                addQueryParameter("num_typos", "4,3,2,1")
            }

            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "40")
        }.build()

        val body = client.get(url).body.string()

        return if (body.contains("\"hits\"")) {
            val data = body.parseAs<SearchResultsDto>()
            MangasPage(data.hits.map { it.document.toSManga(baseUrl) }, data.hasNextPage())
        } else {
            val data = body.parseAs<BrowseMangaDto>()
            MangasPage(data.items.map { it.toSManga(baseUrl) }, true)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "manga") return null
        val id = url.pathSegments.getOrNull(1) ?: return null

        return fetchMangaUpdate(
            SManga.create().apply { this.url = id },
            emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga.apply { initialized = true }
    }

    // =============================== Filters ===============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val filters = client.get("$baseUrl/api/explore/availableFilters").parseAs<FilterData>()
        return filters.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val excludedGenres = prefs.getStringSet(PREF_EXCLUDE_GENRES, emptySet()).orEmpty()
        val filters = data?.parseAs<FilterData>()?.getFilterList(excludedGenres).orEmpty()

        return FilterList(
            filters + listOf(
                YearFilter(),
                MinChaptersFilter(),
                SortFilter(),
                AdultFilter(get18Mode().isNotEmpty()),
                OfficialFilter(),
            ),
        )
    }

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async {
            if (fetchDetails || fetchChapters) {
                client.get("$baseUrl/api/manga/page?id=${manga.url}")
                    .parseAs<MangaObjectDto>()
                    .mangaPage
            } else {
                null
            }
        }
        val chaptersDeferred = async {
            if (fetchChapters) {
                client.get("$baseUrl/api/manga/allChapters?mangaId=${manga.url}")
                    .parseAs<AllChaptersDto>()
            } else {
                null
            }
        }

        val details = detailsDeferred.await()
        val chaptersDto = chaptersDeferred.await()

        val updatedManga = if (fetchDetails && details != null) {
            details.toSManga(baseUrl)
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters && chaptersDto != null) {
            val scanlatorMap = details?.scanlators?.associate { it.id to it.name }.orEmpty()
            chaptersDto.chapters.map {
                it.toSChapter(manga.url, it.scanlationMangaId?.let { id -> scanlatorMap[id] })
            }.sortedWith(
                compareByDescending<SChapter> { it.chapter_number }
                    .thenBy { it.scanlator }
                    .thenByDescending { it.date_upload },
            )
        } else {
            chapters
        }

        SMangaUpdate(updatedManga, updatedChapters)
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get("$baseUrl/api/manga/page?id=${manga.url}")
        .parseAs<MangaObjectDto>()
        .mangaPage
        .recommendations(baseUrl)

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, name) = chapter.url.split("/")
        return "$baseUrl/read/$slug/$name"
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, name) = chapter.url.split("/")
        val url = "$baseUrl/api/read/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("mangaId", slug)
            .addQueryParameter("chapterId", name)
            .build()

        return client.get(url).parseAs<PageObjectDto>().readChapter.pages.mapIndexed { index, page ->
            val imageUrl = when {
                page.image.startsWith("http") -> page.image
                page.image.startsWith("//") -> "https:${page.image}"
                else -> "$baseUrl/static/${page.image.removePrefix("/").removePrefix("static/")}"
            }
            Page(index, imageUrl = imageUrl.replaceFirst(PROTOCOL_REGEX, "https://"))
        }
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            set("Accept", "image/avif,image/webp,*/*")
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    private fun get18Mode(): String {
        val isEnabled = prefs.getBoolean(PREF_SHOW_18, false)
        return if (isEnabled) "&adult=1" else ""
    }

    private fun excludedGenresQuery(): String {
        val ids = prefs.getStringSet(PREF_EXCLUDE_GENRES, emptySet()).orEmpty()
        if (ids.isEmpty()) return ""
        return "&excludedTags=${ids.joinToString(",")}"
    }

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
    }

    companion object {
        private const val PREF_SHOW_18 = "pref_18_mode"
        private const val PREF_EXCLUDE_GENRES = "pref_exclude_genres"
        private const val BROWSE_LIMIT = 40

        private val PROTOCOL_REGEX = Regex("^https?:?//")
    }
}
