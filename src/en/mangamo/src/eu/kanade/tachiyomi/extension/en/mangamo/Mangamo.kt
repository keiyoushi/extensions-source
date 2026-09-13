package eu.kanade.tachiyomi.extension.en.mangamo

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.extension.en.mangamo.MangamoHelper.Companion.parseJson
import eu.kanade.tachiyomi.extension.en.mangamo.dto.ChapterDto
import eu.kanade.tachiyomi.extension.en.mangamo.dto.DocumentDto
import eu.kanade.tachiyomi.extension.en.mangamo.dto.PageDto
import eu.kanade.tachiyomi.extension.en.mangamo.dto.QueryResultDto
import eu.kanade.tachiyomi.extension.en.mangamo.dto.SeriesDto
import eu.kanade.tachiyomi.extension.en.mangamo.dto.UserDto
import eu.kanade.tachiyomi.extension.en.mangamo.dto.documents
import eu.kanade.tachiyomi.extension.en.mangamo.dto.elements
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

@Source
abstract class Mangamo :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val helper = MangamoHelper(headers)

    private var userToken = ""
        get() {
            if (field == "") {
                field = preferences.getString(MangamoConstants.USER_TOKEN_PREF, "")!!

                if (field == "") {
                    field = MangamoAuth.createAnonymousUserToken(client)
                    preferences.edit()
                        .putString(MangamoConstants.USER_TOKEN_PREF, field)
                        .apply()
                }
            }
            return field
        }

    private val auth by cachedBy({ userToken }) {
        MangamoAuth(helper, client, userToken)
    }

    private val firestore by cachedBy({ auth }) {
        FirestoreRequestFactory(helper, auth)
    }

    private suspend fun getUser(): UserDto {
        val request = firestore.getDocument("Users/$userToken") {
            fields = listOf(UserDto::isSubscribed.name)
        }
        return client.get(request.url, request.headers).use { response ->
            response.body.string().parseJson<DocumentDto<UserDto>>().fields
        }
    }

    private val coinMangaPref
        get() = preferences.getStringSet(MangamoConstants.HIDE_COIN_MANGA_PREF, setOf())!!
    private val exclusivesOnlyPref
        get() = preferences.getStringSet(MangamoConstants.EXCLUSIVES_ONLY_PREF, setOf())!!

    override fun OkHttpClient.Builder.configureClient() = addNetworkInterceptor {
        val request = it.request()
        val response = it.proceed(request)

        if (request.url.toString().startsWith("${MangamoConstants.FIREBASE_FUNCTION_BASE_PATH}/page")) {
            if (response.code == 401) {
                throw IOException("You don't have access to this chapter")
            }
        }
        response
    }
        .addNetworkInterceptor {
            val response = it.proceed(it.request())

            // Add Cache-Control to Firestore queries
            if (it.request().url.toString().startsWith(MangamoConstants.FIRESTORE_API_BASE_PATH)) {
                return@addNetworkInterceptor response.newBuilder()
                    .header("Cache-Control", "public, max-age=${MangamoConstants.FIRESTORE_CACHE_LENGTH}")
                    .build()
            }
            response
        }

    private val seriesRequiredFields = listOf(
        SeriesDto::id.name,
        SeriesDto::name.name,
        SeriesDto::name_lowercase.name,
        SeriesDto::description.name,
        SeriesDto::authors.name,
        SeriesDto::genres.name,
        SeriesDto::ongoing.name,
        SeriesDto::releaseStatusTag.name,
        SeriesDto::titleArt.name,
    )

    private fun processSeries(dto: SeriesDto) = SManga.create().apply {
        author = dto.authors?.joinToString { it.name }
        description = dto.description
        genre = dto.genres?.joinToString { it.name }
        status = helper.getSeriesStatus(dto)
        thumbnail_url = dto.titleArt
        title = dto.name!!
        url = helper.getSeriesUrl(dto)
        initialized = true
    }

    private fun parseMangaPage(response: Response, filterPredicate: (SeriesDto) -> Boolean = { true }): MangasPage {
        val collection = response.body.string().parseJson<QueryResultDto<SeriesDto>>()

        val isDone = collection.documents.size < MangamoConstants.BROWSE_PAGE_SIZE

        val results = collection.elements.filter(filterPredicate)

        return MangasPage(results.map { processSeries(it) }, !isDone)
    }

    // Popular manga

    override suspend fun getPopularManga(page: Int): MangasPage {
        val request = firestore.getCollection("Series") {
            limit = MangamoConstants.BROWSE_PAGE_SIZE
            offset = (page - 1) * MangamoConstants.BROWSE_PAGE_SIZE

            val fields = seriesRequiredFields.toMutableList()
            this.fields = fields

            if (coinMangaPref.contains(MangamoConstants.HIDE_COIN_MANGA_OPTION_IN_BROWSE)) {
                fields += SeriesDto::onlyTransactional.name
            }

            val prefFilters =
                if (exclusivesOnlyPref.contains(MangamoConstants.EXCLUSIVES_ONLY_OPTION_IN_BROWSE)) {
                    isEqual(SeriesDto::onlyOnMangamo.name, true)
                } else {
                    null
                }

            filter = and(
                *listOfNotNull(
                    isEqual(SeriesDto::enabled.name, true),
                    prefFilters,
                ).toTypedArray(),
            )
        }

        return client.post(request.url, request.headers, request.body!!).use { response ->
            parseMangaPage(response) {
                !(coinMangaPref.contains(MangamoConstants.HIDE_COIN_MANGA_OPTION_IN_BROWSE) && it.onlyTransactional == true)
            }
        }
    }

    // Latest manga

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val request = firestore.getCollection("Series") {
            limit = MangamoConstants.BROWSE_PAGE_SIZE
            offset = (page - 1) * MangamoConstants.BROWSE_PAGE_SIZE

            val fields = seriesRequiredFields.toMutableList()
            this.fields = fields

            fields += SeriesDto::enabled.name

            if (coinMangaPref.contains(MangamoConstants.HIDE_COIN_MANGA_OPTION_IN_BROWSE)) {
                fields += SeriesDto::onlyTransactional.name
            }

            if (exclusivesOnlyPref.contains(MangamoConstants.EXCLUSIVES_ONLY_OPTION_IN_BROWSE)) {
                fields += SeriesDto::onlyOnMangamo.name
            }

            orderBy = listOf(descending(SeriesDto::updatedAt.name))

            // Filters can't be used with orderBy because firebase wants there to be indexes
            // on various fields to support those queries and we can't create them.
            // Therefore, all filtering has to be done on the client in the parse method.
        }

        return client.post(request.url, request.headers, request.body!!).use { response ->
            parseMangaPage(response) {
                it.enabled == true &&
                    !(coinMangaPref.contains(MangamoConstants.HIDE_COIN_MANGA_OPTION_IN_BROWSE) && it.onlyTransactional == true) &&
                    !(exclusivesOnlyPref.contains(MangamoConstants.EXCLUSIVES_ONLY_OPTION_IN_BROWSE) && it.onlyOnMangamo != true)
            }
        }
    }

    // Search manga

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val request = firestore.getCollection("Series") {
            limit = MangamoConstants.BROWSE_PAGE_SIZE
            offset = (page - 1) * MangamoConstants.BROWSE_PAGE_SIZE

            val fields = seriesRequiredFields.toMutableList()
            this.fields = fields

            if (coinMangaPref.contains(MangamoConstants.HIDE_COIN_MANGA_OPTION_IN_BROWSE)) {
                fields += SeriesDto::onlyTransactional.name
            }

            if (exclusivesOnlyPref.contains(MangamoConstants.EXCLUSIVES_ONLY_OPTION_IN_SEARCH)) {
                fields += SeriesDto::onlyOnMangamo.name
            }

            // Adding additional filters makes Firestore complain about wanting an index
            // so we filter on the client in parse, just like for Latest.

            filter = and(
                isEqual(SeriesDto::enabled.name, true),
                isGreaterThanOrEqual(SeriesDto::name_lowercase.name, query.lowercase()),
                isLessThanOrEqual(SeriesDto::name_lowercase.name, query.lowercase() + "\uf8ff"),
            )
        }

        return client.post(request.url, request.headers, request.body!!).use { response ->
            parseMangaPage(response) {
                !(coinMangaPref.contains(MangamoConstants.HIDE_COIN_MANGA_OPTION_IN_SEARCH) && it.onlyTransactional == true) &&
                    !(exclusivesOnlyPref.contains(MangamoConstants.EXCLUSIVES_ONLY_OPTION_IN_SEARCH) && it.onlyOnMangamo != true)
            }
        }
    }

    // Manga details

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val uri = getMangaUrl(manga).toHttpUrl()
        val seriesId = uri.queryParameter(MangamoConstants.SERIES_QUERY_PARAM)!!.toInt()

        val seriesDeferred = async {
            if (fetchDetails || fetchChapters) {
                val request = firestore.getDocument("Series/$seriesId") {
                    fields = buildList {
                        if (fetchDetails) {
                            addAll(seriesRequiredFields)
                        }
                        if (fetchChapters) {
                            add(SeriesDto::maxFreeChapterNumber.name)
                            add(SeriesDto::maxMeteredReadingChapterNumber.name)
                            add(SeriesDto::onlyTransactional.name)
                        }
                    }
                }
                client.get(request.url, request.headers).use { response ->
                    response.body.string().parseJson<DocumentDto<SeriesDto>>().fields
                }
            } else {
                null
            }
        }
        val chaptersDeferred = async {
            if (fetchChapters) {
                val request = firestore.getCollection("Series/$seriesId/chapters") {
                    fields = listOf(
                        ChapterDto::alwaysFree.name,
                        ChapterDto::enabled.name,
                        ChapterDto::id.name,
                        ChapterDto::seriesId.name,
                        ChapterDto::chapterNumber.name,
                        ChapterDto::name.name,
                        ChapterDto::createdAt.name,
                        ChapterDto::onlyTransactional.name,
                        ChapterDto::type.name,
                    )

                    orderBy = listOf(descending(ChapterDto::chapterNumber.name))
                }
                client.post(request.url, request.headers, request.body!!).use { response ->
                    response.body.string().parseJson<QueryResultDto<ChapterDto>>().elements
                }
            } else {
                null
            }
        }

        val series = seriesDeferred.await()
        val chapterList = chaptersDeferred.await()
        val hideCoinChapters = coinMangaPref.contains(MangamoConstants.HIDE_COIN_MANGA_OPTION_CHAPTERS)

        if (series == null) return@coroutineScope SMangaUpdate(manga, chapters)

        val updatedManga = if (fetchDetails) processSeries(series) else manga
        val updatedChapters = if (chapterList != null) {
            val isUserSubscribed = getUser().isSubscribed == true

            chapterList.mapNotNull { chapter ->
                if (chapter.enabled != true) {
                    return@mapNotNull null
                }

                val chapterNumber = chapter.chapterNumber!!
                val isFreeChapter = chapter.alwaysFree == true || chapterNumber <= (series.maxFreeChapterNumber ?: 0)
                val isMeteredChapter = chapterNumber <= (series.maxMeteredReadingChapterNumber?.toFloatOrNull() ?: 0f)
                val isCoinChapter = chapter.onlyTransactional == true || (series.onlyTransactional == true && chapter.isVolume && !isFreeChapter)

                if (hideCoinChapters && isCoinChapter) {
                    return@mapNotNull null
                }

                SChapter.create().apply {
                    chapter_number = chapterNumber
                    date_upload = chapter.createdAt!!
                    name = chapter.name +
                        if (isCoinChapter) {
                            " \uD83E\uDE99" // coin emoji
                        } else if (isFreeChapter || isUserSubscribed) {
                            ""
                        } else if (isMeteredChapter) {
                            " \uD83D\uDD52" // three-o-clock emoji
                        } else {
                            // subscriber chapter
                            " \uD83D\uDD12" // lock emoji
                        }
                    url = helper.getChapterUrl(chapter)
                }
            }
        } else {
            chapters
        }

        SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val uri = (baseUrl + chapter.url).toHttpUrl()

        val seriesId = uri.queryParameter(MangamoConstants.SERIES_QUERY_PARAM)!!.toInt()
        val chapterId = uri.queryParameter(MangamoConstants.CHAPTER_QUERY_PARAM)!!.toInt()
        val response = client.post(
            "${MangamoConstants.FIREBASE_FUNCTION_BASE_PATH}/page/$seriesId/$chapterId",
            helper.jsonHeaders,
            "{\"idToken\":\"${auth.getIdToken()}\"}".toRequestBody(),
        )
        val data = response.use { it.body.string().parseJson<List<PageDto>>() }

        return data.map {
            Page(it.pageNumber - 1, imageUrl = it.uri)
        }.sortedBy { it.index }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val userTokenPref = EditTextPreference(screen.context).apply {
            key = MangamoConstants.USER_TOKEN_PREF
            summary = "If you are a paying user, enter your user token to authenticate."
            title = "User Token"

            dialogMessage = """
            Copy your token from the Mangamo app by going to My Manga > Profile icon (top right) > About and tapping on the "User" string at the bottom.

            Then replace the auto-generated token you see below with your personal token.
            """.trimIndent()

            setDefaultValue("")

            setOnPreferenceChangeListener { _, newValue ->
                userToken = newValue as String
                true
            }
        }

        val hideCoinMangaPref = MultiSelectListPreference(screen.context).apply {
            key = MangamoConstants.HIDE_COIN_MANGA_PREF
            title = "Hide Coin Manga"

            summary = """
            Hide manga that require coins.

            For technical reasons, manga where a subscription only gives access to some chapters are not considered coin manga, even if coins are required to access all chapters.
            """.trimIndent()

            entries = arrayOf(
                "Hide in Popular/Latest",
                "Hide in Search",
                "Hide Coin Chapters",
            )

            entryValues = arrayOf(
                MangamoConstants.HIDE_COIN_MANGA_OPTION_IN_BROWSE,
                MangamoConstants.HIDE_COIN_MANGA_OPTION_IN_SEARCH,
                MangamoConstants.HIDE_COIN_MANGA_OPTION_CHAPTERS,
            )

            setDefaultValue(setOf<String>())
        }

        val exclusivesOnly = MultiSelectListPreference(screen.context).apply {
            key = MangamoConstants.EXCLUSIVES_ONLY_PREF
            title = "Only Show Exclusives"
            summary = "Only show Mangamo-exclusive manga."

            entries = arrayOf(
                "In Popular/Latest",
                "In Search",
            )

            entryValues = arrayOf(
                MangamoConstants.EXCLUSIVES_ONLY_OPTION_IN_BROWSE,
                MangamoConstants.EXCLUSIVES_ONLY_OPTION_IN_SEARCH,
            )

            setDefaultValue(setOf<String>())
        }

        screen.addPreference(userTokenPref)
        screen.addPreference(hideCoinMangaPref)
        screen.addPreference(exclusivesOnly)
    }
}
