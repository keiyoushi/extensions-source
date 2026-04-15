package eu.kanade.tachiyomi.extension.en.arvenscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class VortexScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Vortex Scans"
    override val baseUrl = "https://vortexscans.org"
    private val apiUrl = "https://api.vortexscans.org"
    override val lang = "en"
    override val supportsLatest = true

    override val client = network.cloudflareClient
    private val preferences by getPreferencesLazy()

    private val baseHost = baseUrl.toHttpUrl().host
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
    private val showLockedChapters
        get() = preferences.getBoolean(SHOW_LOCKED_CHAPTERS_PREF_KEY, SHOW_LOCKED_CHAPTERS_DEFAULT)

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val apiHeaders: Headers by lazy {
        headersBuilder()
            .set("Accept", "application/json, text/plain, */*")
            .set("Origin", baseUrl)
            .build()
    }

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request = queryRequest(
        page = page,
        query = "",
        orderBy = POPULAR_ORDER_BY,
        orderDirection = ORDER_DESC,
    )

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = queryRequest(
        page = page,
        query = "",
        orderBy = LATEST_ORDER_BY,
        orderDirection = ORDER_DESC,
    )

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ========================= Search =========================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (page == 1) {
            val deepLink = parseDeepLink(query, baseHost)
            if (deepLink != null) {
                val summary = findPostBySlug(deepLink.mangaSlug)
                    ?: throw Exception("Series not found")

                val manga = summary.toSMangaSummary()

                return fetchMangaDetails(manga)
                    .map { MangasPage(listOf(it), false) }
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = queryRequest(
        page = page,
        query = query.trim(),
        filters = filters.filterIsInstance<UrlPartFilter>(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val payload = response.parseAs<SearchResponseDto>()
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = payload.totalCount > (currentPage * PER_PAGE)

        val mangas = payload.posts.map { it.toSMangaSummary() }
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Filters =========================

    override fun getFilterList() = FilterList(
        StatusFilter("Status", STATUS_FILTER_KEY, STATUS_OPTIONS),
        TypeFilter("Type", TYPE_FILTER_KEY, TYPE_OPTIONS),
        SortFilter("Sort by", SORT_FILTER_KEY, SORT_OPTIONS),
        SortDirectionFilter("Sort direction", SORT_DIRECTION_FILTER_KEY, SORT_DIRECTION_OPTIONS),
        GenreFilter("Genres", GENRE_OPTIONS),
    )

    // ========================= Details =========================

    override fun getMangaUrl(manga: SManga): String {
        val slug = extractMangaSlug(manga.url)
        return "$baseUrl/$SERIES_PATH_SEGMENT/$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request = postRequest(resolvePostId(manga))

    override fun mangaDetailsParse(response: Response): SManga {
        val payload = response.parseAs<PostResponseDto>()
        return payload.post.toSMangaDetailsModel()
    }

    // ========================= Chapters =========================

    override fun getChapterUrl(chapter: SChapter): String {
        val slugs = extractChapterSlugs(chapter.url)
            ?: return "$baseUrl/$SERIES_PATH_SEGMENT/${chapter.url.substringBefore('#').trim('/')}"

        return "$baseUrl/$SERIES_PATH_SEGMENT/${slugs.first}/${slugs.second}"
    }

    override fun chapterListRequest(manga: SManga): Request = postRequest(resolvePostId(manga))

    override fun chapterListParse(response: Response): List<SChapter> {
        val payload = response.parseAs<PostResponseDto>()
        val mangaSlug = payload.post.slug

        return payload.post.chapters
            .filter { chapter ->
                showLockedChapters || (chapter.isAccessible != false && chapter.isLocked != true)
            }
            .map { it.toSChapterModel(mangaSlug, dateFormat) }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_CHAPTERS_PREF_KEY
            title = "Show inaccessible chapters"
            summaryOn = "Locked/inaccessible chapters will be shown in the chapter list."
            summaryOff = "Locked/inaccessible chapters will be hidden from the chapter list."
            setDefaultValue(SHOW_LOCKED_CHAPTERS_DEFAULT)
        }.also(screen::addPreference)
    }

    // ========================= Pages =========================

    override fun pageListRequest(chapter: SChapter): Request = chapterRequest(resolveChapterId(chapter))

    override fun pageListParse(response: Response): List<Page> {
        val payload = response.parseAs<ChapterResponseDto>()
        val chapter = payload.chapter

        if (chapter.isAccessible == false || chapter.isLocked == true) {
            throw Exception(LOCKED_CHAPTER_MESSAGE)
        }

        val images = chapter.images.sortedBy { it.order ?: Int.MAX_VALUE }
        if (images.isEmpty()) {
            throw Exception("No pages found")
        }

        return images.mapIndexed { index, image ->
            Page(index = index, imageUrl = image.url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun queryRequest(
        page: Int,
        query: String,
        orderBy: String? = null,
        orderDirection: String? = null,
        filters: List<UrlPartFilter> = emptyList(),
    ): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            encodedPath(API_QUERY_PATH)
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", PER_PAGE.toString())
            addQueryParameter("searchTerm", query)

            filters.forEach { it.addUrlParameter(this) }

            if (!orderBy.isNullOrBlank()) {
                setQueryParameter(SORT_FILTER_KEY, orderBy)
            }

            if (!orderDirection.isNullOrBlank()) {
                setQueryParameter(SORT_DIRECTION_FILTER_KEY, orderDirection)
            }
        }.build()

        return GET(url, apiHeaders)
    }

    private fun postRequest(postId: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            encodedPath(API_POST_PATH)
            addQueryParameter("postId", postId.toString())
        }.build()

        return GET(url, apiHeaders)
    }

    private fun chapterRequest(chapterId: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            encodedPath(API_CHAPTER_PATH)
            addQueryParameter("chapterId", chapterId.toString())
        }.build()

        return GET(url, apiHeaders)
    }

    private fun resolvePostId(manga: SManga): Int {
        manga.url.substringAfter('#', "").toIntOrNull()?.let { return it }

        val slug = extractMangaSlug(manga.url)
        val summary = findPostBySlug(slug)
        return summary?.id ?: throw Exception("Unable to resolve series id")
    }

    private fun resolveChapterId(chapter: SChapter): Int {
        chapter.url.substringAfter('#', "").toIntOrNull()?.let { return it }

        val slugs = extractChapterSlugs(chapter.url)
            ?: throw Exception("Unable to resolve chapter id")

        val summary = findPostBySlug(slugs.first)
            ?: throw Exception("Unable to resolve series id")

        return try {
            client.newCall(postRequest(summary.id)).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Unable to resolve chapter id")
                }

                val payload = response.parseAs<PostResponseDto>()
                val chapterDto = payload.post.chapters.firstOrNull { it.slug == slugs.second }
                    ?: throw Exception("Unable to resolve chapter id")

                chapterDto.id
            }
        } catch (_: Exception) {
            throw Exception("Unable to resolve chapter id")
        }
    }

    private fun findPostBySlug(slug: String): PostSummaryDto? {
        val normalizedSlug = slug.trim().trim('/').lowercase()
        val terms = buildSlugSearchTerms(normalizedSlug)

        for (term in terms) {
            try {
                client.newCall(queryRequest(page = 1, query = term)).execute().use { response ->
                    if (response.isSuccessful) {
                        val payload = response.parseAs<SearchResponseDto>()
                        val post = payload.posts.firstOrNull { it.slug.equals(normalizedSlug, ignoreCase = true) }
                        if (post != null) {
                            return post
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return null
    }
}
