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
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.ViewerRequestBody
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.ViewerResponse
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.e4p.E4PInterceptor
import keiyoushi.lib.e4p.E4PManifestReader
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAsProto
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException

class BookWalker :
    HttpSource(),
    ConfigurableSource,
    BookWalkerPreferences {

    override val name = "BookWalker"

    // ID from before the BookWalker migration.
    override val id = 2744810059574599668

    override val baseUrl = "https://bookwalker.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .addInterceptor(E4PInterceptor())
        .addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 400 && request.url.encodedPath == "/api/kyon/kyon.v1.ReadService/Open") {
                throw IOException("Failed to load. You may need to log in via WebView and purchase it to view.")
            }
            response
        }
        .build()

    private val manifestReader = E4PManifestReader(client, headers)

    private val preferences by getPreferencesLazy()

    // Currently BookWalker does not support listing owned items by series anymore, but if in
    // the future that capability is added, it would be desirable to add this option back.
//    override val showLibraryInPopular
//        get() = preferences.getBoolean(PREF_SHOW_LIBRARY_IN_POPULAR, false)

    override val filterChapters
        get() = FilterChaptersPref.fromKey(
            preferences.getString(
                FilterChaptersPref.PREF_KEY,
                FilterChaptersPref.defaultOption.key,
            )!!,
        )

    override val useLatestThumbnail: Boolean
        get() = preferences.getBoolean(PREF_USE_LATEST_THUMBNAIL, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
//        SwitchPreferenceCompat(screen.context).apply {
//            key = PREF_SHOW_LIBRARY_IN_POPULAR
//            title = "Show My Library in Popular"
//            summary = "Show your library instead of popular manga when browsing \"Popular\"."
//
//            setDefaultValue(false)
//        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_USE_LATEST_THUMBNAIL
            title = "Use Latest Volume Cover For Thumbnail"
            summary = "This does not affect browsing or series that don't have any volumes."

            setDefaultValue(false)
        }.also(screen::addPreference)

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
    }

    private val filterInfo by lazy { BookWalkerFilters(this) }

    override fun getFilterList(): FilterList {
        filterInfo.fetchIfNecessaryInBackground()

        return FilterList(
            SortFilter,
            // Currently Webtoons aren't supported in-browser and so are tricky to support here.
            // Once they are working again, this can be re-enabled.
//            FormatFilter,
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
        val pageInfo = response.parseAsProto<SearchResponseDto>()
        val results = pageInfo.results.value
        return MangasPage(
            results.map { it.value.toSManga(720) },
            pageInfo.countInfo.totalCount > pageInfo.countInfo.offset + pageInfo.countInfo.limit,
        )
    }

    private fun MangaInfoDto.toSManga(thumbnailResolution: Int) = SManga.create().apply {
        url = getUrl()
        title = this@toSManga.title
        thumbnail_url = thumbnail?.getImageUrl(thumbnailResolution)
        genre = tags.filter { it.tagKind == TagKind.GENRE }.joinToString { it.name }
    }

    override fun latestUpdatesRequest(page: Int): Request {
        filterInfo.fetchIfNecessaryInBackground()
        return POST(
            endpoint("CollectionService/SearchV2"),
            headers,
            SearchRequestDto(
                limitOffset = LimitOffsetDto(PAGE_SIZE, PAGE_SIZE * (page - 1)),
                sort = SortDto.NEWEST,
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
        checkOldBookWalker(manga)
        return Observable.zip(
            // Main details
            client.newCall(
                POST(
                    endpoint("ContentService/Details"),
                    headers,
                    MangaDetailsRequestDto(manga.url.toMangaId()).toProtoRequestBody(),
                ),
            ).asObservableSuccess().map { it.parseAsProto<MangaDetailsResponseDto>() },
            // Volume list for cover images
            if (useLatestThumbnail) {
                client.newCall(chapterListRequest(manga, ChapterType.VOLUMES)).asObservableSuccess().onErrorReturn { null }
            } else {
                Observable.just(null)
            }.map { it?.parseAsProto<ChaptersResponseDto>()?.chapters },
        ) { details, volumeList ->
            details.info.toSManga(1200).apply {
                status = details.status

                // Replace simple HTML tags with markdown equivalent.
                description = tagToMarkdown.fold("${details.tagline}\n\n${details.description}") { acc, (from, to) ->
                    acc.replace(from, to)
                }.trim()

                author = details.metadata.find { it.name == "AUTHOR" }?.contents?.joinToString { it.name }
                artist = details.metadata.find { it.name == "ARTIST" }?.contents?.joinToString { it.name }

                // volumeList will be null if useLastestThumbnail is false, so no need to check again
                volumeList?.asReversed()?.firstNotNullOfOrNull { it.thumbnail }?.let {
                    thumbnail_url = it.getImageUrl(1200)
                }
            }
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val details = response.parseAsProto<MangaDetailsResponseDto>()

        return details.info.toSManga(1200).apply {
            status = details.status
            description = "${details.tagline}\n\n${details.description}"
            author = details.metadata.find { it.name == "AUTHOR" }?.contents?.joinToString { it.name }
            artist = details.metadata.find { it.name == "ARTIST" }?.contents?.joinToString { it.name }
        }
    }

    private fun chapterListRequest(manga: SManga, chapterType: ChapterType) = POST(
        endpoint("ContentService/Children"),
        headers,
        ChaptersRequestDto(manga.url.toMangaId(), chapterType).toProtoRequestBody(),
    )

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        checkOldBookWalker(manga)
        return Observable.concat(
            listOf(ChapterType.VOLUMES, ChapterType.CHAPTERS).map {
                client.newCall(chapterListRequest(manga, it)).asObservableSuccess()
            },
        ).toList().map { responses ->
            responses.flatMap { response ->
                val chapters = response.parseAsProto<ChaptersResponseDto>().chapters
                chapters.mapNotNull {
                    if (!it.releaseInfo.isAvailable) {
                        return@mapNotNull null
                    }

                    SChapter.create().apply {
                        url = it.getUrl()
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
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url
    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBefore("?")

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = "$baseUrl${chapter.url}".toHttpUrl().pathSegments[1]
        return POST(
            endpoint("ReadService/Open"),
            headers,
            ViewerRequestBody("PRD_$chapterId").toProtoRequestBody(),
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAsProto<ViewerResponse>().details
        val manifest = result.manifestUrl
        return when (result.mimeType) {
            "application/vnd.e4p.prpb+deflate+vnd.e4p.qst" -> manifestReader.extractPagesFromEncryptedManifest(manifest.toHttpUrl())
            "application/vnd.e4p.prpb" -> manifestReader.extractPagesFromUnencryptedManifest(manifest.toHttpUrl())
            else -> throw Exception("Unknown manifest MIME type $manifest")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun checkOldBookWalker(manga: SManga) {
        // This logic can probably be removed at some point, but it is useful to assist existing users with the migration.
        if (manga.url.startsWith("/de") || manga.url.endsWith("/")) {
            throw Exception("This manga is from old BookWalker and needs to be migrated")
        }
    }

    fun endpoint(operation: String) = "$baseUrl/api/kyon/kyon.v1.$operation"

    private fun MangaInfoDto.getUrl() = "/series/${id.substringAfter("CNT_")}/$slug"

    private fun String.toMangaId() = "CNT_" + substringAfter("/series/").substringBefore("/")

    private fun ChapterDto.getUrl() = "/read/${readId.substringAfter("PRD_")}/$slug"

    companion object {
        private val fallbackFilters = listOf(TaggedTriState("Press reset to load filters", ""))

        private const val PAGE_SIZE = 60

        private const val PURCHASE_ICON = "\uD83D\uDCB5" // dollar bill emoji
        private const val PREORDER_ICON = "\uD83D\uDD51" // two-o-clock emoji
        private const val FREE_ICON = "\uD83C\uDF81" // wrapped present emoji

        private val tagToMarkdown = listOf(
            "<BR>".toRegex(RegexOption.IGNORE_CASE) to "\n",
            "</?P>".toRegex(RegexOption.IGNORE_CASE) to "\n",
            "</?B>".toRegex(RegexOption.IGNORE_CASE) to "**",
            "</?I>".toRegex(RegexOption.IGNORE_CASE) to "_",
        )
    }
}
