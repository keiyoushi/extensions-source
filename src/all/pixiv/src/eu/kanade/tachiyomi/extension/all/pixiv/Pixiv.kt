package eu.kanade.tachiyomi.extension.all.pixiv

import android.content.SharedPreferences
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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Pixiv :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun apiUrl(href: String): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder(href)!!
        .addEncodedQueryParameter("lang", lang)

    private val apiHeaders: Headers
        get() = headers.newBuilder()
            .add("Accept", "application/json")
            .build()

    class PixivApiException(message: String? = null) : Exception(message, null)

    /**
     * Sends the previously constructed API call to the Pixiv API.
     * If the server reports an error, A [PixivApiException] will be
     * returned as a [Result.failure].
     */
    private suspend inline fun <reified T> HttpUrl.Builder.executeApi(): Result<T> {
        val resp = client.get(build(), apiHeaders, ensureSuccess = false).parseAs<PixivApiResponse>()
        if (resp.error) {
            return Result.failure(PixivApiException(resp.message))
        }
        return Result.success(resp.body!!.parseAs<T>())
    }

    private var popularMangaNextPage = 1
    private lateinit var popularMangaBuffer: PagedBuffer<SManga>

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page == 1) {
            val seen = mutableSetOf<String>()
            popularMangaBuffer = PagedBuffer { p ->
                val entries = apiUrl("/touch/ajax/ranking/illust?mode=daily&type=manga")
                    .setEncodedQueryParameter("page", p.toString())
                    .executeApi<PixivRankings>().getOrThrow().ranking!!
                if (entries.isEmpty()) return@PagedBuffer null

                val detailsCall = apiUrl("/touch/ajax/illust/details/many")
                entries.forEach { detailsCall.addEncodedQueryParameter("illust_ids[]", it.illustId!!) }

                detailsCall.executeApi<PixivIllustsDetails>().getOrThrow().illust_details!!.toSManga(seen)
            }

            popularMangaNextPage = 2
        } else {
            require(page == popularMangaNextPage++)
        }

        val mangas = popularMangaBuffer.take(50)
        return MangasPage(mangas, hasNextPage = mangas.isNotEmpty())
    }

    private var searchNextPage = 1
    private var searchHash: Int? = null
    private lateinit var searchBuffer: PagedBuffer<PixivIllust>
    private lateinit var searchPredicates: List<(PixivIllust) -> Boolean>

    private var userSearchNextPage = 1
    private var userSearchHash: Int? = null
    private lateinit var userSearchBuffer: PagedBuffer<SManga>

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = PixivTarget.fromUri(url)?.let { getTargetManga(it) }

    // Deeplink selection of specific IDs: simply fetch the single object
    private suspend fun getTargetManga(target: PixivTarget): SManga? = when (target) {
        is PixivTarget.Illustration -> getIllustCached(target.illustId)?.toSManga()

        is PixivTarget.Series -> {
            // TODO: caching!
            apiUrl("/touch/ajax/illust/series/${target.seriesId}")
                .executeApi<PixivSeriesDetails>().getOrNull()?.series?.toSManga()
        }

        is PixivTarget.User -> {
            val user = getUserCached(target.userId)
            SManga.create().apply {
                url = "/users/${target.userId}"
                title = user?.name ?: "User ${target.userId}"
                thumbnail_url = user?.imageBig
            }
        }
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        PixivTarget.fromSearchQuery(query)?.let { target ->
            return MangasPage(listOfNotNull(getTargetManga(target)), hasNextPage = false)
        }

        val filters = filters.list as PixivFilters

        if (filters.users.isNotBlank()) {
            val hash = filters.users.hashCode()
            if (hash != userSearchHash || page == 1) {
                userSearchHash = hash
                userSearchBuffer = makeUserSearchBuffer(nick = filters.users)
                userSearchNextPage = 2
            } else {
                require(page == userSearchNextPage++)
            }

            val mangas = userSearchBuffer.take(TARGET_RESULTS)
            return MangasPage(mangas, hasNextPage = mangas.isNotEmpty())
        }

        val hash = Pair(query, filters.toList()).hashCode()

        if (hash != searchHash || page == 1) {
            searchHash = hash

            // clear predicates
            searchPredicates = emptyList()

            if (query.isNotBlank()) {
                searchBuffer = makeIllustSearchBuffer(
                    word = query,
                    order = filters.order,
                    mode = filters.rating,
                    sMode = "s_tc",
                    type = filters.type,
                    dateBefore = filters.dateBefore.ifBlank { null },
                    dateAfter = filters.dateAfter.ifBlank { null },
                )

                searchPredicates = buildList {
                    filters.makeTagsPredicate()?.let(::add)
                    filters.makeUsersPredicate()?.let(::add)
                }
            } else {
                searchBuffer = makeIllustSearchBuffer(
                    word = filters.tags.ifBlank { "漫画" },
                    order = filters.order,
                    mode = filters.rating,
                    sMode = filters.searchMode,
                    type = filters.type,
                    dateBefore = filters.dateBefore.ifBlank { null },
                    dateAfter = filters.dateAfter.ifBlank { null },
                )
            }

            searchNextPage = 2
        } else {
            require(page == searchNextPage++)
        }

        val filteredIllusts = if (searchPredicates.isEmpty()) {
            searchBuffer.take(TARGET_RESULTS)
        } else {
            // if we have a filter let's be a little smarter about how to get enough results
            fetchWithAdaptiveWindow(searchBuffer, searchPredicates)
        }

        val mangas = filteredIllusts.toSManga()
        return MangasPage(mangas, hasNextPage = mangas.isNotEmpty())
    }

    // fetch with variable window size - if filter is strong and we're not getting a lot of
    // results, cast a bigger net.
    //
    // this filters post-truncate to avoid the case where a strong filter will cause the search
    // to spin forever and futilely fetch page after page trying to get enough results to return
    private suspend fun fetchWithAdaptiveWindow(
        buffer: PagedBuffer<PixivIllust>,
        predicates: List<(PixivIllust) -> Boolean>,
    ): List<PixivIllust> {
        val sampleIllusts = buffer.take(RESULTS_PER_PAGE)
        val sampleFiltered = sampleIllusts.filter { illust -> predicates.all { p -> p(illust) } }

        val hitRate = if (sampleIllusts.isNotEmpty()) {
            sampleFiltered.size.toDouble() / sampleIllusts.size
        } else {
            0.0
        }
        val estimatedWindow = if (hitRate > 0) {
            (TARGET_RESULTS / hitRate).toInt().coerceIn(RESULTS_PER_PAGE, MAX_WINDOW_SIZE)
        } else {
            MAX_WINDOW_SIZE
        }

        // get estimated rest of unfiltered items needed to hit target results
        val remainingNeeded = (estimatedWindow - RESULTS_PER_PAGE).coerceAtLeast(0)
        val additionalIllusts = if (remainingNeeded > 0) {
            buffer.take(remainingNeeded)
        } else {
            emptyList()
        }

        val allIllusts = sampleIllusts + additionalIllusts
        return allIllusts.filter { illust -> predicates.all { p -> p(illust) } }
    }

    private fun makeIllustSearchBuffer(
        word: String,
        sMode: String,
        order: String?,
        mode: String?,
        type: String?,
        dateBefore: String?,
        dateAfter: String?,
    ): PagedBuffer<PixivIllust> {
        val call = apiUrl("/touch/ajax/search/illusts")

        call.addQueryParameter("word", word)
        call.addEncodedQueryParameter("s_mode", sMode)
        type?.let { call.addEncodedQueryParameter("type", it) }
        order?.let { call.addEncodedQueryParameter("order", it) }
        mode?.let { call.addEncodedQueryParameter("mode", it) }
        dateBefore?.let { call.addEncodedQueryParameter("ecd", it) }
        dateAfter?.let { call.addEncodedQueryParameter("scd", it) }

        return PagedBuffer { p ->
            call.setEncodedQueryParameter("p", p.toString())

            val illusts = call.executeApi<PixivResults>().getOrThrow().illusts!!
            if (illusts.isEmpty()) return@PagedBuffer null

            illusts.filter { illust -> illust.is_ad_container != 1 && illust.type != "2" }
        }
    }

    // search by username
    private fun makeUserSearchBuffer(nick: String): PagedBuffer<SManga> {
        val searchUsers = baseUrl.toHttpUrl().newBuilder("/search/users")!!
            .addQueryParameter("s_mode", "s_usr")
            .addQueryParameter("nick", nick)
            .addQueryParameter("i", "1")
            .addQueryParameter("comment", "")

        // have to use desktop User-Agent to get __NEXT_DATA__ (mobile version is SPA without embedded data)
        val searchHeaders = headers.newBuilder()
            .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .build()

        return PagedBuffer { p ->
            searchUsers.setEncodedQueryParameter("p", p.toString())

            val doc = client.get(searchUsers.build(), searchHeaders, ensureSuccess = false).asJsoup()
            val nextDataScript = doc.selectFirst("script#__NEXT_DATA__")?.data() ?: return@PagedBuffer null

            val pageProps = nextDataScript.parseAs<PixivNextData>().props.pageProps
            val userIds = pageProps.userIds

            if (userIds.isEmpty()) return@PagedBuffer null

            val users = pageProps.userData?.users
            userIds.map { userId ->
                val user = users?.get(userId.toString())
                SManga.create().apply {
                    url = "/users/$userId"
                    title = user?.name ?: "User $userId"
                    thumbnail_url = user?.imageBig
                }
            }
        }
    }

    // lookup directly by user id
    private suspend fun getUserIdIllusts(id: String, type: String?): List<PixivIllust> {
        val fetchUserIllusts = apiUrl("/touch/ajax/user/illusts")
            .apply {
                type?.let { setEncodedQueryParameter("type", it) }
                setEncodedQueryParameter("id", id)
            }

        return buildList {
            for (p in countUp(start = 1)) {
                fetchUserIllusts.setEncodedQueryParameter("p", p.toString())

                val illusts = fetchUserIllusts.executeApi<PixivResults>().getOrThrow().illusts!!
                if (illusts.isEmpty()) break

                addAll(illusts)
            }
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(PixivFilters())

    private fun List<PixivIllust>.toSManga(seriesIdsSeen: MutableSet<String> = mutableSetOf()) = mapNotNull { illust ->
        illust.toSManga().takeIf { seriesIdsSeen.add(it.url) }
    }

    private fun PixivSeries.toSearchResult() = PixivSearchResultSeries(
        id = id,
        title = title,
        userId = userId,
        coverImage = coverImage?.let { if (it.isString) it.content else null },
    )
    private fun PixivIllust.toSManga(): SManga {
        if (series == null) {
            val manga = SManga.create()
            manga.setUrlWithoutDomain("/artworks/${id!!}")
            manga.title = title ?: "(null)"
            manga.thumbnail_url = url
            return manga
        } else {
            val series = series.copy(userId = series.userId ?: author_details?.user_id)
            val manga = series.toSManga().apply {
                thumbnail_url = thumbnail_url ?: this@toSManga.url
            }
            return manga
        }
    }
    private fun PixivSeries.toSManga() = toSearchResult().toSManga()
    private fun PixivSearchResultSeries.toSManga(): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain("/user/${userId!!}/series/$id")
        manga.title = title ?: "(null)"
        manga.thumbnail_url = coverImage
        return manga
    }

    private var latestMangaNextPage = 1
    private lateinit var latestMangaBuffer: PagedBuffer<SManga>

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1) {
            val seen = mutableSetOf<String>()
            val call = apiUrl("/touch/ajax/latest?type=manga")
            latestMangaBuffer = PagedBuffer { p ->
                call.setEncodedQueryParameter("p", p.toString())

                val illusts = call.executeApi<PixivResults>().getOrThrow().illusts!!
                if (illusts.isEmpty()) return@PagedBuffer null

                illusts.filter { it.is_ad_container != 1 }.toSManga(seen)
            }

            latestMangaNextPage = 2
        } else {
            require(page == latestMangaNextPage++)
        }

        val mangas = latestMangaBuffer.take(50)
        return MangasPage(mangas, hasNextPage = mangas.isNotEmpty())
    }

    private val getIllustCached by lazy {
        lruCached<String, PixivIllust>(25) { illustId ->
            apiUrl("/touch/ajax/illust/details?illust_id=$illustId")
                .executeApi<PixivIllustDetails>().getOrNull()?.illust_details
        }
    }

    private val getUserCached by lazy {
        lruCached<String, PixivUserInfo>(25) { userId ->
            apiUrl("/ajax/user/$userId?full=1")
                .executeApi<PixivUserInfo>().getOrNull()
        }
    }

    private val getSeriesIllustsCached by lazy {
        lruCached<String, List<PixivIllust>>(25) { seriesId ->
            val call = apiUrl("/touch/ajax/illust/series_content/$seriesId")
            var lastOrder = 0

            buildList {
                while (true) {
                    call.setEncodedQueryParameter("last_order", lastOrder.toString())

                    val illusts = call.executeApi<PixivSeriesContents>()
                        .getOrElse { return@lruCached null }.series_contents!!
                    if (illusts.isEmpty()) break

                    addAll(illusts)
                    lastOrder += illusts.size
                }
            }
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val target = PixivTarget.fromUri(baseUrl + manga.url)
            ?: return SMangaUpdate(manga, if (fetchChapters) emptyList() else chapters)

        return coroutineScope {
            val details = async { if (fetchDetails) updateMangaDetails(manga, target) else manga }
            val chapterList = async { if (fetchChapters) getChapterList(target) else chapters }

            SMangaUpdate(details.await(), chapterList.await())
        }
    }

    private suspend fun updateMangaDetails(manga: SManga, target: PixivTarget): SManga {
        when (target) {
            is PixivTarget.User -> {
                val response = getUserCached(target.userId)

                response?.name?.let {
                    manga.title = it
                    manga.author = it
                    manga.artist = it
                }
                response?.comment?.let { manga.description = it }
                response?.imageBig?.let { manga.thumbnail_url = it }
            }
            is PixivTarget.Series -> {
                val series = apiUrl("/touch/ajax/illust/series/${target.seriesId}")
                    .executeApi<PixivSeriesDetails>().getOrThrow().series!!

                val illusts = getSeriesIllustsCached(target.seriesId)!!

                series.title?.let { manga.title = it }
                series.caption?.let { manga.description = it }

                illusts.firstOrNull()?.author_details?.user_name?.let {
                    manga.artist = it
                    manga.author = it
                }

                val tags = illusts.flatMap { it.tags ?: emptyList() }.toSet()
                if (tags.isNotEmpty()) manga.genre = tags.joinToString()

                val coverImage = series.coverImage?.let { if (it.isString) it.content else null }
                (coverImage ?: illusts.firstOrNull()?.url)?.let { manga.thumbnail_url = it }
            }
            is PixivTarget.Illustration -> {
                val illust = getIllustCached(target.illustId)!!

                illust.title?.let { manga.title = it }

                illust.author_details?.user_name?.let {
                    manga.artist = it
                    manga.author = it
                }

                illust.comment?.let { manga.description = it }
                illust.tags?.let { manga.genre = it.joinToString() }
                illust.url?.let { manga.thumbnail_url = it }
            }
        }

        return manga
    }

    private suspend fun getChapterList(target: PixivTarget): List<SChapter> {
        val illusts = when (target) {
            is PixivTarget.User -> getUserIdIllusts(target.userId, type = null)
            is PixivTarget.Series -> getSeriesIllustsCached(target.seriesId)!!
            is PixivTarget.Illustration -> listOf(getIllustCached(target.illustId)!!)
        }

        return illusts.mapIndexed { i, illust ->
            SChapter.create().apply {
                setUrlWithoutDomain("/artworks/${illust.id!!}")
                name = illust.title ?: "(null)"
                date_upload = (illust.upload_timestamp ?: 0) * 1000
                chapter_number = (illusts.size - i).toFloat()
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val illustId = chapter.url.substringAfterLast('/')

        return apiUrl("/ajax/illust/$illustId/pages")
            .executeApi<List<PixivIllustPage>>().getOrThrow()
            .mapIndexed { i, page ->
                val imageUrl = getImageUrl(page.urls!!)
                Page(i, chapter.url, imageUrl)
            }
    }

    private fun getImageUrl(urls: PixivIllustPageUrls): String {
        val quality = preferences.getString(PREF_IMAGE_QUALITY, "original")!!

        val sizeOrder = listOf("thumb_mini", "small", "regular", "original")
        val startIndex = sizeOrder.indexOf(quality).takeIf { it >= 0 } ?: sizeOrder.lastIndex

        return sizeOrder.drop(startIndex).firstNotNullOf { size ->
            when (size) {
                "thumb_mini" -> urls.thumb_mini
                "small" -> urls.small
                "regular" -> urls.regular
                "original" -> urls.original
                else -> null
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_IMAGE_QUALITY
            title = "Image quality"
            entries = arrayOf("Thumb Mini", "Small", "Regular", "Original")
            entryValues = arrayOf("thumb_mini", "small", "regular", "original")
            setDefaultValue("original")
            summary = "%s"
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_IMAGE_QUALITY = "pref_image_quality"

        // constants for fetchWithAdaptiveWindow
        private const val TARGET_RESULTS = 50
        private const val RESULTS_PER_PAGE = 36
        private const val MAX_WINDOW_SIZE = 1000 // roughly 25 pages
    }
}
