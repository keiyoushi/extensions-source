package eu.kanade.tachiyomi.extension.en.inkr

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
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient

@Source
abstract class Inkr :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val queryApiUrl = "https://icq-api.inkr.com/v1"
    private val contentApiUrl = "https://icd-api.inkr.com/v1"

    private val auth = InkrAuth(client = { client }, baseUrl = { baseUrl })

    private val apiHeaders: Headers by lazy {
        headersBuilder()
            .set("User-Agent", "okhttp/4.9.1")
            .set("ikc-platform", "android")
            .set("cf-ipcountry", "en-GB")
            .set("Accept", "application/json")
            .build()
    }

    private val catalogMutex = Mutex()
    private var catalogCache: CatalogCache? = null

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(authInterceptor())
        .addInterceptor(ImageInterceptor())

    private fun authInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val host = request.url.host
        if (
            (host == "icq-api.inkr.com" || host == "icd-api.inkr.com") &&
            request.header("Authorization") == null
        ) {
            val token = auth.accessToken
            if (token != null) {
                return@Interceptor chain.proceed(
                    request.newBuilder().header("Authorization", "Bearer $token").build(),
                )
            }
        }
        chain.proceed(request)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = browseManga(page, "", FilterList(), SortMode.Popular)

    override suspend fun getLatestUpdates(page: Int): MangasPage = browseManga(page, "", FilterList(), SortMode.Latest)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val trimmed = query.trim()
        val sortMode = when {
            trimmed.isEmpty() -> SortMode.Popular
            genreIdForName(trimmed) != null -> SortMode.Popular
            else -> SortMode.Relevance
        }
        return browseManga(page, trimmed, filters, sortMode)
    }

    private suspend fun browseManga(
        page: Int,
        query: String,
        filters: FilterList,
        sortMode: SortMode,
    ): MangasPage {
        val showNsfw = preferences.getBoolean(SHOW_NSFW_PREF_KEY, false)
        val filterRequest = buildFilteredRequest(filters, query)
        val cacheKey = catalogKey(query, filterRequest, sortMode, showNsfw)
        val titles = catalogMutex.withLock {
            val cached = catalogCache
            if (cached != null && cached.key == cacheKey) {
                cached.titles
            } else {
                val oids = resolveTitleOids(query, filterRequest)
                val hydrated = hydrateTitles(oids)
                    .filter { it.isAvailable && !it.isRemovedFromSale }
                    .filter { showNsfw || !it.isExplicit }
                    // /title/search ranks poorly (often worst-first); keep name matches only
                    .let { list ->
                        if (query.isEmpty() || genreIdForName(query) != null) {
                            list
                        } else {
                            list.filter { it.name.contains(query, ignoreCase = true) }
                        }
                    }
                val sorted = when (sortMode) {
                    SortMode.Popular -> hydrated.sortedByDescending { it.pageReadCount }
                    SortMode.Latest -> hydrated.sortedByDescending { it.latestChapterFirstPublishedDate.orEmpty() }
                    SortMode.Relevance -> hydrated
                }
                catalogCache = CatalogCache(cacheKey, sorted)
                sorted
            }
        }

        val from = (page - 1) * PAGE_SIZE
        if (from >= titles.size) {
            return MangasPage(emptyList(), false)
        }
        val pageTitles = titles.subList(from, minOf(from + PAGE_SIZE, titles.size))
        val mangas = toSMangaList(pageTitles)
        return MangasPage(mangas, from + PAGE_SIZE < titles.size)
    }

    private fun buildFilteredRequest(filters: FilterList, searchQuery: String = ""): FilteredRequest {
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected?.takeIf { it.isNotEmpty() }
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected?.takeIf { it.isNotEmpty() }
        val selectedGenres = filters.firstInstanceOrNull<GenreFilters>()
            ?.state
            ?.flatMap { letterGroup -> letterGroup.state.filter { it.state }.map { it.id } }
            .orEmpty()
            .toMutableList()
        genreIdForName(searchQuery)?.let { selectedGenres.add(it) }

        return FilteredRequest(
            orStyleOrigin = type?.let { listOf(it) },
            releaseStatus = status,
            andGenres = selectedGenres.distinct().takeIf { it.isNotEmpty() },
        )
    }

    private suspend fun resolveTitleOids(searchQuery: String, filterRequest: FilteredRequest): List<String> {
        // Genre chip clicks arrive as a search query matching the genre display name
        if (genreIdForName(searchQuery) != null) {
            return filterTitleOids(filterRequest)
        }
        if (searchQuery.isEmpty()) {
            return filterTitleOids(filterRequest)
        }
        val searchOids = searchTitleOids(searchQuery)
        val hasFilters = filterRequest.orStyleOrigin != null ||
            filterRequest.releaseStatus != null ||
            filterRequest.andGenres != null
        if (!hasFilters) return searchOids
        val allowed = filterTitleOids(filterRequest).toHashSet()
        return searchOids.filter { it in allowed }
    }

    private fun genreIdForName(name: String): String? = GENRES.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

    private suspend fun filterTitleOids(request: FilteredRequest): List<String> = client.post("$queryApiUrl/title/filtered", apiHeaders, request.toJsonRequestBody())
        .parseAs<FilteredResponse>()
        .data

    private suspend fun searchTitleOids(query: String): List<String> = client.post(
        "$queryApiUrl/title/search",
        apiHeaders,
        SearchRequest(query).toJsonRequestBody(),
    ).parseAs<SearchResponse>().data.title

    private suspend fun hydrateTitles(oids: List<String>): List<TitleDto> {
        if (oids.isEmpty()) return emptyList()
        val byOid = coroutineScope {
            oids.chunked(BATCH_SIZE).map { chunk ->
                async {
                    fetchContentMap(chunk, TITLE_LIST_FIELDS).mapValues { (_, element) ->
                        element.parseAs<TitleDto>()
                    }
                }
            }.awaitAll()
        }.flatMap { it.entries }.associate { it.key to it.value }

        return oids.mapNotNull { byOid[it] }
    }

    private suspend fun toSMangaList(titles: List<TitleDto>): List<SManga> {
        val imageOids = titles.mapNotNull { it.thumbnailImage }.distinct()
        val images = fetchNamedMap(imageOids)
        val genreOids = titles.flatMap { it.keyGenreList }.distinct()
        val genres = fetchNamedMap(genreOids)

        return titles.map { title ->
            title.toSManga(
                thumbnailUrl = title.thumbnailImage?.let { images[it]?.url },
                authors = null,
                genres = title.keyGenreList.mapNotNull { genres[it]?.name }.joinToString()
                    .takeIf { it.isNotEmpty() },
            )
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        auth.ensureLoaded()
        val showPaid = preferences.getBoolean(SHOW_PAID_PREF_KEY, false)
        val titleDeferred = async {
            if (!fetchDetails && !fetchChapters) return@async null
            fetchContentMap(listOf(manga.url), TITLE_DETAIL_FIELDS)[manga.url]
                ?.parseAs<TitleDto>()
                ?: throw Exception("Title not found")
        }

        val detailsDeferred = async {
            if (!fetchDetails) return@async manga
            val title = titleDeferred.await()!!
            val image = title.thumbnailImage?.let { fetchNamedMap(listOf(it))[it]?.url }
            val creators = fetchNamedMap(title.titleCreators.map { it.creator }.distinct())
            val authors = title.titleCreators
                .mapNotNull { creators[it.creator]?.name }
                .distinct()
                .joinToString()
                .takeIf { it.isNotEmpty() }
            val genres = fetchNamedMap(title.keyGenreList)
            title.toSManga(
                thumbnailUrl = image,
                authors = authors,
                genres = title.keyGenreList.mapNotNull { genres[it]?.name }.joinToString()
                    .takeIf { it.isNotEmpty() },
            )
        }

        val chaptersDeferred = async {
            if (!fetchChapters) return@async chapters
            val title = titleDeferred.await()!!
            if (title.chapterList.isEmpty()) return@async emptyList()

            val chapterMap = coroutineScope {
                title.chapterList.chunked(BATCH_SIZE).map { chunk ->
                    async {
                        fetchContentMap(chunk, CHAPTER_FIELDS).mapValues { (_, element) ->
                            element.parseAs<ChapterDto>()
                        }
                    }
                }.awaitAll()
            }.flatMap { it.entries }.associate { it.key to it.value }

            val isSubscriber = auth.isSubscriber
            title.chapterList.mapNotNull { oid ->
                val chapter = chapterMap[oid] ?: return@mapNotNull null
                val accessible = chapter.isAccessible(isSubscriber)
                if (!accessible && !showPaid) return@mapNotNull null
                chapter.toSChapter(
                    titleOid = title.oid,
                    showPaidMarker = showPaid,
                    isSubscriber = isSubscriber,
                )
            }.sortedByDescending { it.chapter_number }
        }

        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        auth.ensureLoaded()

        val meta = fetchContentMap(listOf(chapter.url), CHAPTER_FIELDS)[chapter.url]
            ?.parseAs<ChapterDto>()
        val isSubscriber = auth.isSubscriber
        val accessible = meta?.isAccessible(isSubscriber)
            ?: chapter.memo[CHAPTER_ACCESSIBLE_MEMO]?.booleanOrNull
            ?: chapter.memo[CHAPTER_FREE_MEMO]?.booleanOrNull

        if (accessible == false) {
            val revenue = meta?.revenueType?.lowercase().orEmpty()
            throw when {
                auth.accessToken == null ->
                    Exception("Log in via WebView (INKR account), then reopen this chapter")
                revenue == "coin-only" ->
                    Exception("Chapter requires INKR coins (Extra does not unlock coin-only chapters)")
                else ->
                    Exception("Chapter requires INKR coins or Extra subscription")
            }
        }

        val pagesDto = fetchContentMap(
            oids = listOf(chapter.url),
            fields = listOf("chapterPages"),
            includes = ChapterPagesIncludes(
                chapterPages = ChapterPagesInclude(
                    fields = listOf("oid", "width", "height", "type"),
                    includes = EmptyIncludes(),
                    includeKey = "page",
                ),
            ),
        )[chapter.url]?.parseAs<ChapterPagesDto>()
            ?: return emptyList()

        return pagesDto.chapterPages.mapIndexed { index, page ->
            Page(index, imageUrl = "${page.url.trimEnd('/')}/$IMAGE_VARIANT")
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val id = manga.url.removePrefix("ik-title-")
        return "$baseUrl/title/$id"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val titleId = chapter.memo[CHAPTER_TITLE_MEMO]?.string?.removePrefix("ik-title-")
            ?: return baseUrl
        val chapterId = chapter.url.removePrefix("ik-chapter-")
        return "$baseUrl/title/$titleId/chapter/$chapterId"
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segment = url.pathSegments.getOrNull(0) ?: return null
        if (segment != "title") return null
        val raw = url.pathSegments.getOrNull(1) ?: return null
        val id = raw.substringBefore('-').takeIf { it.all(Char::isDigit) } ?: return null
        val oid = "ik-title-$id"
        val title = fetchContentMap(listOf(oid), TITLE_DETAIL_FIELDS)[oid]?.parseAs<TitleDto>()
            ?: return null
        val image = title.thumbnailImage?.let { fetchNamedMap(listOf(it))[it]?.url }
        val creators = fetchNamedMap(title.titleCreators.map { it.creator }.distinct())
        val authors = title.titleCreators
            .mapNotNull { creators[it.creator]?.name }
            .distinct()
            .joinToString()
            .takeIf { it.isNotEmpty() }
        val genres = fetchNamedMap(title.keyGenreList)
        return title.toSManga(
            thumbnailUrl = image,
            authors = authors,
            genres = title.keyGenreList.mapNotNull { genres[it]?.name }.joinToString()
                .takeIf { it.isNotEmpty() },
        ).apply { initialized = true }
    }

    override fun getFilterList(data: JsonElement?) = defaultFilterList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_NSFW_PREF_KEY
            title = "Show NSFW titles"
            summary = "Include titles marked as explicit."
            setDefaultValue(false)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_PAID_PREF_KEY
            title = "Show paid chapters"
            summary = "Display locked coin/Extra chapters (marked 🔒). " +
                "Log in via WebView to unlock purchased/Extra chapters, then refresh the series."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    private suspend fun fetchNamedMap(oids: List<String>): Map<String, NamedDto> {
        if (oids.isEmpty()) return emptyMap()
        return coroutineScope {
            oids.chunked(BATCH_SIZE).map { chunk ->
                async {
                    fetchContentMap(chunk, NAMED_FIELDS).mapValues { (_, element) ->
                        element.parseAs<NamedDto>()
                    }
                }
            }.awaitAll()
        }.flatMap { it.entries }.associate { it.key to it.value }
    }

    private suspend fun fetchContentMap(
        oids: List<String>,
        fields: List<String>,
        includes: ChapterPagesIncludes? = null,
    ): Map<String, JsonElement> {
        if (oids.isEmpty()) return emptyMap()
        val body = listOf(
            ContentJsonRequest(
                fields = fields,
                oids = oids,
                includes = includes,
            ),
        ).toJsonRequestBody()

        return client.post("$contentApiUrl/content_json/batch", apiHeaders, body)
            .parseAs<ContentMapResponse>()
            .data
    }

    private fun catalogKey(
        searchQuery: String,
        request: FilteredRequest,
        sortMode: SortMode,
        showNsfw: Boolean,
    ): String = listOf(
        sortMode.name,
        searchQuery,
        request.orStyleOrigin.orEmpty().joinToString(),
        request.releaseStatus.orEmpty(),
        request.andGenres.orEmpty().joinToString(),
        showNsfw.toString(),
    ).joinToString("\u0000")

    private class CatalogCache(
        val key: String,
        val titles: List<TitleDto>,
    )

    private enum class SortMode {
        Popular,
        Latest,
        Relevance,
    }

    companion object {
        private const val PAGE_SIZE = 20
        private const val BATCH_SIZE = 50
        private const val IMAGE_VARIANT = "w1600.ikc"
        private const val SHOW_NSFW_PREF_KEY = "show_nsfw_titles"
        private const val SHOW_PAID_PREF_KEY = "show_paid_chapters"

        private val TITLE_LIST_FIELDS = listOf(
            "oid",
            "name",
            "thumbnailImage",
            "releaseStatus",
            "styleOrigin",
            "keyGenreList",
            "summary",
            "pageReadCount",
            "latestChapterFirstPublishedDate",
            "isExplicit",
            "monetizationType",
            "isAvailable",
            "isRemovedFromSale",
        )

        private val TITLE_DETAIL_FIELDS = TITLE_LIST_FIELDS + listOf(
            "chapterList",
            "titleCreators",
        )

        private val CHAPTER_FIELDS = listOf(
            "oid",
            "name",
            "order",
            "firstPublishedDate",
            "publishedDate",
            "revenueType",
            "coinPrice",
            "isPurchasedByCoin",
            "isPurchasedBySub",
        )

        private val NAMED_FIELDS = listOf("oid", "name", "url")
    }
}
