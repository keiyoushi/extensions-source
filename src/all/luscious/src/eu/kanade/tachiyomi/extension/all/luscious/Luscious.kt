package eu.kanade.tachiyomi.extension.all.luscious

import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import kotlin.math.ceil

@Source
abstract class Luscious :
    KeiSource(),
    ConfigurableSource {

    val lusLang: String get() = toLusLang(lang)

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val apiBaseUrl: String get() = "$baseUrl/graphql/nobatch/"
    private val cdnHost: String = "ah-img.luscious.net"

    override fun OkHttpClient.Builder.configureClient() = addNetworkInterceptor(rewriteOctetStream)

    private val rewriteOctetStream: Interceptor = Interceptor { chain ->
        val originalResponse: Response = chain.proceed(chain.request())
        if (originalResponse.headers("Content-Type").contains("application/octet-stream") && originalResponse.request.url.toString()
                .contains(".webp")
        ) {
            val orgBody = originalResponse.body.source()
            val newBody = orgBody.asResponseBody("image/webp".toMediaType())
            originalResponse.newBuilder()
                .body(newBody)
                .build()
        } else {
            originalResponse
        }
    }

    // Common
    private fun buildAlbumListRequestInput(page: Int, filters: FilterList, query: String = ""): Variables {
        val sortByFilter = filters.firstInstance<SortBySelectFilter>()
        val albumTypeFilter = filters.firstInstance<AlbumTypeSelectFilter>()
        val selectionFilter = filters.firstInstance<SelectionSelectFilter>()
        val interestsFilter = filters.firstInstance<InterestGroupFilter>()
        val languagesFilter = filters.firstInstance<LanguageGroupFilter>()
        val tagsFilter = filters.firstInstance<TagTextFilters>()
        val creatorFilter = filters.firstInstance<CreatorTextFilters>()
        val favoriteFilter = filters.firstInstance<FavoriteTextFilters>()
        val genreFilter = filters.firstInstance<GenreGroupFilter>()
        val contentTypeFilter = filters.firstInstance<ContentTypeSelectFilter>()
        val albumSizeFilter = filters.firstInstance<AlbumSizeSelectFilter>()
        val restrictGenresFilter = filters.firstInstance<RestrictGenresSelectFilter>()
        return Variables(
            Input(
                display = sortByFilter.selected,
                page = page,
                filters = mutableListOf<Filter>().apply {
                    if (contentTypeFilter.selected != FILTER_VALUE_IGNORE) {
                        add(contentTypeFilter.toJsonObject("content_id"))
                    }

                    if (albumTypeFilter.selected != FILTER_VALUE_IGNORE) {
                        add(albumTypeFilter.toJsonObject("album_type"))
                    }

                    if (selectionFilter.selected != FILTER_VALUE_IGNORE) {
                        add(selectionFilter.toJsonObject("selection"))
                    }

                    if (albumSizeFilter.selected != FILTER_VALUE_IGNORE) {
                        add(albumSizeFilter.toJsonObject("picture_count_rank"))
                    }

                    if (restrictGenresFilter.selected != FILTER_VALUE_IGNORE) {
                        add(restrictGenresFilter.toJsonObject("restrict_genres"))
                    }

                    with(interestsFilter) {
                        if (this.selected.isEmpty()) {
                            throw Exception("Please select an Interest")
                        }
                        add(this.toJsonObject("audience_ids"))
                    }

                    if (lusLang != FILTER_VALUE_IGNORE) {
                        add(
                            Filter(name = "language_ids", value = "+" + languagesFilter.selected.joinToString("+")),
                        )
                    }

                    if (tagsFilter.state.isNotEmpty()) {
                        val tags = "+${tagsFilter.state.lowercase()}".replace(" ", "_")
                            .replace("_,", "+").replace(",_", "+").replace(",", "+")
                            .replace("+-", "-").replace("-_", "-").trim()
                        add(
                            Filter(
                                name = "tagged",
                                value = tags,
                            ),
                        )
                    }

                    if (creatorFilter.state.isNotEmpty()) {
                        add(
                            Filter(
                                name = "created_by_id",
                                value = creatorFilter.state,
                            ),
                        )
                    }

                    if (favoriteFilter.state.isNotEmpty()) {
                        add(
                            Filter(
                                name = "favorite_by_user_id",
                                value = favoriteFilter.state,
                            ),
                        )
                    }

                    if (genreFilter.anyNotIgnored()) {
                        add(genreFilter.toJsonObject("genre_ids"))
                    }

                    if (query != "") {
                        add(
                            Filter(
                                name = "search_query",
                                value = query,
                            ),
                        )
                    }
                },
            ),
        )
    }

    private suspend fun getAlbumList(page: Int, filters: FilterList, query: String = ""): MangasPage {
        val input = buildAlbumListRequestInput(page, filters, query)
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "AlbumList")
            .addQueryParameter("query", ALBUM_LIST_REQUEST_GQL)
            .addQueryParameter("variables", input.toJsonString())
            .build()

        val data = client.get(url).parseAs<AlbumListResponse>()
        with(data.data.album.list) {
            return MangasPage(
                this.items.map {
                    SManga.create().apply {
                        this.url = it.url
                        title = it.title
                        thumbnail_url = it.cover.url
                    }
                },
                this.info.hasNextPage,
            )
        }
    }

    private suspend fun getAlbumInfo(id: String): FullAlbum {
        val input = SingleIdVariable(id = id)
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "AlbumGet")
            .addQueryParameter("query", albumInfoQuery)
            .addQueryParameter("variables", input.toJsonString())
            .build()

        return client.get(url).parseAs<AlbumGetResponse>().data.album.get
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage = getAlbumList(page, getSortFilters(LATEST_DEFAULT_SORT_STATE, lusLang))

    // Chapters

    private suspend fun getChapters(id: String, album: FullAlbum): List<SChapter> {
        val totalPictures = album.numberOfPictures

        val chapters = mutableListOf<SChapter>()
        var page = 1
        var hasMore = true

        while (hasMore) {
            val data = client.get(buildAlbumPicturesPageUrl(id, page)).parseAs<AlbumListOwnPicturesResponse>()
            val pictureItems = parsePictures(data)

            if (pictureItems.isEmpty()) {
                hasMore = false
            } else {
                pictureItems.forEach {
                    val chapter = SChapter.create().apply {
                        chapter_number = it.index.toFloat()
                        name = "${it.index} - ${it.title}"
                        date_upload = (it.created ?: 0L) * 1000L
                    }
                    chapter.setUrlWithoutDomain(it.url)
                    chapters.add(chapter)
                }

                // API natively caps `total_items` tracking to 1000 so we override that by
                // directly tracking standard math iteration against the true `numberOfPictures`
                if (page * 50 >= totalPictures || data.data.picture.list.items.isEmpty()) {
                    hasMore = false
                } else {
                    page++
                }
            }
        }
        return chapters.reversed()
    }

    private fun getMergedChapters(manga: SManga, album: FullAlbum): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val chunkCount = ceil(album.numberOfPictures / 1000.0).toInt().coerceAtLeast(1)

        for (i in 1..chunkCount) {
            val chapter = SChapter.create()
            chapter.url = "${manga.url}?chunk=$i"
            chapter.name = if (chunkCount == 1) "Merged Chapter" else "Merged Chapter (Part $i)"
            chapter.chapter_number = i.toFloat()
            chapter.date_upload = (album.created?.toLong() ?: 0L) * 1000L
            chapters.add(chapter)
        }
        return chapters.reversed()
    }

    private fun getPictureUrl(picture: Picture) = when {
        getResolutionPref() != "-1" -> {
            picture.thumbnails[getResolutionPref()?.toInt()!!].url
        }

        picture.urlToVideo != null -> {
            picture.urlToVideo.replace(".mp4", ".gif")
        }

        picture.urlToOriginal != null -> {
            picture.urlToOriginal
        }

        else -> {
            picture.thumbnails.maxByOrNull { thumbnail ->
                thumbnail.height * thumbnail.width
            }!!.url
        }
    }

    private fun parsePictures(data: AlbumListOwnPicturesResponse): List<PictureItem> {
        val items = mutableListOf<PictureItem>()

        data.data.picture.list.items.forEach {
            val index = it.position
            val url = getPictureUrl(it)

            items.add(PictureItem(index, if (url.startsWith("//")) "https:$url" else url, it.title, it.created.toLong()))
        }

        return items
    }

    // Pages

    private fun buildAlbumPicturesRequestInput(id: String, page: Int): Variables = Variables(
        input = Input(
            filters = listOf(
                Filter(name = "album_id", value = id),
            ),
            display = getSortPref(),
            page = page,
            itemsPerPage = 50,
        ),
    )

    private fun buildAlbumPicturesPageUrl(id: String, page: Int): HttpUrl {
        val input = buildAlbumPicturesRequestInput(id, page)
        return apiBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "AlbumListOwnPictures")
            .addQueryParameter("query", ALBUM_PICTURES_REQUEST_GQL)
            .addQueryParameter("variables", input.toJsonString())
            .build()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (!chapter.url.startsWith("/albums/")) {
            return listOf(Page(0, imageUrl = "https://$cdnHost${chapter.url}"))
        }

        val chunk = chapter.url.substringAfter("?chunk=", "1").substringBefore("#").toIntOrNull() ?: 1
        val id = chapter.url.substringBefore("?").substringAfterLast("_").removeSuffix("/")

        val pages = mutableListOf<Page>()
        val startPage = (chunk - 1) * 20 + 1
        val endPage = chunk * 20

        for (page in startPage..endPage) {
            val data = client.get(buildAlbumPicturesPageUrl(id, page)).parseAs<AlbumListOwnPicturesResponse>()
            val pictureItems = parsePictures(data)

            if (pictureItems.isEmpty()) break

            pictureItems.forEach {
                pages.add(Page(pages.size, imageUrl = it.url.toHttpUrl().newBuilder().host(cdnHost).build().toString()))
            }

            if (pictureItems.size < 50) break
        }
        return pages
    }

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("/albums/")) {
        "$baseUrl${chapter.url.substringBefore("?")}"
    } else {
        "https://$cdnHost${chapter.url}"
    }

    // Details

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url.substringAfterLast("_").removeSuffix("/")
        val album = getAlbumInfo(id)

        val chapterList = when {
            getMergeChapterPref() -> getMergedChapters(manga, album)
            fetchChapters -> getChapters(id, album)
            else -> chapters
        }

        return SMangaUpdate(detailsParse(album).apply { url = manga.url }, chapterList)
    }

    private fun detailsParse(data: FullAlbum): SManga {
        val manga = SManga.create()
        manga.url = data.url
        manga.title = data.title
        manga.thumbnail_url = data.cover.url
        manga.status = 0
        manga.description = "${data.description}\n\nPictures: ${data.numberOfPictures}\nAnimated Pictures: ${data.numberOfAnimatedPictures}"
        val genreList = mutableListOf(data.language?.title)
        genreList += data.labels
        genreList += data.genres.map { it.title }
        genreList += data.audiences.map { it.title }
        genreList += data.tags.map { it.text }
        val artist = data.tags.find { it.text.contains("Artist:") }
        if (artist != null) {
            manga.artist = artist.text.substringAfter(":").trim()
            manga.author = manga.artist
        }
        genreList += data.content.title
        manga.genre = genreList.joinToString(", ")

        return manga
    }

    // Related

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val id = manga.url.substringAfterLast("_").removeSuffix("/")
        val input = SingleIdVariable(id = id)
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "AlbumListRelated")
            .addQueryParameter("query", albumListRelatedQuery)
            .addQueryParameter("variables", input.toJsonString())
            .build()

        val data = client.get(url).parseAs<AlbumRelatedResponse>()
        with(data.data.album.listRelated) {
            return listOfNotNull(
                moreLikeThis,
                itemsLikedLikeThis,
                itemsCreatedByThisUser,
            ).flatMap { relatedItems ->
                relatedItems.map {
                    SManga.create().apply {
                        this.url = it.url
                        title = it.title
                        thumbnail_url = it.cover.url
                    }
                }
            }
        }
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage = getAlbumList(page, getSortFilters(POPULAR_DEFAULT_SORT_STATE, lusLang))

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getAlbumList(page, filters, query)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.endsWith(baseUrl.toHttpUrl().host.substringAfter("."))) return null
        if (url.pathSegments.getOrNull(0) != "albums") return null
        val album = url.pathSegments.getOrNull(1) ?: return null
        val id = album.split("_").last()

        return detailsParse(getAlbumInfo(id))
    }

    override fun getFilterList(data: JsonElement?): FilterList = getSortFilters(POPULAR_DEFAULT_SORT_STATE, lusLang)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val resolutionPref = ListPreference(screen.context).apply {
            key = "${RESOLUTION_PREF_KEY}_$lang"
            title = RESOLUTION_PREF_TITLE
            entries = RESOLUTION_PREF_ENTRIES
            entryValues = RESOLUTION_PREF_ENTRY_VALUES
            setDefaultValue(RESOLUTION_PREF_DEFAULT_VALUE)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString("${RESOLUTION_PREF_KEY}_$lang", entry).commit()
            }
        }
        val sortPref = ListPreference(screen.context).apply {
            key = "${SORT_PREF_KEY}_$lang"
            title = SORT_PREF_TITLE
            entries = SORT_PREF_ENTRIES
            entryValues = SORT_PREF_ENTRY_VALUES
            setDefaultValue(SORT_PREF_DEFAULT_VALUE)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString("${SORT_PREF_KEY}_$lang", entry).commit()
            }
        }
        val mergeChapterPref = CheckBoxPreference(screen.context).apply {
            key = "${MERGE_CHAPTER_PREF_KEY}_$lang"
            title = MERGE_CHAPTER_PREF_TITLE
            summary = MERGE_CHAPTER_PREF_SUMMARY
            setDefaultValue(MERGE_CHAPTER_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("${MERGE_CHAPTER_PREF_KEY}_$lang", checkValue).commit()
            }
        }
        screen.addPreference(resolutionPref)
        screen.addPreference(sortPref)
        screen.addPreference(mergeChapterPref)
    }

    fun getMergeChapterPref(): Boolean = preferences.getBoolean("${MERGE_CHAPTER_PREF_KEY}_$lang", MERGE_CHAPTER_PREF_DEFAULT_VALUE)
    fun getResolutionPref(): String? = preferences.getString("${RESOLUTION_PREF_KEY}_$lang", RESOLUTION_PREF_DEFAULT_VALUE)
    fun getSortPref(): String? = preferences.getString("${SORT_PREF_KEY}_$lang", SORT_PREF_DEFAULT_VALUE)
}
