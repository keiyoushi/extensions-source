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
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class Pixiv(override val lang: String) : ConfigurableSource, HttpSource() {
    override val name = "Pixiv"
    override val baseUrl = "https://www.pixiv.net"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder().add("Referer", "$baseUrl/")

    private open inner class HttpCall(href: String?) {
        val url: HttpUrl.Builder = baseUrl.toHttpUrl()
            .run { href?.let { newBuilder(it)!! } ?: newBuilder() }

        val request: Request.Builder = Request.Builder()
            .headers(headersBuilder().build())

        fun execute(): Response =
            client.newCall(request.url(url.build()).build()).execute()
    }

    class PixivApiException(message: String? = null) : Exception(message, null)

    private inner class ApiCall(href: String?) : HttpCall(href) {
        init {
            url.addEncodedQueryParameter("lang", lang)
            request.addHeader("Accept", "application/json")
        }

        /**
         * Sends the previously constructed API call to the Pixiv API.
         * If the server reports an error, A [PixivApiException] will be
         * returned as a [Result.failure].
         */
        inline fun <reified T> executeApi(): Result<T> {
            val resp = json.decodeFromString<PixivApiResponse>(execute().body.string())
            if (resp.error) {
                return Result.failure(PixivApiException(resp.message))
            }
            return Result.success(json.decodeFromJsonElement<T>(resp.body!!))
        }
    }

    private var popularMangaNextPage = 1
    private lateinit var popularMangaIterator: Iterator<SManga>

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        if (page == 1) {
            popularMangaIterator = sequence {
                val rankingCall = ApiCall("/touch/ajax/ranking/illust?mode=daily&type=manga")

                for (p in countUp(start = 1)) {
                    rankingCall.url.setEncodedQueryParameter("page", p.toString())

                    val entries = rankingCall.executeApi<PixivRankings>().getOrThrow().ranking!!
                    if (entries.isEmpty()) break

                    val detailsCall = ApiCall("/touch/ajax/illust/details/many")
                    entries.forEach { detailsCall.url.addEncodedQueryParameter("illust_ids[]", it.illustId!!) }

                    detailsCall.executeApi<PixivIllustsDetails>().getOrThrow().illust_details!!.forEach { yield(it) }
                }
            }
                .toSManga()
                .iterator()

            popularMangaNextPage = 2
        } else {
            require(page == popularMangaNextPage++)
        }

        val mangas = popularMangaIterator.truncateToList(50)
        return Observable.just(MangasPage(mangas, hasNextPage = mangas.isNotEmpty()))
    }

    private var searchNextPage = 1
    private var searchHash: Int? = null
    private lateinit var searchIterator: Iterator<PixivIllust>
    private lateinit var searchPredicates: List<(PixivIllust) -> Boolean>

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        val target = PixivTarget.fromUri(query) ?: PixivTarget.fromSearchQuery(query)

        val singleResult = { manga: SManga? ->
            Observable.just(
                MangasPage(
                    if (manga != null) {
                        listOf(manga)
                    } else {
                        emptyList()
                    },
                    hasNextPage = false,
                ),
            )
        }

        // Deeplink selection of specific IDs: simply fetch the single object and return
        when (target) {
            is PixivTarget.Illustration -> {
                singleResult(getIllustCached(target.illustId)?.toSManga())
            }
            is PixivTarget.Series -> {
                // TODO: caching!
                val series = ApiCall("/touch/ajax/illust/series/${target.seriesId}")
                    .executeApi<PixivSeriesDetails>().getOrNull()?.series
                singleResult(series?.toSManga())
            }
            else -> {
                null
            }
        }?.let { return it }

        val filters = filters.list as PixivFilters
        val hash = Pair(query, filters.toList()).hashCode()

        if (hash != searchHash || page == 1) {
            searchHash = hash

            lateinit var searchSequence: Sequence<PixivIllust>
            // clear predicates
            searchPredicates = emptyList()

            // TODO: it would be useful to allow multiple user: tags in the query
            // NOTE: probably wouldn't be terribly hard, make makeUserIdIllustSearchSequence accept a
            // list of ids, make PixivTarget.fromSearchQuery handle returning a list of targets or
            // make User target a list (it's just that then you think about supporting mixed lists of
            // multiples of all types, and then have to deal with how to return a mixed list of user
            // results and singletons...)
            if (target is PixivTarget.User) {
                searchSequence = makeUserIdIllustSearchSequence(id = target.userId, type = filters.type)

                searchPredicates = buildList {
                    filters.makeTagsPredicate()?.let(::add)
                    filters.makeRatingPredicate()?.let(::add)
                }
            } else if (query.isNotBlank()) {
                searchSequence = makeIllustSearchSequence(
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
            } else if (filters.users.isNotBlank()) {
                searchSequence = makeUserIllustSearchSequence(nick = filters.users, type = filters.type)
                searchPredicates = buildList {
                    filters.makeTagsPredicate()?.let(::add)
                    filters.makeRatingPredicate()?.let(::add)
                }
            } else {
                searchSequence = makeIllustSearchSequence(
                    word = filters.tags.ifBlank { "漫画" },
                    order = filters.order,
                    mode = filters.rating,
                    sMode = filters.searchMode,
                    type = filters.type,
                    dateBefore = filters.dateBefore.ifBlank { null },
                    dateAfter = filters.dateAfter.ifBlank { null },
                )
            }

            searchIterator = searchSequence.iterator()
            searchNextPage = 2
        } else {
            require(page == searchNextPage++)
        }

        val filteredIllusts = if (searchPredicates.isEmpty()) {
            searchIterator.truncateToList(TARGET_RESULTS)
        } else {
            // if we have a filter let's be a little smarter about how to get enough results
            fetchWithAdaptiveWindow(searchIterator, searchPredicates)
        }

        val mangas = filteredIllusts.toSManga()
        return Observable.just(MangasPage(mangas, hasNextPage = mangas.isNotEmpty()))
    }

    // fetch with variable window size - if filter is strong and we're not getting a lot of
    // results, cast a bigger net.
    //
    // this filters post-truncate to avoid the case where a strong filter will cause the search
    // to spin forever and futilely fetch page after page trying to get enough results to return
    private fun fetchWithAdaptiveWindow(
        iterator: Iterator<PixivIllust>,
        predicates: List<(PixivIllust) -> Boolean>,
    ): List<PixivIllust> {
        val sampleIllusts = iterator.truncateToList(RESULTS_PER_PAGE)
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
            iterator.truncateToList(remainingNeeded)
        } else {
            emptyList()
        }

        val allIllusts = sampleIllusts + additionalIllusts
        return allIllusts.filter { illust -> predicates.all { p -> p(illust) } }
    }

    private fun makeIllustSearchSequence(
        word: String,
        sMode: String,
        order: String?,
        mode: String?,
        type: String?,
        dateBefore: String?,
        dateAfter: String?,
    ) = sequence<PixivIllust> {
        val call = ApiCall("/touch/ajax/search/illusts")

        call.url.addQueryParameter("word", word)
        call.url.addEncodedQueryParameter("s_mode", sMode)
        type?.let { call.url.addEncodedQueryParameter("type", it) }
        order?.let { call.url.addEncodedQueryParameter("order", it) }
        mode?.let { call.url.addEncodedQueryParameter("mode", it) }
        dateBefore?.let { call.url.addEncodedQueryParameter("ecd", it) }
        dateAfter?.let { call.url.addEncodedQueryParameter("scd", it) }

        for (p in countUp(start = 1)) {
            call.url.setEncodedQueryParameter("p", p.toString())

            val illusts = call.executeApi<PixivResults>().getOrThrow().illusts!!
            if (illusts.isEmpty()) break

            for (illust in illusts) {
                if (illust.is_ad_container == 1) continue
                if (illust.type == "2") continue

                yield(illust)
            }
        }
    }

    // search by username
    private fun makeUserIllustSearchSequence(nick: String, type: String?) = sequence<PixivIllust> {
        val searchUsers = HttpCall("/search/users")
            .apply {
                url.addQueryParameter("s_mode", "s_usr")
                url.addQueryParameter("nick", nick)
                url.addQueryParameter("i", "1")
                url.addQueryParameter("comment", "")
                // have to use desktop User-Agent to get __NEXT_DATA__ (mobile version is SPA without embedded data)
                request.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            }

        for (p in countUp(start = 1)) {
            searchUsers.url.setEncodedQueryParameter("p", p.toString())

            val response = searchUsers.execute()
            val htmlBody = response.body.string()

            val doc = org.jsoup.Jsoup.parse(htmlBody)
            val nextDataScript = doc.select("script#__NEXT_DATA__").first()?.data() ?: break

            val nextData = json.decodeFromString<PixivNextData>(nextDataScript)
            val pageProps = nextData.props.pageProps
            val userIds = pageProps.userIds

            if (userIds.isEmpty()) break

            // users is Map<String (userId as string), PixivUserInfo>, userIds is List<Long>
            val users = pageProps.userData?.users
            val exactMatchUserId = users?.let { userData ->
                userIds.find { userId ->
                    userData[userId.toString()]?.name?.equals(nick, ignoreCase = true) == true
                }
            }

            if (exactMatchUserId != null) {
                // found exact match, fetch and return works from this exact user
                yieldAll(makeUserIdIllustSearchSequence(exactMatchUserId.toString(), type))
                break
            } else {
                // return works from all users
                for (userId in userIds) {
                    yieldAll(makeUserIdIllustSearchSequence(userId.toString(), type))
                }
            }
        }
    }

    // lookup directly by user id
    private fun makeUserIdIllustSearchSequence(id: String, type: String?) = sequence<PixivIllust> {
        val fetchUserIllusts = ApiCall("/touch/ajax/user/illusts")
            .apply {
                type?.let { url.setEncodedQueryParameter("type", it) }
                url.setEncodedQueryParameter("id", id)
            }

        for (p in countUp(start = 1)) {
            fetchUserIllusts.url.setEncodedQueryParameter("p", p.toString())

            val illusts = fetchUserIllusts.executeApi<PixivResults>().getOrThrow().illusts!!
            if (illusts.isEmpty()) break

            yieldAll(illusts)
        }
    }

    override fun getFilterList() = FilterList(PixivFilters())

    private fun List<PixivIllust>.toSManga() = asSequence().toSManga().toList()
    private fun Sequence<PixivIllust>.toSManga() = sequence {
        val seriesIdsSeen = mutableSetOf<String>()

        forEach { illust ->
            val manga = illust.toSManga()
            if (seriesIdsSeen.add(manga.url)) {
                yield(manga)
            }
        }
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
    private lateinit var latestMangaIterator: Iterator<SManga>

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        if (page == 1) {
            latestMangaIterator = sequence {
                val call = ApiCall("/touch/ajax/latest?type=manga")

                for (p in countUp(start = 1)) {
                    call.url.setEncodedQueryParameter("p", p.toString())

                    val illusts = call.executeApi<PixivResults>().getOrThrow().illusts!!
                    if (illusts.isEmpty()) break

                    for (illust in illusts) {
                        if (illust.is_ad_container == 1) continue
                        yield(illust)
                    }
                }
            }
                .toSManga()
                .iterator()

            latestMangaNextPage = 2
        } else {
            require(page == latestMangaNextPage++)
        }

        val mangas = latestMangaIterator.truncateToList(50).toList()
        return Observable.just(MangasPage(mangas, hasNextPage = mangas.isNotEmpty()))
    }

    private val getIllustCached by lazy {
        lruCached<String, PixivIllust?>(25) { illustId ->
            val call = ApiCall("/touch/ajax/illust/details?illust_id=$illustId")
            return@lruCached call.executeApi<PixivIllustDetails>().getOrNull()?.illust_details
        }
    }

    private val getSeriesIllustsCached by lazy {
        lruCached<String, List<PixivIllust>?>(25) { seriesId ->
            val call = ApiCall("/touch/ajax/illust/series_content/$seriesId")
            var lastOrder = 0

            return@lruCached buildList {
                while (true) {
                    call.url.setEncodedQueryParameter("last_order", lastOrder.toString())

                    val illusts = call.executeApi<PixivSeriesContents>()
                        .getOrElse { return@lruCached null }.series_contents!!
                    if (illusts.isEmpty()) break

                    addAll(illusts)
                    lastOrder += illusts.size
                }
            }
        }
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val (id, isSeries) = parseSMangaUrl(manga.url)

        if (isSeries) {
            val series = ApiCall("/touch/ajax/illust/series/$id")
                .executeApi<PixivSeriesDetails>().getOrThrow().series!!

            val illusts = getSeriesIllustsCached(id)!!

            if (series.id != null && series.userId != null) {
                manga.setUrlWithoutDomain("/user/${series.userId}/series/${series.id}")
            }

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
        } else {
            val illust = getIllustCached(id)!!

            illust.id?.let { manga.setUrlWithoutDomain("/artworks/$it") }
            illust.title?.let { manga.title = it }

            illust.author_details?.user_name?.let {
                manga.artist = it
                manga.author = it
            }

            illust.comment?.let { manga.description = it }
            illust.tags?.let { manga.genre = it.joinToString() }
            illust.url?.let { manga.thumbnail_url = it }
        }

        return Observable.just(manga)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val (id, isSeries) = parseSMangaUrl(manga.url)

        val illusts = when (isSeries) {
            true -> getSeriesIllustsCached(id)!!
            false -> listOf(getIllustCached(id)!!)
        }

        val chapters = illusts.mapIndexed { i, illust ->
            SChapter.create().apply {
                setUrlWithoutDomain("/artworks/${illust.id!!}")
                name = illust.title ?: "(null)"
                date_upload = (illust.upload_timestamp ?: 0) * 1000
                chapter_number = (illusts.size - i).toFloat()
            }
        }

        return Observable.just(chapters)
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val illustId = chapter.url.substringAfterLast('/')

        val pages = ApiCall("/ajax/illust/$illustId/pages")
            .executeApi<List<PixivIllustPage>>().getOrThrow()
            .mapIndexed { i, page ->
                val imageUrl = getImageUrl(page.urls!!)
                Page(i, chapter.url, imageUrl)
            }

        return Observable.just(pages)
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

    override fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException()

    override fun mangaDetailsParse(response: Response): SManga =
        throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> =
        throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    override fun popularMangaRequest(page: Int): Request =
        throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage =
        throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        throw UnsupportedOperationException()
}
