package eu.kanade.tachiyomi.extension.en.toonkotsu

import android.util.Base64
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class Toonkotsu :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy {
        if (contains(PREF_DEDUPLICATE_CHAPTERS)) {
            val editor = edit().remove(PREF_DEDUPLICATE_CHAPTERS)
            if (getBoolean(PREF_DEDUPLICATE_CHAPTERS, false)) editor.putString(PREF_CHAPTER_LIST, ONE_PER_CHAPTER)
            editor.apply()
        }
    }

    private val showAdult get() = preferences.getBoolean(PREF_ADULT, false)

    private val showSpoilerTags get() = preferences.getBoolean(PREF_SPOILER_TAGS, false)

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::decryptImage)

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, "popular")

    // The site's "Latest Updates" feed, one item per uploaded chapter
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/cdn/v1/latest".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "70")
            if (!showAdult) addQueryParameter("sfw", "1")
            applyContentPreferences()
        }.build()
        val data = client.get(url).parseAs<LatestDto>()
        val mangas = data.items.map { it.series }.distinctBy { it.hid }.map { it.toSManga(baseUrl, showSpoilerTags) }
        return MangasPage(mangas, data.hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = browse(page, "relevance") {
        if (query.isNotBlank()) addQueryParameter("q", query.trim())
        val included = mutableListOf<String>()
        val excluded = mutableListOf<String>()
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    setQueryParameter("sort", filter.value)
                    setQueryParameter("order", filter.order)
                }
                is SelectFilter -> if (filter.value.isNotEmpty()) setQueryParameter(filter.param, filter.value)
                is CheckBoxGroup -> filter.values.takeIf { it.isNotEmpty() }?.let { addQueryParameter(filter.param, it.joinToString(",")) }
                is TagGroup -> filter.state.forEach {
                    if (it.isIncluded()) included += it.name
                    if (it.isExcluded()) excluded += it.name
                }
                is NumberFilter -> filter.state.trim().toIntOrNull()?.let { addQueryParameter(filter.param, it.toString()) }
                else -> {}
            }
        }
        if (included.isNotEmpty()) addQueryParameter("genres", included.joinToString(","))
        if (excluded.isNotEmpty()) addQueryParameter("exclude", excluded.joinToString(","))
    }

    private suspend fun browse(page: Int, sort: String, params: HttpUrl.Builder.() -> Unit = {}): MangasPage {
        val url = "$baseUrl/cdn/v1/browse".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", sort)
            addQueryParameter("order", "desc")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "42")
            if (!showAdult) addQueryParameter("sfw", "1")
            params()
            applyContentPreferences()
        }.build()
        val data = client.get(url).parseAs<BrowseDto>()
        return MangasPage(data.items.map { it.toSManga(baseUrl, showSpoilerTags) }, data.hasNextPage)
    }

    // Like the site's own language and tag settings: a search's own language wins, excluded tags add up and match any
    private fun HttpUrl.Builder.applyContentPreferences() {
        val current = build()
        val languages = preferences.getStringSet(PREF_LANGUAGES, emptySet())!!
        if (current.queryParameter("originalLanguage") == null && languages.isNotEmpty()) {
            addQueryParameter("originalLanguage", languages.sorted().joinToString(","))
        }
        val excludedTags = commaList(PREF_EXCLUDED_TAGS, "")
        if (excludedTags.isNotEmpty()) {
            val excluded = current.queryParameter("exclude")?.split(',').orEmpty() + excludedTags
            setQueryParameter("exclude", excluded.distinct().joinToString(","))
            setQueryParameter("excludeMode", "any")
        }
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!url.isSiteUrl()) return MangasPage(emptyList(), false)
        val name = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return super.getMangasByUrl(url, page)
        return when (url.pathSegments.first()) {
            "author" -> browse(page, "year") { addQueryParameter("author", name) }
            "tag" -> browse(page, "score") { addQueryParameter("genres", name) }
            "year" -> browse(page, "score") { addQueryParameter("year", name) }
            "group" -> {
                val groupUrl = "$baseUrl/cdn/v1/groups".toHttpUrl().newBuilder().apply {
                    addPathSegment(name)
                    addQueryParameter("page", page.toString())
                    addQueryParameter("limit", "42")
                    if (!showAdult) addQueryParameter("sfw", "1")
                }.build()
                val data = client.get(groupUrl).parseAs<GroupPageDto>()
                MangasPage(data.items.map { it.toSManga(baseUrl, showSpoilerTags) }, data.hasNextPage)
            }
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.isSiteUrl() || url.pathSegments.firstOrNull() != "title") return null
        val hid = url.pathSegments.getOrNull(1)?.let { HID_REGEX.find(it) }?.value ?: return null
        val manga = SManga.create().apply { this.url = "/title/$hid" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    private fun HttpUrl.isSiteUrl() = host.removePrefix("www.") == baseUrl.toHttpUrl().host.removePrefix("www.")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val hid = getMangaUrl(manga).toHttpUrl().pathSegments[1]
        val mangabaka = if (fetchDetails) async { fetchMangabaka(hid) } else null
        val data = client.get("$baseUrl/cdn/v1/series/$hid").parseAs<SeriesResponse>()
        // Without details the description stays null, which the app keeps as stored
        val details = mangabaka?.let { data.series.toSMangaDetails(baseUrl, showSpoilerTags, showAdult, it.await()) }
            ?: data.series.toSManga(baseUrl, showSpoilerTags)
        // One per chapter ranks by likes, a request of its own
        val needsLikes = preferences.getString(PREF_CHAPTER_LIST, ONE_PER_GROUP) == ONE_PER_CHAPTER
        SMangaUpdate(details, if (fetchChapters || !needsLikes) chapterList(data, manga.url) else chapters)
    }

    // The site's Details tab and External Links; `{"available":false}` when it has none
    private suspend fun fetchMangabaka(hid: String): MangabakaDto = client.get("$baseUrl/api/v1/series/$hid/mangabaka").parseAs()

    private suspend fun chapterList(data: SeriesResponse, mangaUrl: String): List<SChapter> {
        val allUploads = data.chapters.flatMap { it.withAlts() }
        // The site splits rows by volume when 3+ chapter numbers span several volumes
        val byVolume = allUploads.filter { it.n != null && it.numericVolume != null }
            .groupBy({ it.n }, { it.numericVolume })
            .count { (_, volumes) -> volumes.distinct().size > 1 } >= 3
        val hiddenGroups = commaList(PREF_HIDDEN_GROUPS, "")
        val uploads = allUploads.filterNot { upload -> hiddenGroups.any { upload.isFromSource(it) || upload.isFromGroup(it) } }
        val preferredSources = commaList(PREF_PREFERRED_SOURCES, "")
        val sourceRank = { upload: ChapterDto -> preferredSources.rankOf(upload::isFromSource) }
        // One upload per row, in the site's order; preferred sources sit where the site's own preferred source does
        val picks = uploads.sortedWith(compareBy({ it.partial }, sourceRank, { it.parentId != null }))
            .distinctBy { it.row(byVolume) }
            .toSet()
        val chapterList = when (preferences.getString(PREF_CHAPTER_LIST, ONE_PER_GROUP)) {
            ONE_PER_GROUP -> {
                val complete = picks.filterNot { it.partial }.mapTo(HashSet()) { it.row(byVolume, byGroup = false) }
                // The site hides a partial-only row when another group has the full chapter
                uploads.filter { it in picks && (!it.partial || it.row(byVolume, byGroup = false) !in complete) }
            }
            ONE_PER_CHAPTER -> {
                val preferredGroups = commaList(PREF_PREFERRED_GROUPS, DEFAULT_PREFERRED_GROUPS)
                // The site's per-chapter likes, its only user rank for uploads
                val likes = fetchLikes(data.series.id)
                uploads.filter { it in picks }
                    .groupBy { it.row(byVolume, byGroup = false) }
                    .map { (_, same) ->
                        same.sortedWith(
                            compareBy<ChapterDto>({ it.partial }, { preferredGroups.rankOf(it::isFromGroup) })
                                .thenByDescending { likes[it.id.toString()] ?: 0 },
                        ).first()
                    }
            }
            else -> {
                val rows = uploads.groupBy { it.row(byVolume, byGroup = false) }.values
                val copies = rows.flatMapTo(HashSet()) { row ->
                    val kept = mutableListOf<ChapterDto>()
                    row.sortedWith(compareByDescending<ChapterDto> { it in picks }.thenByDescending { it.mb ?: 0.0 }).filter { upload ->
                        (upload !in picks && kept.any(upload::isCopyOf)).also { if (!it) kept += upload }
                    }
                }
                uploads.filterNot { it in copies }
            }
        }
        val nameFormat = preferences.getString(PREF_CHAPTER_NAMES, NAME_LABEL)!!
        return chapterList.map { it.toSChapter(mangaUrl, withSourceCode = it !in picks, nameFormat) }
    }

    private suspend fun fetchLikes(seriesId: Long): Map<String, Int> {
        val url = "$baseUrl/api/manga/chapter-likes".toHttpUrl().newBuilder()
            .addQueryParameter("seriesId", seriesId.toString())
            .build()
        return client.get(url).parseAs<LikesDto>().counts
    }

    private fun commaList(key: String, default: String) = preferences.getString(key, default).orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

    private fun List<String>.rankOf(matches: (String) -> Boolean) = indexOfFirst(matches).takeIf { it >= 0 } ?: size

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val hid = getMangaUrl(manga).toHttpUrl().pathSegments[1]
        return client.get("$baseUrl/cdn/v1/series/$hid").parseAs<SeriesResponse>().recs.map { it.toSManga(baseUrl, showSpoilerTags) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val id = chapterUrl.toHttpUrl().pathSegments.last()
        return fetchChapter(id, chapterUrl).chapter.pages.mapIndexed { index, page ->
            Page(index, imageUrl = "$baseUrl${page.src}#${page.k}.${page.n}")
        }
    }

    private suspend fun fetchChapter(id: String, chapterUrl: String, unlocked: Boolean = false): ChapterResponse {
        val unlocksBefore = unlocks
        val response = client.get("$baseUrl/api/manga/chapter/$id", ensureSuccess = false)
        if (response.isSuccessful) return response.parseAs()
        if (response.code != 429) {
            response.close()
            throw HttpException(response.code)
        }
        val limit = response.parseAs<LimitDto>()
        if (unlocked || limit.code != "manga-limit" || limit.reason != "verify") {
            throw Exception(limit.error ?: "HTTP 429")
        }
        unlock(chapterUrl, unlocksBefore)
        return fetchChapter(id, chapterUrl, unlocked = true)
    }

    private val unlockMutex = Mutex()

    @Volatile
    private var unlocks = 0

    // Site's "Confirm you're human" reader check
    private suspend fun unlock(chapterUrl: String, unlocksBefore: Int) = unlockMutex.withLock {
        if (unlocks != unlocksBefore) return@withLock
        val token = getTurnstileToken(url = chapterUrl, siteKey = TURNSTILE_SITE_KEY)
        client.post("$baseUrl/api/manga/unlock", body = UnlockBody(token).toJsonRequestBody()).close()
        unlocks++
    }

    private fun decryptImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment == null || !request.url.encodedPath.startsWith("/cdn/e/")) return chain.proceed(request)

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        val key = Base64.decode(fragment.substringBefore("."), Base64.URL_SAFE)
        val nonce = Base64.decode(fragment.substringAfter("."), Base64.URL_SAFE)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(nonce.copyOf(16)))
        }
        val body = response.body.source().cipherSource(cipher).buffer()
            .asResponseBody(response.body.contentType(), response.body.contentLength())
        return response.newBuilder().body(body).build()
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/cdn/v1/facets?sfw=0").parseAs<FacetsDto>().toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val facets = data?.parseAs<FacetsDto>()
        val tags = facets?.genres.orEmpty()
            .filterNot { it.name in HIDDEN_TAGS || (!showAdult && (it.isAdult || it.name in ADULT_TAGS)) }
        val (themes, genres) = tags.partition { it.category != null }
        return FilterList(
            listOfNotNull(
                SortFilter(),
                facets?.let { TypeFilter(it.types.map(FacetDto::name)) },
                StatusFilter(),
                DemographicFilter(),
                CountryFilter(),
                facets?.let { GenreFilter(genres.map(FacetDto::name)) },
                facets?.let { ThemeFilter(themes.map(FacetDto::name)) },
                facets?.let { GenreModeFilter() },
                facets?.let { ExcludeModeFilter() },
                NumberFilter("Year from", "yearFrom"),
                NumberFilter("Year to", "yearTo"),
                NumberFilter("Chapters from", "chaptersMin"),
                NumberFilter("Chapters to", "chaptersMax"),
            ),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ADULT
            title = "Show 18+ content"
            summary = "Same as the 18+ option in the site's content rating menu"
            setDefaultValue(false)
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = PREF_LANGUAGES
            title = "Original languages"
            summary = "Only show titles from these countries in Popular, Latest and search, like the site's language setting. None selected shows all"
            entries = arrayOf("Japan", "Korea", "China", "Western")
            entryValues = arrayOf("ja", "ko", "zh", "en")
            setDefaultValue(emptySet<String>())
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_EXCLUDED_TAGS
            title = "Excluded tags"
            summary = "Tag names, comma-separated, as in the genre filter. Titles with any of them are left out of Popular, Latest and search, like the site's tag setting"
            dialogTitle = "Excluded tags"
            setDefaultValue("")
        }.let(screen::addPreference)

        val preferredGroups = EditTextPreference(screen.context).apply {
            key = PREF_PREFERRED_GROUPS
            title = "Preferred groups"
            summary = "Group names, comma-separated. One per chapter shows the first listed group that has a complete upload. " +
                "Default: $DEFAULT_PREFERRED_GROUPS, the site's official releases"
            dialogTitle = "Preferred groups"
            setDefaultValue(DEFAULT_PREFERRED_GROUPS)
            setEnabled(preferences.getString(PREF_CHAPTER_LIST, ONE_PER_GROUP) == ONE_PER_CHAPTER)
        }

        ListPreference(screen.context).apply {
            key = PREF_CHAPTER_LIST
            title = "Chapter list"
            entries = arrayOf("All versions, like the site's version picker", "One per group, like the site's list", "One per chapter")
            entryValues = arrayOf(ALL_UPLOADS, ONE_PER_GROUP, ONE_PER_CHAPTER)
            summary = "%s"
            setDefaultValue(ONE_PER_GROUP)
            setOnPreferenceChangeListener { _, newValue ->
                preferredGroups.setEnabled(newValue == ONE_PER_CHAPTER)
                true
            }
        }.let(screen::addPreference)

        screen.addPreference(preferredGroups)

        EditTextPreference(screen.context).apply {
            key = PREF_HIDDEN_GROUPS
            title = "Hidden groups and sources"
            summary = "Group names or source codes, comma-separated. Their uploads are left out of the chapter list"
            dialogTitle = "Hidden groups and sources"
            setDefaultValue("")
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PREFERRED_SOURCES
            title = "Preferred sources"
            summary = "Source codes, comma-separated, as shown in parentheses in All versions. Picks which copy of a chapter a group shows"
            dialogTitle = "Preferred sources"
            setDefaultValue("")
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_CHAPTER_NAMES
            title = "Chapter names"
            entries = arrayOf(
                "As on the site (e.g. 'Chapter 25', 'Finale')",
                "With volume (e.g. 'Vol.3 Chapter 25', 'Vol.3 Finale')",
                "With volume and number (e.g. 'Vol.3 Chapter 25', 'Vol.3 Ch.26 Finale')",
            )
            entryValues = arrayOf(NAME_LABEL, NAME_VOLUME, NAME_VOLUME_NUMBER)
            summary = "%s"
            setDefaultValue(NAME_LABEL)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SPOILER_TAGS
            title = "Show spoiler tags"
            summary = "Include tags the site marks as spoilers in the genres"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_ADULT = "show_hentai"
        private const val PREF_DEDUPLICATE_CHAPTERS = "pref_deduplicate_chapters"
        private const val PREF_SPOILER_TAGS = "pref_spoiler_tags"
        private const val PREF_CHAPTER_NAMES = "pref_chapter_names"
        private const val PREF_LANGUAGES = "pref_original_languages"
        private const val PREF_EXCLUDED_TAGS = "pref_excluded_tags"
        private const val PREF_CHAPTER_LIST = "pref_chapter_list"
        private const val PREF_PREFERRED_SOURCES = "pref_preferred_sources"
        private const val PREF_PREFERRED_GROUPS = "pref_preferred_groups"
        private const val PREF_HIDDEN_GROUPS = "pref_hidden_groups"
        private const val DEFAULT_PREFERRED_GROUPS = "Official"
        private const val ALL_UPLOADS = "all"
        private const val ONE_PER_GROUP = "group"
        private const val ONE_PER_CHAPTER = "chapter"
        private const val TURNSTILE_SITE_KEY = "0x4AAAAAAFQpQeG5qpmLQtf9"
        private val HID_REGEX = Regex("""^[a-z][0-9a-z]{4}(?=-|$)""")
    }
}
