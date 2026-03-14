package eu.kanade.tachiyomi.extension.all.luscious

import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import rx.Observable

abstract class Luscious(
    final override val lang: String,
) : HttpSource(),
    ConfigurableSource {

    override val supportsLatest: Boolean = true
    override val name: String = "Luscious"
    val lusLang: String = toLusLang(lang)

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val baseUrl: String = getMirrorPref()!!

    private val apiBaseUrl: String = "$baseUrl/graphql/nobatch/"

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override val client: OkHttpClient
        get() = network.cloudflareClient.newBuilder()
            .addNetworkInterceptor(rewriteOctetStream)
            .build()

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
        val sortByFilter = filters.findInstance<SortBySelectFilter>()!!
        val albumTypeFilter = filters.findInstance<AlbumTypeSelectFilter>()!!
        val selectionFilter = filters.findInstance<SelectionSelectFilter>()!!
        val interestsFilter = filters.findInstance<InterestGroupFilter>()!!
        val languagesFilter = filters.findInstance<LanguageGroupFilter>()!!
        val tagsFilter = filters.findInstance<TagTextFilters>()!!
        val creatorFilter = filters.findInstance<CreatorTextFilters>()!!
        val favoriteFilter = filters.findInstance<FavoriteTextFilters>()!!
        val genreFilter = filters.findInstance<GenreGroupFilter>()!!
        val contentTypeFilter = filters.findInstance<ContentTypeSelectFilter>()!!
        val albumSizeFilter = filters.findInstance<AlbumSizeSelectFilter>()!!
        val restrictGenresFilter = filters.findInstance<RestrictGenresSelectFilter>()!!
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

    private fun buildAlbumListRequest(page: Int, filters: FilterList, query: String = ""): Request {
        val input = buildAlbumListRequestInput(page, filters, query)
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "AlbumList")
            .addQueryParameter("query", ALBUM_LIST_REQUEST_GQL)
            .addQueryParameter("variables", input.toJsonString())
            .build()
        return GET(url, headers)
    }

    private fun parseAlbumListResponse(response: Response): MangasPage {
        val data = response.parseAs<AlbumListResponse>()
        with(data.data.album.list) {
            return MangasPage(
                this.items.map {
                    SManga.create().apply {
                        url = it.url
                        title = it.title
                        thumbnail_url = it.cover.url
                    }
                },
                this.info.hasNextPage,
            )
        }
    }

    private fun buildAlbumInfoRequestInput(id: String): SingleIdVariable = SingleIdVariable(
        id = id,
    )

    private fun buildAlbumInfoRequest(id: String): Request {
        val input = buildAlbumInfoRequestInput(id)
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "AlbumGet")
            .addQueryParameter("query", albumInfoQuery)
            .addQueryParameter("variables", input.toJsonString())
            .build()
        return GET(url, headers)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = buildAlbumListRequest(page, getSortFilters(LATEST_DEFAULT_SORT_STATE, lusLang))

    override fun latestUpdatesParse(response: Response): MangasPage = parseAlbumListResponse(response)

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val id = manga.url.substringAfterLast("_").removeSuffix("/")

        return client.newCall(GET(buildAlbumPicturesPageUrl(id, 1)))
            .asObservableSuccess()
            .map { parseAlbumPicturesResponse(it, manga.url) }
    }

    private fun getPictureUrl(picture: Picture) = when {
        getResolutionPref() != "-1" -> {
            picture.thumbnails[getResolutionPref()?.toInt()!!].url
        }

        picture.urlToVideo != null -> {
            picture.urlToVideo.toHttpUrl().newBuilder().host("ah-img.luscious.net").build().toString().replace(".mp4", ".gif")
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

    private fun parseAlbumPicturesResponse(response: Response, mangaUrl: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        val id = response.request.url.queryParameter("variables").toString().parseAs<Variables>().input.filters
            .first { it.name == "album_id" }.value

        var data = response.parseAs<AlbumListOwnPicturesResponse>()

        when (getMergeChapterPref()) {
            true -> {
                val chapter = SChapter.create()
                chapter.url = mangaUrl
                chapter.name = "Merged Chapter"
                chapter.date_upload = data.data.picture.list.items[0].created.toLong()
                chapter.chapter_number = 1F
                chapters.add(chapter)
            }

            false -> {
                var nextPage = true
                var page = 2

                while (nextPage) {
                    nextPage = data.data.picture.list.info.hasNextPage
                    val pictureItems = parsePictures(data)
                    pictureItems.forEach {
                        val chapter = SChapter.create().apply {
                            url = it.url
                            chapter_number = it.index.toFloat()
                            name = "${it.index} - ${it.title}"
                            date_upload = (it.created ?: 0) * 1000
                        }
                        chapters.add(chapter)
                    }
                    if (nextPage) {
                        val newPage = client.newCall(GET(buildAlbumPicturesPageUrl(id, page))).execute()
                        data = newPage.parseAs<AlbumListOwnPicturesResponse>()
                    }
                    page++
                }
            }
        }

        return chapters.reversed()
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // Pages

    private fun buildAlbumPicturesRequestInput(id: String, page: Int): Variables = Variables(
        input = Input(
            filters = listOf(
                Filter(name = "album_id", value = id),
            ),
            display = getSortPref(),
            page = page,
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

    private fun parseAlbumPicturesResponseMergeChapter(response: Response): List<Page> {
        val pages = mutableListOf<Page>()
        var nextPage = true
        var page = 2
        val id = response.request.url.queryParameter("variables").toString().parseAs<Variables>().input.filters
            .first { it.name == "album_id" }.value

        var data = response.parseAs<AlbumListOwnPicturesResponse>()

        while (nextPage) {
            nextPage = data.data.picture.list.info.hasNextPage
            val pictureItems = parsePictures(data)
            pages.addAll(pictureItems.map { Page(it.index, it.url, it.url) })
            if (nextPage) {
                val newPage = client.newCall(GET(buildAlbumPicturesPageUrl(id, page))).execute()
                data = newPage.parseAs<AlbumListOwnPicturesResponse>()
            }
            page++
        }
        return pages
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = when (getMergeChapterPref()) {
        true -> {
            val id = chapter.url.substringAfterLast("_").removeSuffix("/")

            client.newCall(GET(buildAlbumPicturesPageUrl(id, 1)))
                .asObservableSuccess()
                .map { parseAlbumPicturesResponseMergeChapter(it) }
        }

        false -> {
            Observable.just(listOf(Page(0, chapter.url, chapter.url)))
        }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun fetchImageUrl(page: Page): Observable<String> {
        if (page.imageUrl != null) {
            return Observable.just(page.imageUrl)
        }

        return client.newCall(GET(page.url, headers))
            .asObservableSuccess()
            .map {
                val data = it.parseAs<AlbumListOwnPicturesResponse>().data.picture.list
                getPictureUrl(data.items[page.index % 50])
            }
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val id = manga.url.substringAfterLast("_").removeSuffix("/")
        return client.newCall(buildAlbumInfoRequest(id))
            .asObservableSuccess()
            .map { detailsParse(it) }
    }

    private fun detailsParse(response: Response): SManga {
        val data = response.parseAs<AlbumGetResponse>().data.album.get
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

    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    // Popular

    override fun popularMangaParse(response: Response): MangasPage = parseAlbumListResponse(response)

    override fun popularMangaRequest(page: Int): Request = buildAlbumListRequest(page, getSortFilters(POPULAR_DEFAULT_SORT_STATE, lusLang))

    // Search

    override fun searchMangaParse(response: Response): MangasPage = parseAlbumListResponse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = buildAlbumListRequest(
        page,
        filters.let {
            if (it.isEmpty()) {
                getSortFilters(SEARCH_DEFAULT_SORT_STATE, lusLang)
            } else {
                it
            }
        },
        query,
    )

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith("ID:")) {
        val id = query.substringAfterLast("ID:")
        client.newCall(buildAlbumInfoRequest(id))
            .asObservableSuccess()
            .map { MangasPage(listOf(detailsParse(it)), false) }
    } else if (query.startsWith("ALBUM:")) {
        val album = query.substringAfterLast("ALBUM:")
        val id = album.split("_").last()
        client.newCall(buildAlbumInfoRequest(id))
            .asObservableSuccess()
            .map { MangasPage(listOf(detailsParse(it)), false) }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    override fun getFilterList(): FilterList = getSortFilters(POPULAR_DEFAULT_SORT_STATE, lusLang)

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
        val mirrorPref = ListPreference(screen.context).apply {
            key = "${MIRROR_PREF_KEY}_$lang"
            title = MIRROR_PREF_TITLE
            entries = MIRROR_PREF_ENTRIES
            entryValues = MIRROR_PREF_ENTRY_VALUES
            setDefaultValue(MIRROR_PREF_DEFAULT_VALUE)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString("${MIRROR_PREF_KEY}_$lang", entry).commit()
            }
        }
        screen.addPreference(resolutionPref)
        screen.addPreference(sortPref)
        screen.addPreference(mergeChapterPref)
        screen.addPreference(mirrorPref)
    }

    fun getMergeChapterPref(): Boolean = preferences.getBoolean("${MERGE_CHAPTER_PREF_KEY}_$lang", MERGE_CHAPTER_PREF_DEFAULT_VALUE)
    fun getResolutionPref(): String? = preferences.getString("${RESOLUTION_PREF_KEY}_$lang", RESOLUTION_PREF_DEFAULT_VALUE)
    fun getSortPref(): String? = preferences.getString("${SORT_PREF_KEY}_$lang", SORT_PREF_DEFAULT_VALUE)
    fun getMirrorPref(): String? = preferences.getString("${MIRROR_PREF_KEY}_$lang", MIRROR_PREF_DEFAULT_VALUE)
}
