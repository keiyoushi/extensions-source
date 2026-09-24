package eu.kanade.tachiyomi.extension.en.bookwalker

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.ChapterDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.ChapterType
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.ChaptersRequestDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.ChaptersResponseDto
import eu.kanade.tachiyomi.extension.en.bookwalker.dto.FilterInfoDto
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
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.e4p.E4PInterceptor
import keiyoushi.lib.e4p.E4PManifestReader
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseAsProto
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Source
abstract class BookWalker :
    KeiSource(),
    ConfigurableSource,
    BookWalkerPreferences {

    // ID from before the BookWalker migration.

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(E4PInterceptor())
        addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            if (response.code == 400 && request.url.encodedPath == "/api/kyon/kyon.v1.ReadService/Open") {
                throw IOException("Failed to load. You may need to log in via WebView and purchase it to view.")
            }
            response
        }
    }

    private val manifestReader get() = E4PManifestReader(client, headers)

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

    private val filterInfo get() = BookWalkerFilters(this)

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = filterInfo.fetchGenres().toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<FilterInfoDto>>()
            ?: return FilterList(SortFilter)

        return FilterList(
            SortFilter,
            // Currently Webtoons aren't supported in-browser and so are tricky to support here.
            // Once they are working again, this can be re-enabled.
//            FormatFilter,
            TriStateFilter("Genres", "genres", genres.map { TaggedTriState(it.name, it.id) }),
        )
    }

    override suspend fun getPopularManga(page: Int): MangasPage = client.post(
        endpoint("CollectionService/SearchV2"),
        SearchRequestDto(
            limitOffset = LimitOffsetDto(PAGE_SIZE, PAGE_SIZE * (page - 1)),
            sort = SortDto.POPULAR,
            formats = listOf(SeriesFormat.MANGA),
            filters = listOf(),
            searchDomain = SearchPageTypeDto(SearchPageType.Browse()),
        ).toProtoRequestBody(),
    ).toMangasPage()

    private fun Response.toMangasPage(): MangasPage {
        val pageInfo = this.parseAsProto<SearchResponseDto>()
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

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.post(
        endpoint("CollectionService/SearchV2"),
        SearchRequestDto(
            limitOffset = LimitOffsetDto(PAGE_SIZE, PAGE_SIZE * (page - 1)),
            sort = SortDto.NEWEST,
            formats = listOf(SeriesFormat.MANGA),
            filters = listOf(),
            searchDomain = SearchPageTypeDto(SearchPageType.Browse()),
        ).toProtoRequestBody(),
    ).toMangasPage()

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = client.post(
        endpoint("CollectionService/SearchV2"),
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
    ).toMangasPage()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val volumeListRequest = async {
            when {
                fetchChapters -> chapterList(manga, ChapterType.VOLUMES).chapters
                // Only wanted for the cover image here, so don't fail the update over it.
                fetchDetails && useLatestThumbnail ->
                    runCatching { chapterList(manga, ChapterType.VOLUMES).chapters }.getOrNull()
                else -> null
            }
        }

        val mangaDetails = async {
            if (!fetchDetails) return@async manga

            // Main details
            val details = client.post(
                endpoint("ContentService/Details"),
                MangaDetailsRequestDto(manga.url.toMangaId()).toProtoRequestBody(),
            ).parseAsProto<MangaDetailsResponseDto>()

            details.info.toSManga(1200).apply {
                status = details.status

                // Replace simple HTML tags with markdown equivalent.
                description = tagToMarkdown.fold("${details.tagline}\n\n${details.description}") { acc, (from, to) ->
                    acc.replace(from, to)
                }.trim()

                author = details.metadata.find { it.name == "AUTHOR" }?.contents?.joinToString { it.name }
                artist = details.metadata.find { it.name == "ARTIST" }?.contents?.joinToString { it.name }

                if (useLatestThumbnail) {
                    volumeListRequest.await()?.asReversed()?.firstNotNullOfOrNull { it.thumbnail }?.let {
                        thumbnail_url = it.getImageUrl(1200)
                    }
                }
            }
        }

        val chapterList = async {
            if (!fetchChapters) return@async chapters

            val chaptersRequest = async { chapterList(manga, ChapterType.CHAPTERS).chapters }

            listOf(volumeListRequest.await().orEmpty(), chaptersRequest.await())
                .flatMap { chapters ->
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

        SMangaUpdate(
            mangaDetails.await(),
            chapterList.await(),
        )
    }

    private suspend fun chapterList(manga: SManga, chapterType: ChapterType) = client.post(
        endpoint("ContentService/Children"),
        ChaptersRequestDto(manga.url.toMangaId(), chapterType).toProtoRequestBody(),
    ).parseAsProto<ChaptersResponseDto>()

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url
    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBefore("?")

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = "$baseUrl${chapter.url}".toHttpUrl().pathSegments[1]
        val result = client.post(
            endpoint("ReadService/Open"),
            ViewerRequestBody("PRD_$chapterId").toProtoRequestBody(),
        ).parseAsProto<ViewerResponse>().details

        val manifest = result.manifestUrl
        return when (result.mimeType) {
            "application/vnd.e4p.prpb+deflate+vnd.e4p.qst" -> manifestReader.extractPagesFromEncryptedManifest(manifest.toHttpUrl())
            "application/vnd.e4p.prpb" -> manifestReader.extractPagesFromUnencryptedManifest(manifest.toHttpUrl())
            else -> throw Exception("Unknown manifest MIME type $manifest")
        }
    }

    fun endpoint(operation: String) = "$baseUrl/api/kyon/kyon.v1.$operation"

    private fun MangaInfoDto.getUrl() = "/series/${id.substringAfter("CNT_")}/$slug"

    private fun String.toMangaId() = "CNT_" + substringAfter("/series/").substringBefore("/")

    private fun ChapterDto.getUrl() = "/read/${readId.substringAfter("PRD_")}/$slug"

    companion object {
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
