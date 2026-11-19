package eu.kanade.tachiyomi.extension.all.komga

import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.ListPreference
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.apache.commons.text.StringSubstitutor
import java.io.IOException
import java.util.Locale

@Source
class Komga(
    override val lang: String,
    override val id: Long,
) : KeiSource(),
    ConfigurableSource,
    UnmeteredSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

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

    private val cfAccessClientId
        get() = preferences.getString(PREF_CF_ACCESS_CLIENT_ID, "")!!

    private val cfAccessClientSecret
        get() = preferences.getString(PREF_CF_ACCESS_CLIENT_SECRET, "")!!

    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", "TachiyomiKomga/${AppInfo.getVersionName()}")
        if (apiKey.isNotBlank()) {
            set("X-API-Key", apiKey)
        }
        if (cfAccessClientId.isNotBlank()) {
            set("CF-Access-Client-Id", cfAccessClientId)
            set("CF-Access-Client-Secret", cfAccessClientSecret)
        }
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::toCurrentAddress)
        addInterceptor(::authenticate)
        addNetworkInterceptor(::withoutKomgaCookies)
        dns(Dns.SYSTEM) // don't use DNS over HTTPS as it breaks IP addressing
    }

    private fun toCurrentAddress(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return chain.proceed(request.newBuilder().url(request.url.atCurrentAddress()).build())
    }

    // Stored urls keep the address the entry was added with
    private fun HttpUrl.atCurrentAddress(): HttpUrl {
        val api = pathSegments.lastIndexOf("api")
        val server = baseUrl.toHttpUrlOrNull()
        if (api < 0 || server == null) return this
        return server.newBuilder()
            .apply { encodedPathSegments.drop(api).forEach(::addEncodedPathSegment) }
            .encodedQuery(encodedQuery)
            .build()
    }

    // An empty token: Komga keeps no session for this API key (1.29+)
    private class Session(val account: String, val token: String)

    @Volatile
    private var session: Session? = null
    private val loginLock = Any()

    // Each source keeps its own Komga session in X-Auth-Token
    private fun authenticate(chain: Interceptor.Chain): Response {
        val request = chain.request()
        fun withToken(token: String) = request.newBuilder().header("X-Auth-Token", token).build()

        val account = "$baseUrl\n$apiKey\n$username\n$password"
        val token = session?.takeIf { it.account == account }?.token
        if (token != null) {
            val response = chain.proceed(withToken(token))
            if (response.code != 401) {
                // Komga before 1.29 replaces a dead token for an API key
                response.newSessionToken()?.takeIf { response.isSuccessful && it != token }?.let { session = Session(account, it) }
                return response
            }
            if (apiKey.isNotBlank()) response.ensureAuthorized()
            response.close()
        }

        synchronized(loginLock) {
            // Another request may have logged in while this one waited
            val latest = session?.takeIf { it.account == account && it.token != token }?.token
            if (latest != null) return chain.proceed(withToken(latest))

            // An empty X-Auth-Token asks Komga to return the new session in that header
            val login = withToken("").newBuilder()
                .apply { if (apiKey.isBlank()) header("Authorization", Credentials.basic(username, password)) }
                .build()
            val response = chain.proceed(login)
            response.ensureAuthorized()
            if (response.isSuccessful) session = Session(account, response.newSessionToken().orEmpty())
            return response
        }
    }

    // Komga's login cookies from the WebView would pick the account (remember-me even over a dead token)
    private fun withoutKomgaCookies(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cookies = request.header("Cookie") ?: return chain.proceed(request)
        val kept = cookies.split("; ").filterNot { it.substringBefore("=") in KOMGA_COOKIES }
        val stripped = request.newBuilder()
            .apply { if (kept.isEmpty()) removeHeader("Cookie") else header("Cookie", kept.joinToString("; ")) }
            .build()
        return chain.proceed(stripped)
    }

    // From the server only: a 304 from the app's shared cache carries the headers, token included, of whichever source cached it
    private fun Response.newSessionToken() = networkResponse?.header("X-Auth-Token")

    private fun Response.ensureAuthorized() {
        if (code == 401) {
            close()
            throw IOException("Check the username, password or API key in Settings")
        }
    }

    private val defaultType
        get() = preferences.getString(PREF_DEFAULT_TYPE, TYPE_SERIES)!!

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(TypeSelect(defaultType), SeriesSort(Filter.Sort.Selection(1, true))))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(TypeSelect(defaultType), SeriesSort(Filter.Sort.Selection(3, false))))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (baseUrl.isBlank()) throw Exception("Set Address in Settings")

        val collection = filters.firstInstanceOrNull<CollectionSelect>()?.let { it.collections[it.state] }?.takeIf { it.id != null }
        val type = if (collection != null) TYPE_SERIES else filters.firstInstanceOrNull<TypeSelect>()?.type ?: defaultType
        val libraryFilter = filters.firstInstanceOrNull<LibraryFilter>()

        val apiRoot = "$baseUrl/api"
        val url = "$apiRoot/v1".toHttpUrl().newBuilder()
            .addPathSegments(
                when (type) {
                    TYPE_READLISTS -> "readlists"
                    TYPE_BOOKS -> "books/list"
                    else -> "series/list"
                },
            )
            .addQueryParameter("page", (page - 1).toString())

        val term = query.trim().replace(LUCENE_RESERVED, """\\$0""").replace(LUCENE_OPERATOR) { it.value.lowercase() }
        val searchTerm = when {
            term.isEmpty() || type == TYPE_READLISTS -> term
            // A tapped author, genre or tag searches for its name
            else -> "(($term) OR author:($term) OR genre:($term) OR tag:($term))"
        }
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
            if (searchTerm.isNotEmpty()) url.addQueryParameter("search", searchTerm)
            if (libraryIds.isNotEmpty()) url.addQueryParameter("library_id", libraryIds.joinToString(","))
            val data = client.get(url.build(), Fetch.XHR).parseAs<PageWrapperDto<ReadListDto>>()
            return MangasPage(data.content.map { it.toSManga(apiRoot) }, !data.last)
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
        return if (type == TYPE_BOOKS) {
            val data = response.parseAs<PageWrapperDto<BookDto>>()
            MangasPage(data.content.map { it.toSManga(apiRoot) }, !data.last)
        } else {
            val data = response.parseAs<PageWrapperDto<SeriesDto>>()
            MangasPage(data.content.map { it.toSManga(apiRoot) }, !data.last)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val server = baseUrl.toHttpUrlOrNull() ?: return null
        if (url.host != server.host || url.port != server.port) return null
        val (type, id) = WEB_URL.find(url.encodedPath)?.destructured ?: return null
        val manga = SManga.create().apply {
            this.url = "$baseUrl/api/v1/${if (type.startsWith("book")) "books" else type}/$id"
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = manga.url
        return when {
            url.contains("/api/v1/books/") -> {
                val book = client.get(url, Fetch.XHR).parseAs<BookDto>()
                SMangaUpdate(book.toSManga(manga.apiRoot), listOf(book.toSChapter(url, isFromReadList = true, book.metadata.title, chapterNumber = 1F)))
            }
            url.contains("/api/v1/readlists/") -> coroutineScope {
                val details = async { if (fetchDetails) client.get(url, Fetch.XHR).parseAs<ReadListDto>().toSManga(manga.apiRoot) else manga }
                val books = async {
                    if (fetchChapters) {
                        val booksUrl = url.toHttpUrl().newBuilder()
                            .addPathSegment("books")
                            .addQueryParameter("unpaged", "true")
                            .addQueryParameter("media_status", "READY")
                            .addQueryParameter("deleted", "false")
                            .build()
                        client.get(booksUrl, Fetch.XHR)
                            .parseAs<PageWrapperDto<BookDto>>().content
                            .toSChapters(manga, isFromReadList = true)
                    } else {
                        chapters
                    }
                }
                SMangaUpdate(details.await(), books.await())
            }
            else -> coroutineScope {
                val details = async { if (fetchDetails) client.get(url, Fetch.XHR).parseAs<SeriesDto>().toSManga(manga.apiRoot) else manga }
                val books = async { if (fetchChapters) getSeriesChapters(manga) else chapters }
                SMangaUpdate(details.await(), books.await())
            }
        }
    }

    private suspend fun getSeriesChapters(manga: SManga): List<SChapter> {
        val seriesId = manga.url.toHttpUrl().pathSegments.last()
        val url = "$baseUrl/api/v1/books/list".toHttpUrl().newBuilder()
            .addQueryParameter("unpaged", "true")
            .addQueryParameter("sort", "metadata.numberSort,asc")
            .build()
        val body = SearchDto(
            ConditionDto(
                allOf = listOf(
                    ConditionDto(seriesId = OperatorDto("is", seriesId)),
                    ConditionDto(mediaStatus = OperatorDto("is", "READY")),
                    ConditionDto(deleted = OperatorDto("isFalse")),
                ),
            ),
        ).toJsonRequestBody()
        return client.post(url, Fetch.XHR, body).parseAs<PageWrapperDto<BookDto>>().content
            .toSChapters(manga, isFromReadList = false)
    }

    override fun getMangaUrl(manga: SManga) = manga.url.toWebUrl()

    override fun getChapterUrl(chapter: SChapter) = chapter.url.toWebUrl()

    private fun String.toWebUrl() = toHttpUrl().atCurrentAddress().toString()
        .replace("/api/v1/books/", "/book/")
        .replace("/api/v1/", "/")

    private fun List<BookDto>.toSChapters(manga: SManga, isFromReadList: Boolean): List<SChapter> {
        val apiRoot = manga.apiRoot
        return filter { it.media?.mediaProfile != "EPUB" || it.media.epubDivinaCompatible }
            .mapIndexed { index, book ->
                val url = "$apiRoot/v1/books/${book.id}"
                book.toSChapter(url, isFromReadList, manga.title, chapterNumber = if (isFromReadList) index + 1F else book.metadata.numberSort)
            }
            .reversed()
    }

    // The entry's chapters and details keep the address it was stored with, so the app still matches them
    private val SManga.apiRoot: String
        get() {
            val url = url.toHttpUrl()
            val prefix = url.encodedPathSegments.take(url.pathSegments.lastIndexOf("api") + 1)
            return url.newBuilder()
                .encodedPath("/")
                .query(null)
                .apply { prefix.forEach(::addEncodedPathSegment) }
                .build()
                .toString()
        }

    private fun BookDto.toSChapter(url: String, isFromReadList: Boolean, mangaTitle: String, chapterNumber: Float?) = SChapter.create().apply {
        this.url = url
        name = getChapterName(chapterNameTemplate, isFromReadList, mangaTitle)
        chapter_number = chapterNumber ?: -1F
        scanlator = getScanlator(separator)
        date_upload = date?.let(::parseDate) ?: 0L
    }

    private val chapterNameTemplate
        get() = preferences.getString(PREF_CHAPTER_NAME_TEMPLATE, PREF_CHAPTER_NAME_TEMPLATE_DEFAULT)!!

    private val separator
        get() = preferences.getString(PREF_SCANLATOR_SEP, PREF_SCANLATOR_SEP_DEFAULT)!!
            .let { if (it == ",") "$it " else " $it " }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val bookUrl = chapter.url.toHttpUrl().atCurrentAddress()
        val pages = client.get(bookUrl.newBuilder().addPathSegment("pages").build(), Fetch.XHR).parseAs<List<PageDto>>()

        return pages.map {
            val url = bookUrl.newBuilder()
                .addPathSegment("pages")
                .addPathSegment(it.number.toString())
                .apply { if (it.mediaType !in SUPPORTED_IMAGE_TYPES) addQueryParameter("convert", "png") }
                .build()
            Page(it.number, imageUrl = url.toString())
        }
    }

    override fun getImageRequest(page: Page) = super.getImageRequest(page).newBuilder()
        .header("Accept", "image/*,*/*;q=0.8").build()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        // Cached for another address or account: KeiSource refetches when this throws
        check(filterData == null || filterData.server == serverKey) { "Filters of another server" }

        val filters = mutableListOf<Filter<*>>(
            TypeSelect(defaultType),
            SeriesSort(),
            UnreadFilter(),
            InProgressFilter(),
            ReadFilter(),
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
        }

        return FilterList(filters)
    }

    @Serializable
    class FilterData(
        val server: String,
        val libraries: List<LibraryDto> = emptyList(),
        val collections: List<CollectionDto> = emptyList(),
        val genres: Set<String> = emptySet(),
        val tags: Set<String> = emptySet(),
        val publishers: Set<String> = emptySet(),
        val authors: Map<String, List<AuthorDto>> = emptyMap(), // roles to list of authors
    )

    override suspend fun fetchFilterData(): JsonElement {
        val server = serverKey
        if (baseUrl.isBlank()) return FilterData(server).toJsonElement()

        return coroutineScope {
            val libraries = async { client.get("$baseUrl/api/v1/libraries", Fetch.XHR).parseAs<List<LibraryDto>>() }
            val collections = async { client.get("$baseUrl/api/v1/collections?unpaged=true", Fetch.XHR).parseAs<PageWrapperDto<CollectionDto>>().content }
            val genres = async { client.get("$baseUrl/api/v1/genres", Fetch.XHR).parseAs<Set<String>>() }
            val tags = async { client.get("$baseUrl/api/v1/tags", Fetch.XHR).parseAs<Set<String>>() }
            val publishers = async { client.get("$baseUrl/api/v1/publishers", Fetch.XHR).parseAs<Set<String>>() }
            val authors = async { client.get("$baseUrl/api/v1/authors", Fetch.XHR).parseAs<List<AuthorDto>>().groupBy { it.role } }

            val libraryList = libraries.await()
            val libraryIds = libraryList.map { it.id }.toSet()
            preferences.edit()
                .putString(PREF_LIBRARY_LIST, libraryList.toJsonString())
                .putStringSet(PREF_DEFAULT_LIBRARIES, defaultLibraries.intersect(libraryIds))
                .apply()

            FilterData(
                server = server,
                libraries = libraryList,
                collections = collections.await(),
                genres = genres.await(),
                tags = tags.await(),
                publishers = publishers.await(),
                authors = authors.await(),
            ).toJsonElement()
        }
    }

    // Address and account the cached filters and library list belong to
    private val serverKey
        get() = "$baseUrl\n$username\n$apiKey".sha256().toHexString()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addEditTextPreference(
            title = "Source display name",
            default = "",
            summary = displayName.ifBlank { "Here you can change the source display name" },
            key = PREF_DISPLAY_NAME,
            restartRequired = true,
        )

        val addressSummary: (String) -> String = { it.ifBlank { "The server address" } }
        screen.addEditTextPreference(
            title = "Address",
            default = "",
            summary = addressSummary(baseUrl),
            getSummary = addressSummary,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            validate = { it.toHttpUrlOrNull() != null },
            validationMessage = "The URL is invalid or malformed",
            key = PREF_ADDRESS,
        )

        val apiKeySummary: (String) -> String = {
            if (it.isBlank()) "Optional, used instead of the username and password" else "*".repeat(it.length)
        }
        screen.addEditTextPreference(
            title = "API key",
            default = "",
            summary = apiKeySummary(apiKey),
            getSummary = apiKeySummary,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            key = PREF_API_KEY,
        )

        val usernameSummary: (String) -> String = { it.ifBlank { "The user account email" } }
        screen.addEditTextPreference(
            title = "Username",
            default = "",
            summary = usernameSummary(username),
            getSummary = usernameSummary,
            key = PREF_USERNAME,
        )

        val passwordSummary: (String) -> String = { if (it.isBlank()) "The user account password" else "*".repeat(it.length) }
        screen.addEditTextPreference(
            title = "Password",
            default = "",
            summary = passwordSummary(password),
            getSummary = passwordSummary,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            key = PREF_PASSWORD,
        )

        ListPreference(screen.context).apply {
            key = PREF_DEFAULT_TYPE
            title = "Default type"
            summary = "Listed in Popular and Latest, and preselected in the filters: %s"
            entries = TYPES
            entryValues = TYPES
            setDefaultValue(TYPE_SERIES)
        }.also(screen::addPreference)

        val cfAccessIdSummary: (String) -> String = { it.ifBlank { "Optional, for a server behind Cloudflare Access" } }
        screen.addEditTextPreference(
            title = "Cloudflare Access client ID",
            default = "",
            summary = cfAccessIdSummary(cfAccessClientId),
            getSummary = cfAccessIdSummary,
            dialogMessage = "The ID and secret of a Cloudflare Access service token",
            key = PREF_CF_ACCESS_CLIENT_ID,
        )
        val cfAccessSecretSummary: (String) -> String = { if (it.isBlank()) "Optional, for a server behind Cloudflare Access" else "*".repeat(it.length) }
        screen.addEditTextPreference(
            title = "Cloudflare Access client secret",
            default = "",
            summary = cfAccessSecretSummary(cfAccessClientSecret),
            getSummary = cfAccessSecretSummary,
            dialogMessage = "The ID and secret of a Cloudflare Access service token",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            key = PREF_CF_ACCESS_CLIENT_SECRET,
        )

        val libraries = preferences.getString(PREF_LIBRARY_LIST, "[]")!!.parseAs<List<LibraryDto>>()
        MultiSelectListPreference(screen.context).apply {
            key = PREF_DEFAULT_LIBRARIES
            title = "Default libraries"
            summary = if (libraries.isEmpty()) "Open the source once to load the libraries" else "Show content from the selected libraries by default"
            entries = libraries.map { it.name }.toTypedArray()
            entryValues = libraries.map { it.id }.toTypedArray()
            setDefaultValue(emptySet<String>())
            setEnabled(libraries.isNotEmpty())
        }.also(screen::addPreference)

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

    companion object {
        internal const val TYPE_SERIES = "Series"
        internal const val TYPE_READLISTS = "Read lists"
        internal const val TYPE_BOOKS = "Books"
        internal val TYPES = arrayOf(TYPE_SERIES, TYPE_READLISTS, TYPE_BOOKS)

        // Lucene syntax, escaped so search terms match literally
        private val LUCENE_RESERVED = Regex("""[-+&|!(){}\[\]^"~*?:\\/]""")

        // Web UI and API paths of an entry
        private val WEB_URL = Regex("""/(?:api/v1/)?(series|books?|readlists)/(\w+)""")
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
private const val PREF_CF_ACCESS_CLIENT_ID = "cf_access_client_id"
private const val PREF_CF_ACCESS_CLIENT_SECRET = "cf_access_client_secret"
private const val PREF_LIBRARY_LIST = "library_list"
private const val PREF_DEFAULT_LIBRARIES = "Default libraries"
private const val PREF_DEFAULT_TYPE = "Default type"
private const val PREF_CHAPTER_NAME_TEMPLATE = "Chapter name template"
private const val PREF_CHAPTER_NAME_TEMPLATE_DEFAULT = "{number} - {title} ({size})"
private const val PREF_SCANLATOR_SEP = "Scanlator seperator"
private const val PREF_SCANLATOR_SEP_DEFAULT = ","

private val KOMGA_COOKIES = setOf("KOMGA-SESSION", "komga-remember-me")

private val SUPPORTED_IMAGE_TYPES = listOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/jxl", "image/heif", "image/avif")
