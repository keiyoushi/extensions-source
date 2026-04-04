package eu.kanade.tachiyomi.extension.en.bookwalker

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.ChapterDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.ChapterType
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.ChaptersRequestDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.ChaptersResponseDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.LimitOffsetDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.MangaDetailsRequestDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.MangaDetailsResponseDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.MangaInfoDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchPageType
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchPageTypeDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchRequestDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SearchResponseDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SeriesFormat
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.SortDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.TagKind
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Single

class BookWalker :
    HttpSource(),
    ConfigurableSource,
    BookWalkerPreferences {

    override val name = "BookWalker"

    override val baseUrl = "https://bookwalker.com"

    override val lang = "en"

    override val supportsLatest = true

    private val readerManager = BookWalkerChapterReaderManager(this)

    override val client = network.client.newBuilder()
        .addInterceptor(BookWalkerImageRequestInterceptor(readerManager))
        .build()

    private val preferences by getPreferencesLazy()

//    override val showLibraryInPopular
//        get() = preferences.getBoolean(PREF_SHOW_LIBRARY_IN_POPULAR, false)

    override val filterChapters
        get() = FilterChaptersPref.fromKey(
            preferences.getString(
                FilterChaptersPref.PREF_KEY,
                FilterChaptersPref.defaultOption.key,
            )!!,
        )

    override val attemptToReadPreviews
        get() = preferences.getBoolean(PREF_ATTEMPT_READ_PREVIEWS, false)

//    override val useEarliestThumbnail: Boolean
//        get() = preferences.getBoolean(PREF_USE_EARLIEST_THUMBNAIL, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
//        SwitchPreferenceCompat(screen.context).apply {
//            key = PREF_SHOW_LIBRARY_IN_POPULAR
//            title = "Show My Library in Popular"
//            summary = "Show your library instead of popular manga when browsing \"Popular\"."
//
//            setDefaultValue(false)
//        }.also(screen::addPreference)
//
//        SwitchPreferenceCompat(screen.context).apply {
//            key = PREF_USE_EARLIEST_THUMBNAIL
//            title = "Use First Volume Cover For Thumbnail"
//            summary = "This does not affect browsing, and may not work properly for chapter " +
//                "releases or for series with a very large number of volumes."
//
//            setDefaultValue(false)
//        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = FilterChaptersPref.PREF_KEY
            title = "Filter Shown Chapters"
            summary = "Choose what types of chapters to show."

            entries = arrayOf(
                "Show owned and free chapters",
                "Show obtainable chapters",
                "Show all chapters",
            )

            entryValues = arrayOf(
                FilterChaptersPref.OWNED.key,
                FilterChaptersPref.OBTAINABLE.key,
                FilterChaptersPref.ALL.key,
            )

            setDefaultValue(FilterChaptersPref.defaultOption.key)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ATTEMPT_READ_PREVIEWS
            title = "Show Previews When Available"
            summary = "Determines whether attempting to read an un-owned chapter should show the " +
                "preview. Even when disabled, you will still be able to read free chapters you " +
                "have not \"purchased\"."

            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    private val filterInfo by lazy { BookWalkerFilters(this) }

    override fun getFilterList(): FilterList {
        filterInfo.fetchIfNecessaryInBackground()

        return FilterList(
            SortFilter,
            FormatFilter,
            TriStateFilter("Genres", "genres", filterInfo.genreFilters ?: fallbackFilters),
        )
    }

    override fun popularMangaRequest(page: Int): Request {
        filterInfo.fetchIfNecessaryInBackground()
        return POST(
            endpoint("CollectionService/SearchV2"),
            headers,
            SearchRequestDto(
                limitOffset = LimitOffsetDto(PAGE_SIZE, PAGE_SIZE * (page - 1)),
                sort = SortDto.POPULAR,
                formats = listOf(SeriesFormat.MANGA),
                filters = listOf(),
                searchDomain = SearchPageTypeDto(SearchPageType.Browse()),
            ).toProtoRequestBody(),
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val pageInfo = response.parseProtoAs<SearchResponseDto>()
        val results = pageInfo.results.value
        return MangasPage(
            results.map { it.value.toSManga() },
            pageInfo.countInfo.totalCount > pageInfo.countInfo.offset + pageInfo.countInfo.limit,
        )
    }

    private fun MangaInfoDto.toSManga() = SManga.create().apply {
        url = getUrl()
        title = this@toSManga.title
        thumbnail_url = thumbnail?.getImageUrl(720)
        genre = tags.filter { it.tagKind == TagKind.GENRE }.joinToString { it.name }
    }

    override fun latestUpdatesRequest(page: Int): Request {
        filterInfo.fetchIfNecessaryInBackground()
        return POST(
            endpoint("CollectionService/SearchV2"),
            headers,
            SearchRequestDto(
                limitOffset = LimitOffsetDto(PAGE_SIZE, PAGE_SIZE * (page - 1)),
                sort = SortDto.LAST_UPDATED,
                formats = listOf(SeriesFormat.MANGA),
                filters = listOf(),
                searchDomain = SearchPageTypeDto(SearchPageType.Browse()),
            ).toProtoRequestBody(),
        )
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        filterInfo.fetchIfNecessaryInBackground()
        return POST(
            endpoint("CollectionService/SearchV2"),
            headers,
            SearchRequestDto(
                limitOffset = LimitOffsetDto(PAGE_SIZE, PAGE_SIZE * (page - 1)),
                query = query,
                sort = SortDto.LAST_UPDATED,
                formats = listOf(SeriesFormat.MANGA),
                filters = listOf(),
                searchDomain = SearchPageTypeDto(SearchPageType.Browse()),
            ).let {
                filters.list
                    .filterIsInstance<SearchFilter>()
                    .fold(it) { acc, filter -> filter.process(acc) }
            }.toProtoRequestBody(),
        )
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        // Detect manga from the old extension. This logic can probably be removed at some point,
        // but it is useful to assist existing users with the migration.
        if (manga.url.startsWith("/de") || manga.url.endsWith("/")) {
            throw Exception("This manga is from old BookWalker and needs to be migrated")
        }
        return super.fetchMangaDetails(manga)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = POST(
        endpoint("ContentService/Details"),
        headers,
        MangaDetailsRequestDto(manga.url.toMangaId()).toProtoRequestBody(),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val details = response.parseProtoAs<MangaDetailsResponseDto>()

        return details.info.toSManga().apply {
            status = details.status
            description = "${details.tagline}\n\n${details.description}"
            author = details.metadata.find { it.name == "AUTHOR" }?.contents?.joinToString { it.name }
            artist = details.metadata.find { it.name == "ARTIST" }?.contents?.joinToString { it.name }
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.concat(
        listOf(ChapterType.VOLUMES, ChapterType.CHAPTERS).map {
            val request = POST(
                endpoint("ContentService/Children"),
                headers,
                ChaptersRequestDto(manga.url.toMangaId(), it).toProtoRequestBody(),
            )
            client.newCall(request).asObservableSuccess()
        },
    ).toList().map { responses ->
        responses.flatMap { response ->
            val chapters = response.parseProtoAs<ChaptersResponseDto>().chapters
            chapters.mapNotNull {
                SChapter.create().apply {
                    // Because preview chapters have a smaller number of pages, we want to avoid a scenario
                    // where a user starts reading a chapter preview, then decides to purchase the chapter,
                    // and then finds that the app is still only showing them up to the end of the preview.
                    // By giving owned chapters a different URL, we can hint to the app that they are
                    // "different" chapters, and it should therefore fetch a new page list.
                    url = it.getUrl() + "?$CHAPTER_PREVIEW_QUERY_PARAM=${!it.isOwned}"
                    val suffix =
                        if (!it.releaseInfo.isReleased) {
                            if (!filterChapters.includes(FilterChaptersPref.ALL)) {
                                return@mapNotNull null
                            }
                            " $PREORDER_ICON"
                        } else if (it.isOwned) {
                            ""
                        } else if (it.currentPrice == 0) {
                            " $FREE_ICON"
                        } else {
                            if (!filterChapters.includes(FilterChaptersPref.OBTAINABLE)) {
                                return@mapNotNull null
                            }
                            " $PURCHASE_ICON"
                        }
                    name = it.title + suffix
                    chapter_number = it.chapterNumber.number.toFloatOrNull() ?: -1f
                    date_upload = it.releaseInfo.releaseDate.value * 1000
                }
            }.asReversed()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url
    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBefore("?")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = rxSingle {
        val readerUrl = getChapterUrl(chapter)
        val pageCount = readerManager.getReader(readerUrl).contents.getPageCount()

        IntRange(0, pageCount - 1).map {
            // The page index query parameter exists only to prevent the app from trying to
            // be smart about caching by page URLs, since the URL is the same for all the pages.
            // It doesn't do anything, and in fact gets stripped back out in imageRequest.
            Page(
                it,
                imageUrl = readerUrl.toHttpUrl().newBuilder()
                    .setQueryParameter(PAGE_INDEX_QUERY_PARAM, it.toString())
                    .build()
                    .toString(),
            )
        }
    }.toObservable()

    override fun imageRequest(page: Page): Request {
        // This URL doesn't actually contain the image. It will be intercepted, and the actual image
        // will be extracted from a webview of the URL being sent here.
        val imageUrl = page.imageUrl!!.toHttpUrl()
        return GET(
            imageUrl.newBuilder()
                .removeAllQueryParameters(CHAPTER_PREVIEW_QUERY_PARAM)
                .removeAllQueryParameters(PAGE_INDEX_QUERY_PARAM)
                .build()
                .toString(),
            headers.newBuilder()
                .set(HEADER_IS_REQUEST_FROM_EXTENSION, "true")
                .set(HEADER_PAGE_INDEX, imageUrl.queryParameter(PAGE_INDEX_QUERY_PARAM)!!)
                .build(),
        )
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    fun endpoint(operation: String) = "$baseUrl/api/kyon/kyon.v1.$operation"

    private fun MangaInfoDto.getUrl() = "/series/${id.substringAfter("CNT_")}/$slug"

    private fun String.toMangaId() = "CNT_" + substringAfter("/series/").substringBefore("/")

    private fun ChapterDto.getUrl() = "/read/${readId.substringAfter("PRD_")}/$slug"

    private fun <T> rxSingle(dispatcher: CoroutineDispatcher = Dispatchers.IO, block: suspend CoroutineScope.() -> T): Single<T> = Single.create { sub ->
        CoroutineScope(dispatcher).launch {
            try {
                sub.onSuccess(block())
            } catch (e: Throwable) {
                sub.onError(e)
            }
        }
    }

    companion object {
        private val fallbackFilters = listOf(TaggedTriState("Press reset to load filters", ""))

        const val PAGE_SIZE = 60

        private const val PURCHASE_ICON = "\uD83E\uDE99" // coin emoji
        private const val PREORDER_ICON = "\uD83D\uDD51" // two-o-clock emoji
        private const val FREE_ICON = "\uD83C\uDF81" // wrapped present emoji

        private const val CHAPTER_PREVIEW_QUERY_PARAM = "nocache_ispreview"
        private const val PAGE_INDEX_QUERY_PARAM = "nocache_pagenum"
    }
}
