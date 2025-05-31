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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.io.IOException

class Mangamo : ConfigurableSource, HttpSource() {

    override val name = "Mangamo"

    override val lang = "en"

    override val baseUrl = "https://www.mangamo.com"

    override val supportsLatest = true

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

    private val user by cachedBy({ Pair(userToken, firestore) }) {
        val response = client.newCall(
            firestore.getDocument("Users/$userToken") {
                fields = listOf(UserDto::isSubscribed.name)
            },
        ).execute()
        response.body.string().parseJson<DocumentDto<UserDto>>().fields
    }

    private val coinMangaPref
        get() = preferences.getStringSet(MangamoConstants.HIDE_COIN_MANGA_PREF, setOf())!!
    private val exclusivesOnlyPref
        get() = preferences.getStringSet(MangamoConstants.EXCLUSIVES_ONLY_PREF, setOf())!!

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor {
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
        .build()

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

    override fun popularMangaRequest(page: Int): Request = firestore.getCollection("Series") {
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

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response) {
        if (coinMangaPref.contains(MangamoConstants.HIDE_COIN_MANGA_OPTION_IN_BROWSE)) {
            if (it.onlyTransactional == true) {
                return@parseMangaPage false
            }
        }
        true
    }

    // Latest manga

    override fun latestUpdatesRequest(page: Int): Request = firestore.getCollection("Series") {
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

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response) {
        if (it.enabled != true) {
            return@parseMangaPage false
        }
        if (coinMangaPref.contains(MangamoConstants.HIDE_COIN_MANGA_OPTION_IN_BROWSE)) {
            if (it.onlyTransactional == true) {
                return@parseMangaPage false
            }
        }
        if (exclusivesOnlyPref.contains(MangamoConstants.EXCLUSIVES_ONLY_OPTION_IN_BROWSE)) {
            if (it.onlyOnMangamo != true) {
                return@parseMangaPage false
            }
        }
        true
    }

    // Search manga

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = firestore.getCollection("Series") {
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

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response) {
        if (coinMangaPref.contains(MangamoConstants.HIDE_COIN_MANGA_OPTION_IN_SEARCH)) {
            if (it.onlyTransactional == true) {
                return@parseMangaPage false
            }
        }
        if (exclusivesOnlyPref.contains(MangamoConstants.EXCLUSIVES_ONLY_OPTION_IN_SEARCH)) {
            if (it.onlyOnMangamo != true) {
                return@parseMangaPage false
            }
        }
        true
    }

    // Manga details

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val uri = getMangaUrl(manga).toHttpUrl()

        val seriesId = uri.queryParameter(MangamoConstants.SERIES_QUERY_PARAM)!!.toInt()

        return firestore.getDocument("Series/$seriesId") {
            fields = seriesRequiredFields
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.body.string().parseJson<DocumentDto<SeriesDto>>().fields
        return processSeries(dto)
    }

    // Chapter list section

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val uri = getMangaUrl(manga).toHttpUrl()

        val seriesId = uri.queryParameter(MangamoConstants.SERIES_QUERY_PARAM)!!.toInt()

        val seriesObservable = client.newCall(
            firestore.getDocument("Series/$seriesId") {
                fields = listOf(
                    SeriesDto::maxFreeChapterNumber.name,
                    SeriesDto::maxMeteredReadingChapterNumber.name,
                    SeriesDto::onlyTransactional.name,
                )
            },
        ).asObservableSuccess().map { response ->
            response.body.string().parseJson<DocumentDto<SeriesDto>>().fields
        }

        val chaptersObservable = client.newCall(
            firestore.getCollection("Series/$seriesId/chapters") {
                fields = listOf(
                    ChapterDto::enabled.name,
                    ChapterDto::id.name,
                    ChapterDto::seriesId.name,
                    ChapterDto::chapterNumber.name,
                    ChapterDto::name.name,
                    ChapterDto::createdAt.name,
                    ChapterDto::onlyTransactional.name,
                )

                orderBy = listOf(descending(ChapterDto::chapterNumber.name))
            },
        ).asObservableSuccess().map { response ->
            response.body.string().parseJson<QueryResultDto<ChapterDto>>().elements
        }

        val hideCoinChapters = coinMangaPref.contains(MangamoConstants.HIDE_COIN_MANGA_OPTION_CHAPTERS)

        return Observable.combineLatest(seriesObservable, chaptersObservable) { series, chapters ->
            chapters
                .mapNotNull { chapter ->
                    if (chapter.enabled != true) {
                        return@mapNotNull null
                    }

                    val isUserSubscribed = user.isSubscribed == true

                    val isFreeChapter = chapter.chapterNumber!! <= (series.maxFreeChapterNumber ?: 0)
                    val isMeteredChapter = chapter.chapterNumber <= (series.maxMeteredReadingChapterNumber ?: 0)
                    val isCoinChapter = chapter.onlyTransactional == true ||
                        (series.onlyTransactional == true && !isFreeChapter)

                    if (hideCoinChapters && isCoinChapter) {
                        return@mapNotNull null
                    }

                    SChapter.create().apply {
                        chapter_number = chapter.chapterNumber
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
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException()

    private fun getPagesImagesRequest(series: Int, chapter: Int): Request {
        return POST(
            "${MangamoConstants.FIREBASE_FUNCTION_BASE_PATH}/page/$series/$chapter",
            helper.jsonHeaders,
            "{\"idToken\":\"${auth.getIdToken()}\"}".toRequestBody(),
        )
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val uri = (baseUrl + chapter.url).toHttpUrl()

        val seriesId = uri.queryParameter(MangamoConstants.SERIES_QUERY_PARAM)!!.toInt()
        val chapterId = uri.queryParameter(MangamoConstants.CHAPTER_QUERY_PARAM)!!.toInt()

        return getPagesImagesRequest(seriesId, chapterId)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.body.string().parseJson<List<PageDto>>()

        return data.map {
            Page(it.pageNumber - 1, imageUrl = it.uri)
        }.sortedBy { it.index }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

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
