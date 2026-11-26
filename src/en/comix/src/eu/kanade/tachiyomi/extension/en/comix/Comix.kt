package eu.kanade.tachiyomi.extension.en.comix

import android.content.SharedPreferences
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
import keiyoushi.annotation.Source
import keiyoushi.lib.cronet.cronet
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@Source
abstract class Comix :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy {
        edit().apply {
            remove(LEGACY_SOLVE_CAPTCHA_PREF)
            if (getString(PREF_POSTER_QUALITY, null) == "small") putString(PREF_POSTER_QUALITY, "medium")
        }.apply()
    }

    private val tagIdCache = object : LinkedHashMap<String, List<String>>(
        TAG_ID_CACHE_SIZE,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > TAG_ID_CACHE_SIZE
    }

    private val siteKeys = SiteKeys({ baseUrl }) { url, fetch -> client.get(url, fetch).body.string() }

    private val captchaMutex = Mutex()
    private val captchaSolves = AtomicInteger()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::altHostFallback)
        .rateLimit(2) { it.host == baseUrlHost && it.pathSegments.first() !in STATIC_PATHS }
        .rateLimit(2) { it.altHosts().isNotEmpty() }
        .rateLimit(5)
        .cronet()

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    private val apiUrl get() = "$baseUrl/api/v1".toHttpUrl()

    private class RequestPacer(private val minGapMs: Long, private val maxGapMs: Long) {
        private var next = TimeSource.Monotonic.markNow()

        suspend fun waitForSlot() {
            val wait = synchronized(this) {
                val start = maxOf(TimeSource.Monotonic.markNow(), next)
                next = start + Random.nextLong(minGapMs, maxGapMs).milliseconds
                -start.elapsedNow()
            }
            if (wait.isPositive()) delay(wait)
        }
    }

    private fun altHostFallback(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val altHosts = request.url.altHosts()
        if (altHosts.isEmpty()) return chain.proceed(request)

        val urls = listOf(request.url) + altHosts.mapIndexed { i, host ->
            request.url.newBuilder()
                .host(host)
                .setQueryParameter("r", (i + 1).toString())
                .build()
        }
        var failed: Response? = null
        var error: IOException? = null
        for (url in urls) {
            failed?.close()
            try {
                val response = chain.proceed(request.newBuilder().url(url).build())
                if (response.isSuccessful) return response
                failed = response
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                error = e
            }
        }
        return failed ?: throw error!!
    }

    private fun HttpUrl.altHosts(): List<String> = fragment
        ?.takeIf { it.startsWith(ALT_HOSTS_FRAGMENT) }
        ?.removePrefix(ALT_HOSTS_FRAGMENT)
        ?.split(',')
        .orEmpty()

    private val apiHeaders
        get() = headersBuilder()
            .set("Accept", "application/json")
            .set("X-Requested-With", "XMLHttpRequest")
            .set("X-Rt", "0")
            .build()

    private suspend inline fun <reified T> readApi(url: HttpUrl, from: String): T {
        val (response, key) = callApi(url, from)
        val encrypted = response.header("x-enc") == ENCRYPTED_BODY_VERSION
        return response.parseAs<T> { text ->
            if (encrypted) key.decrypt(text.parseAs<EncryptedBody>().e) else text
        }
    }

    private suspend fun callApi(url: HttpUrl, from: String, retried: Boolean = false): Pair<Response, SiteKey> {
        val key = siteKeys.current()
        val signed = url.newBuilder().addQueryParameter("_", key.sign(url)).build()
        val solvesBefore = captchaSolves.get()
        val response = client.get(signed, apiHeaders, Fetch.XHR.from(from), ensureSuccess = false)
        if (response.isSuccessful) {
            siteKeys.noteBuild(response.header("x-build-id"))
            return response to key
        }
        val refusal = response.use {
            if (it.body.contentType()?.subtype == "json") it.parseAs<ApiRefusal>() else null
        }
        when {
            refusal?.error == CAPTCHA_REQUIRED -> {
                if (retried) throw Exception(SITE_CHECK_ERROR)
                passSiteCheck(solvesBefore)
                return callApi(url, from, retried = true)
            }
            refusal?.code?.content in KEY_REFUSALS && !retried -> {
                siteKeys.onKeyRetired()
                return callApi(url, from, retried = true)
            }
            response.code == 502 && !retried -> {
                delay(BAD_GATEWAY_RETRY_MS)
                return callApi(url, from, retried = true)
            }
        }
        throw HttpException(response.code)
    }

    private suspend fun passSiteCheck(solvesBefore: Int) = captchaMutex.withLock {
        if (captchaSolves.get() != solvesBefore) return@withLock
        val challengeUrl = baseUrl.toHttpUrl().resolve(WAF_CHALLENGE_PATH)!!
        val page = client.get(challengeUrl, Fetch.DOCUMENT).body.string()
        val turnstileKey = TURNSTILE_SITE_KEY.find(page)?.groupValues?.get(1) ?: throw Exception(SITE_CHECK_ERROR)
        val token = getTurnstileToken(challengeUrl.toString(), turnstileKey)
        val verified = client.post(
            baseUrl.toHttpUrl().resolve(WAF_VERIFY_PATH)!!,
            Fetch.XHR.from(challengeUrl.toString()),
            WafVerifyRequest(token).toJsonRequestBody(),
            ensureSuccess = false,
        ).use { it.isSuccessful && it.parseAs<WafVerifyResponse>().success }
        if (!verified) throw Exception(SITE_CHECK_ERROR)
        captchaSolves.incrementAndGet()
    }

    private fun mangaApi(manga: SManga) = apiUrl.newBuilder()
        .addPathSegment("manga")
        .addPathSegment(manga.url.removePrefix("/").substringBefore('-'))

    override suspend fun getPopularManga(page: Int) = getBrowseList(page, "score")

    override suspend fun getLatestUpdates(page: Int) = getBrowseList(page, "chapter_updated_at")

    private suspend fun getBrowseList(page: Int, order: String): MangasPage {
        val url = apiUrl.newBuilder().apply {
            addPathSegment("manga")
            addQueryParameter("order[$order]", "desc")
            addQueryParameter("page", page.toString())
            applyPreferenceFilters()
        }.build()
        return getMangaList(url)
    }

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val requestUrl = url.newBuilder().addQueryParameter("limit", BROWSE_PAGE_LIMIT.toString()).build()
        val result = readApi<ApiResponse<MangaItems>>(requestUrl, "/browse").result
        val mangaList = result.items.map {
            it.toBasicSManga(baseUrl.toHttpUrl(), preferences.posterQuality())
        }
        return MangasPage(mangaList, result.meta.page < result.meta.lastPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val authorIds = resolveTagIdsForNames("author", filters.firstInstanceOrNull<Filters.AuthorFilter>()?.state.names())
        val artistIds = resolveTagIdsForNames("artist", filters.firstInstanceOrNull<Filters.ArtistFilter>()?.state.names())
        val (includedTags, excludedTags) = filters.firstInstanceOrNull<Filters.TagsFilter>()?.state.names()
            .partition { !it.startsWith('-') }
        val tagIds = resolveTagIdsForNames("tag", includedTags)
        val excludedTagIds = resolveTagIdsForNames("tag", excludedTags.map { it.removePrefix("-").trim() }.filter { it.isNotEmpty() })
        val hasTermSelection = filters.filterIsInstance<Filters.TermFilter>()
            .any { it.hasSelection } || tagIds.isNotEmpty() || excludedTagIds.isNotEmpty()
        val url = apiUrl.newBuilder()
            .addPathSegment("manga")
            .apply {
                filters.filterIsInstance<Filters.UriFilter>()
                    .filterNot { it is Filters.RequiresTermSelection && !hasTermSelection }
                    .forEach {
                        if (it is Filters.QueryAwareFilter) {
                            it.addToUri(this, query)
                        } else {
                            it.addToUri(this)
                        }
                    }

                authorIds.forEach { addQueryParameter("authors[]", it) }
                artistIds.forEach { addQueryParameter("artists[]", it) }
                tagIds.forEach { addQueryParameter("genres_in[]", it) }
                excludedTagIds.forEach { addQueryParameter("genres_ex[]", it) }

                if (query.isNotBlank()) {
                    addQueryParameter("keyword", query)
                }

                addQueryParameter("page", page.toString())
            }.build()

        return getMangaList(url)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") !in SITE_HOSTS) return null
        if (url.pathSegments.size < 2 || url.pathSegments[0] != "title") return null

        val mangaSlug = url.pathSegments[1].takeIf { it.substringBefore("-").isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/$mangaSlug" }
        return parseMangaDetails(getMangaDetail(manga))
    }

    private fun sourceFilters() = Filters(
        contentRating = preferences.contentRating(),
        selectedTypes = preferences.defaultTypes(),
        selectedDemographics = preferences.defaultDemographics(),
        blockedGenres = preferences.blockedGenres(),
    )

    private fun HttpUrl.Builder.applyPreferenceFilters() {
        sourceFilters().getFilterList()
            .filterIsInstance<Filters.PreferenceFilter>()
            .forEach { it.addToUri(this) }
    }

    private fun String?.names(): List<String> = orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private suspend fun resolveTagIdsForNames(type: String, names: List<String>): List<String> = names.flatMap { resolveTagIds(type, it) }

    private suspend fun resolveTagIds(type: String, name: String): List<String> {
        val cacheKey = "$type\u0000${name.lowercase()}"
        synchronized(tagIdCache) { tagIdCache[cacheKey] }?.let { return it }

        val url = apiUrl.newBuilder()
            .addPathSegments("tags/search")
            .addQueryParameter("type", type)
            .addQueryParameter("q", name)
            .addQueryParameter("limit", "20")
            .build()
        val hits = client.get(url, apiHeaders, Fetch.XHR.from("/browse")).parseAs<TagSearchResponse>().result
        if (hits.isEmpty()) throw Exception("No $type named \"$name\"")
        val ids = hits.filter { it.label.equals(name, ignoreCase = true) }.ifEmpty { hits }.map { it.id.toString() }
        synchronized(tagIdCache) { tagIdCache[cacheKey] = ids }
        return ids
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = SMangaUpdate(
        manga = { if (fetchDetails) parseMangaDetails(getMangaDetail(manga)) else manga },
        chapters = { if (fetchChapters) updateChapters(manga, chapters) else chapters },
    )

    private suspend fun updateChapters(manga: SManga, chapters: List<SChapter>): List<SChapter> {
        val deduplicateChapters = preferences.deduplicateChapters()
        val scanlatorBlacklist = preferences.scanlatorBlacklist()
        val blacklistSignature = scanlatorBlacklist.sorted().joinToString(",")
        val listMemo = chapters.firstOrNull()?.memo?.takeIf { CHAPTER_LIST_DEDUPLICATED_MEMO in it } ?: manga.memo
        val fetchUntilKnown = preferences.fetchChaptersUntilKnown() &&
            listMemo[CHAPTER_LIST_DEDUPLICATED_MEMO]?.booleanOrNull == deduplicateChapters &&
            listMemo[CHAPTER_LIST_BLACKLIST_MEMO]?.string == blacklistSignature &&
            listMemo[CHAPTER_DATA_VERSION_MEMO]?.int == CHAPTER_DATA_VERSION
        val knownIds = if (fetchUntilKnown) chapters.mapNotNull { it.chapterId() }.toSet() else emptySet()

        val knownDates = chapters.mapNotNull { chapter ->
            chapter.chapterId()?.let { it to chapter.date_upload }?.takeIf { it.second > 0 }
        }.toMap()
        val fetched = getChapterList(manga, knownIds).map { it.toSChapter() }.onEach { chapter ->
            chapter.chapterId()?.let(knownDates::get)?.let { chapter.date_upload = it }
        }
        val candidates = if (fetchUntilKnown) fetched + chapters else fetched
        return selectChapters(candidates, deduplicateChapters, scanlatorBlacklist).also { selected ->
            selected.firstOrNull()?.apply {
                memo = buildJsonObject {
                    memo.forEach { (key, value) -> put(key, value) }
                    put(CHAPTER_LIST_DEDUPLICATED_MEMO, deduplicateChapters)
                    put(CHAPTER_LIST_BLACKLIST_MEMO, blacklistSignature)
                    put(CHAPTER_DATA_VERSION_MEMO, CHAPTER_DATA_VERSION)
                }
            }
        }
    }

    private suspend fun getMangaDetail(manga: SManga): Manga = readApi<ApiResponse<Manga>>(mangaApi(manga).build(), getMangaUrl(manga)).result

    private fun parseMangaDetails(detail: Manga): SManga = detail.toSManga(
        baseUrl.toHttpUrl(),
        preferences.posterQuality(),
        preferences.alternativeNamesInDescription(),
        preferences.scorePosition(),
        preferences.showExtraInfo(),
        preferences.showTagsInGenres(),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title${manga.url}"

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = mangaApi(manga).addPathSegment("recommended").build()
        return readApi<ApiResponse<MangaItems>>(url, getMangaUrl(manga)).result.items.map {
            it.toBasicSManga(baseUrl.toHttpUrl(), preferences.posterQuality())
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}"

    private fun SChapter.chapterId(): Int? = memo[CHAPTER_ID_MEMO]?.int
        ?: getChapterUrl(this).toHttpUrlOrNull()
            ?.pathSegments
            ?.lastOrNull()
            ?.substringBefore("-chapter-")
            ?.toIntOrNull()

    private suspend fun getChapterList(manga: SManga, knownIds: Set<Int>): List<Chapter> {
        val chapters = ArrayList<Chapter>()
        var page = 1
        while (true) {
            chapterListPacer.waitForSlot()
            val url = mangaApi(manga)
                .addPathSegment("chapters")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", CHAPTER_PAGE_LIMIT.toString())
                .addQueryParameter("order[number]", "desc")
                .addQueryParameter("v", "0")
                .addQueryParameter("w", "0")
                .build()
            val result = readApi<ApiResponse<ChapterItems>>(url, getMangaUrl(manga)).result
            chapters += result.items
            if (result.items.any { it.id in knownIds } || page >= result.meta.lastPage) break
            page++
        }
        return chapters
    }

    private fun selectChapters(
        allChapters: List<SChapter>,
        shouldDeduplicate: Boolean,
        scanlatorBlacklist: Set<String>,
    ): List<SChapter> {
        val uniqueChapters = allChapters.distinctBy(SChapter::url)
        val filteredChapters = if (scanlatorBlacklist.isEmpty()) {
            uniqueChapters
        } else {
            uniqueChapters.filter { chapter ->
                chapter.scanlator.orEmpty().trim().lowercase() !in scanlatorBlacklist &&
                    chapter.groupId()?.toString() !in scanlatorBlacklist
            }
        }

        val finalChapters = if (shouldDeduplicate) {
            val chapterMap = LinkedHashMap<Float, SChapter>()
            deduplicateChapters(chapterMap, filteredChapters)
            chapterMap.values.toList()
        } else {
            filteredChapters
        }

        return finalChapters.sortedByDescending(SChapter::chapter_number)
    }

    private fun deduplicateChapters(
        chapterMap: LinkedHashMap<Float, SChapter>,
        items: List<SChapter>,
    ) {
        for (ch in items) {
            val key = ch.chapter_number
            val current = chapterMap[key]
            if (current == null) {
                chapterMap[key] = ch
            } else {
                val newIsOfficial = ch.isOfficial()
                val currentIsOfficial = current.isOfficial()
                val newIsOfficialGroup = ch.groupId() == OFFICIAL_GROUP_ID
                val currentIsOfficialGroup = current.groupId() == OFFICIAL_GROUP_ID

                val better = when {
                    newIsOfficial && !currentIsOfficial -> true
                    !newIsOfficial && currentIsOfficial -> false
                    newIsOfficialGroup && !currentIsOfficialGroup -> true
                    !newIsOfficialGroup && currentIsOfficialGroup -> false
                    else -> when {
                        ch.votes() > current.votes() -> true
                        ch.votes() < current.votes() -> false
                        else -> (ch.chapterId() ?: 0) > (current.chapterId() ?: 0)
                    }
                }
                if (better) chapterMap[key] = ch
            }
        }
    }

    private fun SChapter.votes(): Int = memo[CHAPTER_VOTES_MEMO]?.int ?: 0

    private fun SChapter.isOfficial(): Boolean = memo[CHAPTER_OFFICIAL_MEMO]?.booleanOrNull == true

    private fun SChapter.groupId(): Int? = memo[CHAPTER_GROUP_ID_MEMO]?.int

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.chapterId() ?: throw Exception("Could not find the chapter id")
        val url = apiUrl.newBuilder()
            .addPathSegment("chapters")
            .addPathSegment(chapterId.toString())
            .addQueryParameter("v", "0")
            .addQueryParameter("w", "0")
            .build()
        val pages = readApi<ApiResponse<ChapterPagesResult>>(url, getChapterUrl(chapter)).result.pages
        return pages.items.mapIndexed { index, image ->
            val imageUrl = image.url.toHttpUrl()
            val altHosts = pages.altHosts(imageUrl.host, index)
            val pageUrl = imageUrl.newBuilder().apply {
                if (altHosts.isNotEmpty()) fragment(ALT_HOSTS_FRAGMENT + altHosts.joinToString(","))
            }.build()
            Page(index, imageUrl = pageUrl.toString())
        }
    }

    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder().removeHeader("Referer").build()

    override fun getFilterList(data: JsonElement?) = sourceFilters().getFilterList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_FETCH_CHAPTERS_UNTIL_KNOWN
            title = "Faster chapter list fetching"
            summary = "Enabled: Uses fewer requests, but may miss newly added older chapters " +
                "(e.g. chapter 5.5 when the latest known chapter is 150).\n\n" +
                "Disabled: Finds older chapter additions, but fetching large chapter lists is slower."
            setDefaultValue(true)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_POSTER_QUALITY
            title = "Thumbnail Quality"
            summary = "Change the quality of the thumbnail. Current: %s."
            entryValues = arrayOf("medium", "large")
            entries = arrayOf("Medium", "Large")
            setDefaultValue("large")
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_CONTENT_RATING
            title = "Content rating"
            summary = "Maximum content rating shown in popular, latest, and search " +
                "results. The Content rating filter in search overrides this. " +
                "Current: %s."
            entries = arrayOf("Show all", "Safe only", "Up to Suggestive", "Up to Erotica", "Up to Pornographic")
            entryValues = arrayOf("", "safe", "suggestive", "erotica", "pornographic")
            setDefaultValue(DEFAULT_CONTENT_RATING)
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = PREF_DEFAULT_TYPES
            title = "Default types"
            summary = "Types to include in popular, latest, and search results. " +
                "The Type filter in search overrides this."
            entries = Filters.getTypes().map { it.first }.toTypedArray()
            entryValues = Filters.getTypes().map { it.second }.toTypedArray()
            setDefaultValue(Filters.getTypes().map { it.second }.toSet())
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = PREF_DEFAULT_DEMOGRAPHICS
            title = "Default demographics"
            summary = "Unticked demographics are hidden from popular, latest, and search " +
                "results. The Demographic filter in search overrides this."
            entries = Filters.getDemographics().map { it.first }.toTypedArray()
            entryValues = Filters.getDemographics().map { it.second }.toTypedArray()
            setDefaultValue(Filters.getDemographics().map { it.second }.toSet())
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = PREF_BLOCKED_GENRES
            title = "Blocked genres"
            summary = "Genres always excluded from results. The search filter " +
                "can still include a blocked genre as a one-off override."
            entries = Filters.getGenres().map { it.first }.toTypedArray()
            entryValues = Filters.getGenres().map { it.second }.toTypedArray()
            setDefaultValue(emptySet<String>())
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = DEDUPLICATE_CHAPTERS
            title = "Deduplicate Chapters"
            summary = "Remove duplicate chapters from the chapter list.\n" +
                "Official chapters (Comix-marked) are preferred, followed by the highest-voted or most recent."
            setDefaultValue(false)
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_SCANLATOR_BLACKLIST
            title = "Scanlator Blacklist"
            summary = "Filter out chapters from specific groups. Comma-separated list of group names or group IDs (e.g., 'Violet Scans, 307')."
            dialogTitle = "Exclude groups"
            setDefaultValue("")
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = ALTERNATIVE_NAMES_IN_DESCRIPTION
            title = "Show Alternative Names in Description"
            setDefaultValue(false)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_EXTRA_INFO
            title = "Show extra info in description"
            summary = "Append publication year, language, content rating, publisher, rank, " +
                "ratings count, follower count, and tracker links to the manga description."
            setDefaultValue(true)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_TAGS_IN_GENRES
            title = "Show tags in genre chips"
            summary = "Add the site's tags (e.g. Demons, Vampires, Time Travel) to the genres in the manga details."
            setDefaultValue(false)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_SCORE_POSITION
            title = "Score display position"
            summary = "%s"
            entries = arrayOf("Top of description", "Bottom of description", "Don't show")
            entryValues = arrayOf("top", "bottom", "none")
            setDefaultValue("top")
        }.let(screen::addPreference)
    }

    private fun SharedPreferences.posterQuality() = getString(PREF_POSTER_QUALITY, "large")

    private fun SharedPreferences.deduplicateChapters() = getBoolean(DEDUPLICATE_CHAPTERS, false)

    private fun SharedPreferences.fetchChaptersUntilKnown() = getBoolean(PREF_FETCH_CHAPTERS_UNTIL_KNOWN, true)

    private fun SharedPreferences.scanlatorBlacklist(): Set<String> = getString(PREF_SCANLATOR_BLACKLIST, "")
        ?.split(",")
        ?.map { it.trim().lowercase() }
        ?.filter { it.isNotEmpty() }
        ?.toSet() ?: emptySet()

    private fun SharedPreferences.alternativeNamesInDescription() = getBoolean(ALTERNATIVE_NAMES_IN_DESCRIPTION, false)

    private fun SharedPreferences.scorePosition() = getString(PREF_SCORE_POSITION, "top") ?: "top"

    private fun SharedPreferences.showExtraInfo() = getBoolean(PREF_SHOW_EXTRA_INFO, true)

    private fun SharedPreferences.showTagsInGenres() = getBoolean(PREF_SHOW_TAGS_IN_GENRES, false)

    private fun SharedPreferences.defaultTypes(): Set<String> {
        val all = Filters.getTypes().map { it.second }.toSet()
        return getStringSet(PREF_DEFAULT_TYPES, all) ?: all
    }

    private fun SharedPreferences.defaultDemographics(): Set<String> {
        val all = Filters.getDemographics().map { it.second }.toSet()
        return getStringSet(PREF_DEFAULT_DEMOGRAPHICS, all)?.ifEmpty { all } ?: all
    }

    private fun SharedPreferences.blockedGenres(): Set<String> = getStringSet(PREF_BLOCKED_GENRES, emptySet()) ?: emptySet()

    private fun SharedPreferences.contentRating(): String {
        if (contains(PREF_CONTENT_RATING)) {
            return getString(PREF_CONTENT_RATING, DEFAULT_CONTENT_RATING) ?: DEFAULT_CONTENT_RATING
        }
        if (contains(LEGACY_HIDE_NSFW_PREF) && !getBoolean(LEGACY_HIDE_NSFW_PREF, true)) {
            return ""
        }
        return DEFAULT_CONTENT_RATING
    }

    companion object {
        private const val PREF_POSTER_QUALITY = "pref_poster_quality"
        private const val LEGACY_SOLVE_CAPTCHA_PREF = "pref_solve_captcha"
        private const val PREF_CONTENT_RATING = "pref_content_rating"
        private const val PREF_DEFAULT_TYPES = "pref_default_types"
        private const val PREF_DEFAULT_DEMOGRAPHICS = "pref_default_demographics"
        private const val PREF_BLOCKED_GENRES = "pref_blocked_genres"
        private const val LEGACY_HIDE_NSFW_PREF = "nsfw_pref"
        private const val DEDUPLICATE_CHAPTERS = "pref_deduplicate_chapters"
        private const val PREF_FETCH_CHAPTERS_UNTIL_KNOWN = "pref_fetch_chapters_until_known"
        private const val PREF_SCANLATOR_BLACKLIST = "pref_scanlator_blacklist"
        private const val ALTERNATIVE_NAMES_IN_DESCRIPTION = "pref_alt_names_in_description"
        private const val PREF_SHOW_EXTRA_INFO = "pref_show_extra_info"
        private const val PREF_SHOW_TAGS_IN_GENRES = "pref_show_tags_in_genres"
        private const val PREF_SCORE_POSITION = "pref_score_position"

        private const val DEFAULT_CONTENT_RATING = "suggestive"
        private val chapterListPacer = RequestPacer(minGapMs = 2500, maxGapMs = 5000)
        private const val CHAPTER_PAGE_LIMIT = 100
        private const val BROWSE_PAGE_LIMIT = 28
        private const val BAD_GATEWAY_RETRY_MS = 5000L
        private const val ALT_HOSTS_FRAGMENT = "alt="
        private val STATIC_PATHS = setOf("images", "assets")

        private const val ENCRYPTED_BODY_VERSION = "2"

        private val SITE_HOSTS = setOf("comix.to", "comix.ws")
        private const val CAPTCHA_REQUIRED = "captcha_required"
        private val KEY_REFUSALS = setOf("key_retired", "invalid_token")

        private const val WAF_CHALLENGE_PATH = "/@waf/challenge"
        private const val WAF_VERIFY_PATH = "/@waf/verify"
        private val TURNSTILE_SITE_KEY = Regex("""sitekey:\s*["']([^"']+)["']""")
        private const val SITE_CHECK_ERROR = "Open in WebView to pass the site's check"

        private const val OFFICIAL_GROUP_ID = 10702
        private const val CHAPTER_DATA_VERSION = 2
        private const val TAG_ID_CACHE_SIZE = 50
    }
}
