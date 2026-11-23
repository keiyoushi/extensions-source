package eu.kanade.tachiyomi.extension.all.luscious

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import kotlin.time.Duration.Companion.minutes

@Source
abstract class Luscious :
    KeiSource(),
    ConfigurableSource {

    private val lusLang: String get() = toLusLang(lang)

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val apiBaseUrl: String get() = "$baseUrl/graphql/nobatch/"
    private val cdnHost: String = "ah-img.luscious.net"
    private val cdnBaseUrl = "https://$cdnHost/".toHttpUrl()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(videoGifInterceptor)
        .addNetworkInterceptor(rewriteOctetStream)
        .rateLimit(60, 1.minutes) { it.pathSegments.firstOrNull() == "graphql" }

    // a video's gif is at .gif or .jpg, else the still frame passed in the fragment
    private val videoGifInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url
        val name = url.pathSegments.last()
        if (url.host != cdnHost || !name.endsWith(".gif")) return@Interceptor chain.proceed(request)

        val alternatives = listOfNotNull(
            url.newBuilder().setPathSegment(url.pathSize - 1, name.removeSuffix(".gif") + ".jpg").build(),
            url.fragment?.toHttpUrl(),
        )
        var response = chain.proceed(request)
        for (alternative in alternatives) {
            if (response.code != 404) break
            response.close()
            response = chain.proceed(request.newBuilder().url(alternative).build())
        }
        response
    }

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

    override suspend fun getPopularManga(page: Int): MangasPage = getAlbumList(page, getSortFilters(POPULAR_DEFAULT_SORT_STATE, lusLang))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getAlbumList(page, getSortFilters(LATEST_DEFAULT_SORT_STATE, lusLang))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getAlbumList(page, filters, query)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.endsWith(baseUrl.toHttpUrl().host.substringAfter("."))) return null
        if (url.pathSegments.getOrNull(0) != "albums") return null
        val album = url.pathSegments.getOrNull(1) ?: return null
        val id = album.split("_").last()

        return detailsParse(getAlbumInfo(id))
    }

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

                    val languageIds = listOfNotNull(lusLang.takeIf { it != FILTER_VALUE_IGNORE }) + languagesFilter.selected
                    if (languageIds.isNotEmpty()) {
                        add(Filter(name = "language_ids", value = languageIds.joinToString("") { "+$it" }))
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

        val data = client.get(url, Fetch.XHR).parseAs<AlbumListResponse>()
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

        return client.get(url, Fetch.XHR).parseAs<AlbumGetResponse>().data.album.get
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url.substringAfterLast("_").removeSuffix("/")
        val album = getAlbumInfo(id)

        val chapterList = if (fetchChapters) getChapters(manga, album, chapters) else chapters

        return SMangaUpdate(detailsParse(album).apply { url = manga.url }, chapterList)
    }

    private fun detailsParse(data: FullAlbum): SManga {
        val manga = SManga.create()
        manga.url = data.url
        manga.title = data.title
        manga.thumbnail_url = data.cover.url
        manga.description = "${data.description}\n\nPictures: ${data.numberOfPictures}\nAnimated Pictures: ${data.numberOfAnimatedPictures}"
        val genreList = listOfNotNull(data.language?.title).toMutableList()
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
        manga.genre = genreList.joinToString()

        return manga
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val id = manga.url.substringAfterLast("_").removeSuffix("/")
        val input = SingleIdVariable(id = id)
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "AlbumListRelated")
            .addQueryParameter("query", albumListRelatedQuery)
            .addQueryParameter("variables", input.toJsonString())
            .build()

        val data = client.get(url, Fetch.XHR).parseAs<AlbumRelatedResponse>()
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

    // pictures from..to of the album, 1-based in upload order
    private class Part(val from: Int, val to: Int, val url: String, val date: Long)

    private fun part(chapter: SChapter): Part {
        val url = baseUrl.toHttpUrl().resolve(chapter.url)!!
        // ?chunk=N parts of earlier versions
        val chunk = url.queryParameter("chunk")?.toInt()
        if (chunk != null) {
            return Part((chunk - 1) * PICTURES_PER_CHAPTER + 1, chunk * PICTURES_PER_CHAPTER, chapter.url, chapter.date_upload)
        }
        return Part(url.queryParameter("from")!!.toInt(), url.queryParameter("to")!!.toInt(), chapter.url, chapter.date_upload)
    }

    private fun partUrl(manga: SManga, from: Int, to: Int): String {
        val url = baseUrl.toHttpUrl().resolve(manga.url)!!.newBuilder()
            .addQueryParameter("from", from.toString())
            .addQueryParameter("to", to.toString())
            .build()
        return "${url.encodedPath}?${url.encodedQuery}"
    }

    private fun getChapters(manga: SManga, album: FullAlbum, chapters: List<SChapter>): List<SChapter> {
        val total = album.numberOfPictures
        val created = (album.created?.toLong() ?: 0L) * 1000L
        val modified = (album.modified?.toLong() ?: 0L) * 1000L

        // one-picture chapters of older versions are dropped
        val listed = chapters.filter { it.url.startsWith("/albums/") }.map { chapter ->
            val part = part(chapter)
            // a ?chunk= part past the album's end now ends there, so later pictures get their own chapter
            if (part.to > total && "chunk=" in part.url) {
                val to = maxOf(total, part.from)
                Part(part.from, to, partUrl(manga, part.from, to), part.date)
            } else {
                part
            }
        }

        val listedEnd = listed.maxOfOrNull { it.to } ?: 0
        val added = (listedEnd + 1..total step PICTURES_PER_CHAPTER).map { from ->
            val to = minOf(from + PICTURES_PER_CHAPTER - 1, total)
            // on a first listing only the newest part dates from the album's last upload
            val date = if (listed.isEmpty() && to < total) created else modified
            Part(from, to, partUrl(manga, from, to), date)
        }

        val parts = (listed + added).sortedBy { it.from }
        return parts.mapIndexed { i, part ->
            SChapter.create().apply {
                url = part.url
                name = if (parts.size == 1) "Chapter" else "Part ${i + 1}"
                chapter_number = (i + 1).toFloat()
                date_upload = part.date
            }
        }.reversed()
    }

    private suspend fun getPicturesPage(id: String, display: String, page: Int): AlbumListOwnPicturesList {
        val input = Variables(
            Input(
                display = display,
                page = page,
                itemsPerPage = PICTURES_PER_PAGE,
                filters = listOf(Filter(name = "album_id", value = id)),
            ),
        )
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "AlbumListOwnPictures")
            .addQueryParameter("query", ALBUM_PICTURES_REQUEST_GQL)
            .addQueryParameter("variables", input.toJsonString())
            .build()

        return client.get(url, Fetch.XHR).parseAs<AlbumListOwnPicturesResponse>().data.picture.list
    }

    // pictures from..to, 1-based in the given order
    private suspend fun getPictures(id: String, display: String, from: Int, to: Int): List<Picture> = coroutineScope {
        val pages = (from - 1) / PICTURES_PER_PAGE + 1..(to - 1) / PICTURES_PER_PAGE + 1
        pages.map { page -> async { getPicturesPage(id, display, page).items } }.awaitAll()
            .flatten()
            .drop((from - 1) % PICTURES_PER_PAGE)
            .take(to - from + 1)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // one-picture chapters of older versions
        if (!chapter.url.startsWith("/albums/")) throw Exception("Refresh the chapter list")

        val id = baseUrl.toHttpUrl().resolve(chapter.url)!!.pathSegments[1].substringAfterLast("_")
        val part = part(chapter)
        val total = getAlbumInfo(id).numberOfPictures
        val to = minOf(part.to, total)
        if (part.from > to) return emptyList()

        val pictures = if (to > PICTURES_PER_CHAPTER && total - part.from < PICTURES_PER_CHAPTER) {
            getPictures(id, "date_newest", total - to + 1, total - part.from + 1).asReversed()
        } else {
            getPictures(id, "date_oldest", part.from, to)
        }
        if (pictures.isEmpty()) throw Exception("Log in via WebView to view more than 1000 pictures")

        return pictures.mapIndexed { i, picture ->
            Page(i, imageUrl = getPictureUrl(picture))
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().resolve(chapter.url)!!.newBuilder().query(null).build().toString()

    private fun getPictureUrl(picture: Picture): String = when {
        // gif of a video, unsigned on the image host
        picture.urlToVideo != null -> {
            val video = picture.urlToVideo.resolved()
            val still = picture.thumbnails.largest()?.url?.resolved()
            video.newBuilder()
                .host(cdnHost)
                .query(null)
                .setPathSegment(video.pathSize - 1, video.pathSegments.last().removeSuffix(".mp4") + ".gif")
                .fragment(still?.toString())
                .build()
        }

        getResolutionPref() != "-1" -> {
            val size = RESOLUTION_SIZES.getValue(getResolutionPref())
            (picture.thumbnails.find { it.size == size } ?: picture.thumbnails.largest()!!).url.resolved()
        }

        picture.urlToOriginal != null -> {
            picture.urlToOriginal.resolved()
        }

        else -> {
            picture.thumbnails.largest()!!.url.resolved()
        }
    }.toString()

    private fun List<Cover>.largest(): Cover? = maxByOrNull { it.height * it.width }

    private fun String.resolved(): HttpUrl = cdnBaseUrl.resolve(this)!!

    override fun getFilterList(data: JsonElement?): FilterList = getSortFilters(POPULAR_DEFAULT_SORT_STATE, lusLang)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val resolutionPref = ListPreference(screen.context).apply {
            key = "${RESOLUTION_PREF_KEY}_$lang"
            title = RESOLUTION_PREF_TITLE
            entries = RESOLUTION_PREF_ENTRIES
            entryValues = RESOLUTION_PREF_ENTRY_VALUES
            setDefaultValue(RESOLUTION_PREF_DEFAULT_VALUE)
            summary = "%s"
        }
        screen.addPreference(resolutionPref)
    }

    private fun getResolutionPref(): String = preferences.getString("${RESOLUTION_PREF_KEY}_$lang", RESOLUTION_PREF_DEFAULT_VALUE)!!
}
