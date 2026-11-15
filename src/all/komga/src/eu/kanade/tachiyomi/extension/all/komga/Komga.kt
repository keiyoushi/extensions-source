package eu.kanade.tachiyomi.extension.all.komga

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Toast
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.apache.commons.text.StringSubstitutor
import java.util.Locale

@Source
class Komga(
    override val lang: String,
    override val id: Long,
) : KeiSource(),
    ConfigurableSource,
    UnmeteredSource {

    internal val preferences: SharedPreferences by getPreferencesLazy()

    private val displayName by lazy { preferences.getString(PREF_DISPLAY_NAME, "")!! }

    override val name by lazy {
        val displayNameSuffix = displayName
            .ifBlank { instanceSuffix }
            .let { if (it.isNotBlank()) " ($it)" else "" }

        "Komga$displayNameSuffix"
    }

    // Distinguish the fixed factory instances by position (Komga, Komga (2), Komga (3), ...)
    // when the user hasn't set a display name. Inferred from the source id.
    private val instanceSuffix: String
        get() = INSTANCE_IDS.indexOf(id).let { if (it > 0) "${it + 1}" else "" }

    override val supportsFilterFetching = true

    override val baseUrl
        get() = preferences.getString(PREF_ADDRESS, "")!!.removeSuffix("/")

    private val username
        get() = preferences.getString(PREF_USERNAME, "")!!

    private val password
        get() = preferences.getString(PREF_PASSWORD, "")!!

    private val apiKey
        get() = preferences.getString(PREF_API_KEY, "")!!

    private val defaultLibraries
        get() = preferences.getStringSet(PREF_DEFAULT_LIBRARIES, emptySet())!!

    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", "TachiyomiKomga/${AppInfo.getVersionName()}")
        if (apiKey.isNotBlank()) {
            set("X-API-Key", apiKey)
        }
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        // One session cookie per host, shared with the app's Komga tracker, so one user per host
        authenticator { _, response ->
            if (apiKey.isNotBlank() || response.request.header("Authorization") != null) {
                null // Give up if API key is set or we've already failed to authenticate.
            } else {
                response.request.newBuilder()
                    .addHeader("Authorization", Credentials.basic(username, password))
                    .build()
            }
        }
        dns(Dns.SYSTEM) // don't use DNS over HTTPS as it breaks IP addressing
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SeriesSort(Filter.Sort.Selection(1, true))))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SeriesSort(Filter.Sort.Selection(3, false))))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (baseUrl.isBlank()) throw Exception("Set Address in Settings")

        val collection = filters.firstInstanceOrNull<CollectionSelect>()?.let { it.collections[it.state] }?.takeIf { it.id != null }
        val type = when (filters.firstInstanceOrNull<TypeSelect>()?.state?.takeIf { collection == null }) {
            1 -> TYPE_READLISTS
            2 -> TYPE_BOOKS
            else -> TYPE_SERIES
        }
        val libraryFilter = filters.firstInstanceOrNull<LibraryFilter>()

        val url = "$baseUrl/api/v1".toHttpUrl().newBuilder()
            .addPathSegments(
                when (type) {
                    TYPE_READLISTS -> "readlists"
                    TYPE_BOOKS -> "books/list"
                    else -> "series/list"
                },
            )
            .addQueryParameter("page", (page - 1).toString())

        val searchTerm = query.trim().replace(LUCENE_RESERVED, """\\$0""").replace(LUCENE_OPERATOR) { it.value.lowercase() }
        val sort = filters.firstInstanceOrNull<SeriesSort>()?.state
        val sortCriteria = when {
            // Komga shows a collection in its own order whatever the sort
            collection != null -> if (collection.ordered) "collection.number,asc" else "metadata.titleSort,asc"
            sort == null -> null
            else -> {
                val field = when {
                    // Komga only ranks by relevance when there is a search term
                    sort.index == 0 && searchTerm.isNotEmpty() -> "relevance"
                    sort.index <= 1 -> when (type) {
                        TYPE_SERIES -> "metadata.titleSort"
                        TYPE_BOOKS -> "metadata.title"
                        else -> "name"
                    }
                    sort.index == 2 -> "createdDate"
                    sort.index == 3 -> "lastModifiedDate"
                    // Komga only sorts series randomly
                    type == TYPE_SERIES -> "random"
                    else -> null
                }
                field?.let { "$it,${if (sort.ascending) "asc" else "desc"}" }
            }
        }
        sortCriteria?.let { url.addQueryParameter("sort", it) }

        if (type == TYPE_READLISTS) {
            val libraryIds = libraryFilter?.selectedIds ?: defaultLibraries
            url.addQueryParameter("search", searchTerm)
            if (libraryIds.isNotEmpty()) url.addQueryParameter("library_id", libraryIds.joinToString(","))
            return processSeriesPage(client.get(url.build(), Fetch.XHR), baseUrl)
        }

        val conditions = buildList {
            add(ConditionDto(deleted = OperatorDto("isFalse")))
            collection?.id?.let { add(ConditionDto(collectionId = OperatorDto("is", it))) }
            if (libraryFilter == null && defaultLibraries.isNotEmpty()) {
                add(ConditionDto(anyOf = defaultLibraries.map { ConditionDto(libraryId = OperatorDto("is", it)) }))
            }
            filters.filterIsInstance<ConditionFilter>()
                .filter { type == TYPE_SERIES || it.appliesToBooks }
                .groupBy { it.key }
                .map { (_, group) -> group.flatMap { it.alternatives() } }
                .filter { it.isNotEmpty() }
                .forEach { add(ConditionDto(anyOf = it)) }
        }

        val body = SearchDto(ConditionDto(allOf = conditions), searchTerm.ifEmpty { null }).toJsonRequestBody()
        val response = try {
            client.post(url.build(), Fetch.XHR, body)
        } catch (e: HttpException) {
            // Servers before the list API answer 405
            if (e.code == 405) throw Exception("Update Komga to 1.19 or newer") else throw e
        }
        return processSeriesPage(response, baseUrl)
    }

    private fun processSeriesPage(response: Response, baseUrl: String): MangasPage {
        val data = if (response.isFromReadList()) {
            response.parseAs<PageWrapperDto<ReadListDto>>()
        } else if (response.isFromBook()) {
            response.parseAs<PageWrapperDto<BookDto>>()
        } else {
            response.parseAs<PageWrapperDto<SeriesDto>>()
        }

        return MangasPage(data.content.map { it.toSManga(baseUrl) }, !data.last)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val (manga, chapters) = coroutineScope {
            val mangaD = async { if (fetchDetails) getMangaDetails(manga) else manga }
            val chaptersD = async { if (fetchChapters) getChapterList(manga) else chapters }
            mangaD.await() to chaptersD.await()
        }

        return SMangaUpdate(manga, chapters)
    }

    override fun getMangaUrl(manga: SManga) = manga.url.replace("/api/v1", "")

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val response = client.get(manga.url, Fetch.XHR)

        return if (response.isFromReadList()) {
            response.parseAs<ReadListDto>().toSManga(baseUrl)
        } else if (response.isFromBook()) {
            response.parseAs<BookDto>().toSManga(baseUrl)
        } else {
            response.parseAs<SeriesDto>().toSManga(baseUrl)
        }
    }

    private val chapterNameTemplate
        get() = preferences.getString(PREF_CHAPTER_NAME_TEMPLATE, PREF_CHAPTER_NAME_TEMPLATE_DEFAULT)!!

    private val separator
        get() = preferences.getString(PREF_SCANLATOR_SEP, PREF_SCANLATOR_SEP_DEFAULT)!!
            .let { if (it == ",") "$it " else " $it " }

    override fun getChapterUrl(chapter: SChapter) = chapter.url.replace("/api/v1/books", "/book")

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val url = when {
            manga.url.isFromBook() -> "${manga.url}?unpaged=true&media_status=READY&deleted=false"
            else -> "${manga.url}/books?unpaged=true&media_status=READY&deleted=false"
        }
        val response = client.get(url, Fetch.XHR)

        if (response.isFromBook()) {
            val book = response.parseAs<BookDto>()
            return listOf(
                SChapter.create().apply {
                    chapter_number = 1F
                    this.url = "$baseUrl/api/v1/books/${book.id}"
                    name = book.getChapterName(chapterNameTemplate, isFromReadList = true, manga.title)
                    scanlator = book.metadata.authors
                        .filter { it.role == "translator" }
                        .joinToString(separator) { it.name }
                    date_upload = parseDate(
                        book.metadata.releaseDate
                            ?: book.created
                            ?: book.fileLastModified,
                    )
                },
            )
        }
        val page = response.parseAs<PageWrapperDto<BookDto>>().content
        val isFromReadList = response.isFromReadList()
        val chapterNameTemplate = chapterNameTemplate

        return page
            .filter {
                it.media.mediaProfile != "EPUB" || it.media.epubDivinaCompatible
            }
            .mapIndexed { index, book ->
                SChapter.create().apply {
                    chapter_number = if (!isFromReadList) book.metadata.numberSort else index + 1F
                    this.url = "$baseUrl/api/v1/books/${book.id}"
                    name = book.getChapterName(chapterNameTemplate, isFromReadList, manga.title)
                    scanlator = book.metadata.authors
                        .filter { it.role == "translator" }
                        .joinToString(separator) { it.name }
                    // XXX: `Book.fileLastModified` actually uses the server's running timezone,
                    // not UTC, even if the timestamp ends with a Z! We cannot determine the
                    // server's timezone, which is why this is a last resort option.
                    date_upload = parseDate(
                        book.metadata.releaseDate
                            ?: book.created
                            ?: book.fileLastModified,
                    )
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = client.get("${chapter.url}/pages", Fetch.XHR).parseAs<List<PageDto>>()

        return pages.map {
            val url = "${chapter.url}/pages/${it.number}" +
                if (!SUPPORTED_IMAGE_TYPES.contains(it.mediaType)) {
                    "?convert=png"
                } else {
                    ""
                }

            Page(it.number, imageUrl = url)
        }
    }

    override fun getImageRequest(page: Page) = super.getImageRequest(page).newBuilder()
        .header("Accept", "image/*,*/*;q=0.8").build()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()

        val filters = mutableListOf<Filter<*>>(
            UnreadFilter(),
            InProgressFilter(),
            ReadFilter(),
            TypeSelect(),
        ).apply {
            filterData?.collections?.takeIf { it.isNotEmpty() }
                ?.map { CollectionFilterEntry(it.name, it.id, it.ordered) }
                ?.let { add(CollectionSelect(listOf(CollectionFilterEntry("None")) + it)) }
            filterData?.libraries?.takeIf { it.isNotEmpty() }
                ?.let { add(LibraryFilter(it, defaultLibraries)) }
            fun addOptions(name: String, options: List<MultiSelectOption>?, appliesToBooks: Boolean = true, toCondition: (String) -> ConditionDto) {
                if (!options.isNullOrEmpty()) add(MultiSelectFilter(name, options, appliesToBooks, toCondition))
            }
            val status = listOf("Ongoing", "Ended", "Abandoned", "Hiatus").map { MultiSelectOption(it, it.uppercase(Locale.ROOT)) }
            addOptions("Status", status, appliesToBooks = false) { ConditionDto(seriesStatus = OperatorDto("is", it)) }
            addOptions("Genres", filterData?.genres?.map(::MultiSelectOption), appliesToBooks = false) { ConditionDto(genre = OperatorDto("is", it)) }
            addOptions("Tags", filterData?.tags?.map(::MultiSelectOption)) { ConditionDto(tag = OperatorDto("is", it)) }
            addOptions("Publishers", filterData?.publishers?.map(::MultiSelectOption), appliesToBooks = false) { ConditionDto(publisher = OperatorDto("is", it)) }
            addAll(filterData?.authors.orEmpty().map { (role, authors) -> AuthorGroup(role, authors.map { AuthorFilter(it) }) })
            add(SeriesSort())
        }

        return FilterList(filters)
    }

    private val SharedPreferences.libraries
        get() = getString(PREF_LIBRARY_LIST, "[]")!!

    private fun clearCredentials() {
        preferences.edit()
            .putString(PREF_LIBRARY_LIST, "[]")
            .putStringSet(PREF_DEFAULT_LIBRARIES, emptySet<String>())
            .apply()
    }

    private var loginJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        fun defaultLibrarySummary(apiKey: String, username: String): String = if (apiKey.isNotBlank() || username.isNotBlank()) {
            "Show content from selected libraries by default."
        } else {
            "Currently not logged in"
        }
        val libraries = preferences.libraries.parseAs<List<LibraryDto>>()
        val defaultLibraryPref = MultiSelectListPreference(screen.context).apply {
            key = PREF_DEFAULT_LIBRARIES
            title = "Default libraries"
            summary = defaultLibrarySummary(apiKey, username)
            entries = libraries.map { it.name }.toTypedArray()
            entryValues = libraries.map { it.id }.toTypedArray()
            setDefaultValue(emptySet<String>())
            setEnabled(preferences.libraries != "[]")
        }

        fun onCompleteLogin(result: Boolean) {
            defaultLibraryPref.setEnabled(result)
            defaultLibraryPref.summary = if (result) defaultLibrarySummary("unused", "unused") else "Failed to log in"
            defaultLibraryPref.values = emptySet<String>()

            if (result) {
                val libraryList = preferences.libraries.parseAs<List<LibraryDto>>()
                defaultLibraryPref.entries = libraryList.map { it.name }.toTypedArray()
                defaultLibraryPref.entryValues = libraryList.map { it.id }.toTypedArray()
            } else {
                clearCredentials()
            }
        }

        fun logIn() {
            defaultLibraryPref.setEnabled(false)
            defaultLibraryPref.summary = "Loading..."
            clearCredentials()

            loginJob?.cancel()
            loginJob = scope.launch {
                try {
                    val libraries = client.get("$baseUrl/api/v1/libraries", Fetch.XHR).parseAs<List<LibraryDto>>()
                    handler.post {
                        preferences.edit()
                            .putString(PREF_LIBRARY_LIST, libraries.toJsonString())
                            .apply()
                        onCompleteLogin(true)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e

                    handler.post {
                        Toast.makeText(applicationContext, e.message, Toast.LENGTH_SHORT).show()
                        onCompleteLogin(false)
                    }
                }
            }
        }

        screen.addEditTextPreference(
            title = "Source display name",
            default = "",
            summary = displayName.ifBlank { "Here you can change the source display name" },
            key = PREF_DISPLAY_NAME,
            restartRequired = true,
        )

        val addressSummary: (String) -> String = {
            it.ifBlank { "The server address" }
        }
        screen.addEditTextPreference(
            title = "Address",
            default = "",
            summary = addressSummary(baseUrl),
            getSummary = addressSummary,
            dialogMessage = "Komga sources on the same hostname share one login, even on different ports. To use another account on the same server, add it under another hostname (an extra DNS entry).",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            validate = { it.toHttpUrlOrNull() != null },
            validationMessage = "The URL is invalid or malformed",
            key = PREF_ADDRESS,
        ) {
            if (apiKey.isNotBlank() || (username.isNotBlank() && password.isNotBlank())) {
                logIn()
            }
        }

        // API key preference (takes precedence over username/password)
        val apiKeySummary: (String) -> String = {
            if (it.isBlank()) {
                "Optional: Use an API key for authentication"
            } else {
                "*".repeat(it.length)
            }
        }
        screen.addEditTextPreference(
            title = "API key",
            default = "",
            summary = apiKeySummary(apiKey),
            getSummary = apiKeySummary,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            key = PREF_API_KEY,
        ) {
            if (baseUrl.isNotBlank()) {
                logIn()
            }
        }
        // Only show username/password if API key is not set
        if (apiKey.isBlank()) {
            val usernameSummary: (String) -> String = { it.ifBlank { "The user account email" } }
            screen.addEditTextPreference(
                title = "Username",
                default = "",
                summary = usernameSummary(username),
                getSummary = usernameSummary,
                key = PREF_USERNAME,
            ) {
                if (baseUrl.isNotBlank() && apiKey.isBlank() && password.isNotBlank()) {
                    logIn()
                }
            }

            val passwordSummary: (String) -> String = {
                if (it.isBlank()) "The user account password" else "*".repeat(it.length)
            }
            screen.addEditTextPreference(
                title = "Password",
                default = "",
                summary = passwordSummary(password),
                getSummary = passwordSummary,
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                key = PREF_PASSWORD,
            ) {
                if (baseUrl.isNotBlank() && apiKey.isBlank() && username.isNotBlank()) {
                    logIn()
                }
            }
        }

        screen.addPreference(defaultLibraryPref)

        val values = hashMapOf(
            "title" to "",
            "seriesTitle" to "",
            "number" to "",
            "createdDate" to "",
            "releaseDate" to "",
            "size" to "",
            "sizeBytes" to "",
        )
        val stringSubstitutor = StringSubstitutor(values, "{", "}").apply {
            isEnableUndefinedVariableException = true
        }

        screen.addEditTextPreference(
            key = PREF_CHAPTER_NAME_TEMPLATE,
            title = "Chapter title format",
            summary = "Customize how chapter names appear. Chapters in read lists will always be prefixed by the series' name.",
            inputType = InputType.TYPE_CLASS_TEXT,
            default = PREF_CHAPTER_NAME_TEMPLATE_DEFAULT,
            dialogMessage = """
            |Supported placeholders:
            |- {title}: Chapter name
            |- {seriesTitle}: Series name
            |- {number}: Chapter number
            |- {createdDate}: Chapter creation date
            |- {releaseDate}: Chapter release date
            |- {size}: Chapter file size (formatted)
            |- {sizeBytes}: Chapter file size (in bytes)
            |If you wish to place some text between curly brackets, place the escape character "$"
            |before the opening curly bracket, e.g. ${'$'}{series}.
            """.trimMargin(),
            validate = {
                try {
                    stringSubstitutor.replace(it)
                    true
                } catch (e: IllegalArgumentException) {
                    false
                }
            },
            validationMessage = "Invalid chapter title format",
        )

        screen.addEditTextPreference(
            key = PREF_SCANLATOR_SEP,
            title = "Scanlator name separator",
            summary = "Character to use as delimiter for multiple authors",
            default = PREF_SCANLATOR_SEP_DEFAULT,
            inputType = InputType.TYPE_CLASS_TEXT,
            validate = { it.length == 1 },
            validationMessage = "Only one character",
        )
    }

    @Serializable
    class FilterData(
        val libraries: List<LibraryDto>,
        val collections: List<CollectionDto>,
        val genres: Set<String>,
        val tags: Set<String>,
        val publishers: Set<String>,
        val authors: Map<String, List<AuthorDto>>, // roles to list of authors
    )

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val libraries = async { client.get("$baseUrl/api/v1/libraries", Fetch.XHR).parseAs<List<LibraryDto>>() }
        val collections = async { client.get("$baseUrl/api/v1/collections?unpaged=true", Fetch.XHR).parseAs<PageWrapperDto<CollectionDto>>().content }
        val genres = async { client.get("$baseUrl/api/v1/genres", Fetch.XHR).parseAs<Set<String>>() }
        val tags = async { client.get("$baseUrl/api/v1/tags", Fetch.XHR).parseAs<Set<String>>() }
        val publishers = async { client.get("$baseUrl/api/v1/publishers", Fetch.XHR).parseAs<Set<String>>() }
        val authors = async { client.get("$baseUrl/api/v1/authors", Fetch.XHR).parseAs<List<AuthorDto>>().groupBy { it.role } }

        FilterData(
            libraries = libraries.await(),
            collections = collections.await(),
            genres = genres.await(),
            tags = tags.await(),
            publishers = publishers.await(),
            authors = authors.await(),
        ).toJsonElement()
    }

    fun String.isFromReadList() = contains("/api/v1/readlists")

    fun String.isFromBook() = contains("/api/v1/books")

    fun Response.isFromReadList() = request.url.toString().isFromReadList()

    fun Response.isFromBook() = request.url.toString().isFromBook()

    companion object {
        internal const val TYPE_SERIES = "Series"
        internal const val TYPE_READLISTS = "Read lists"
        internal const val TYPE_BOOKS = "Books"

        // Lucene syntax, escaped so search terms match literally
        private val LUCENE_RESERVED = Regex("""[-+&|!(){}\[\]^"~*?:\\/]""")
        private val LUCENE_OPERATOR = Regex("""(?<![^\s\u3000])(AND|OR|NOT)(?![^\s\u3000])""")
    }
}

// Order must match the source { } blocks in build.gradle.kts (used to label factory instances).
private val INSTANCE_IDS = listOf(4508733312114627536L, 8074481155021144106L, 5132811728275817394L)

private const val PREF_DISPLAY_NAME = "Source display name"
private const val PREF_ADDRESS = "Address"
private const val PREF_USERNAME = "Username"
private const val PREF_PASSWORD = "Password"
private const val PREF_API_KEY = "API key"
private const val PREF_LIBRARY_LIST = "library_list"
private const val PREF_DEFAULT_LIBRARIES = "Default libraries"
private const val PREF_CHAPTER_NAME_TEMPLATE = "Chapter name template"
private const val PREF_CHAPTER_NAME_TEMPLATE_DEFAULT = "{number} - {title} ({size})"
private const val PREF_SCANLATOR_SEP = "Scanlator seperator"
private const val PREF_SCANLATOR_SEP_DEFAULT = ","

private val SUPPORTED_IMAGE_TYPES = listOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/jxl", "image/heif", "image/avif")
