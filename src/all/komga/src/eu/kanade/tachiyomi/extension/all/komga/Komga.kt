package eu.kanade.tachiyomi.extension.all.komga

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import android.util.Log
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.extension.all.komga.dto.AuthorDto
import eu.kanade.tachiyomi.extension.all.komga.dto.BookDto
import eu.kanade.tachiyomi.extension.all.komga.dto.CollectionDto
import eu.kanade.tachiyomi.extension.all.komga.dto.LibraryDto
import eu.kanade.tachiyomi.extension.all.komga.dto.PageDto
import eu.kanade.tachiyomi.extension.all.komga.dto.PageWrapperDto
import eu.kanade.tachiyomi.extension.all.komga.dto.ReadListDto
import eu.kanade.tachiyomi.extension.all.komga.dto.SeriesDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.text.StringSubstitutor
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest
import java.util.Locale

open class Komga(private val suffix: String = "") : ConfigurableSource, UnmeteredSource, HttpSource() {

    internal val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val displayName by lazy { preferences.getString(PREF_DISPLAY_NAME, "")!! }

    override val name by lazy {
        val displayNameSuffix = displayName
            .ifBlank { suffix }
            .let { if (it.isNotBlank()) " ($it)" else "" }

        "Komga$displayNameSuffix"
    }

    override val lang = "all"

    override val baseUrl by lazy { preferences.getString(PREF_ADDRESS, "")!!.removeSuffix("/") }

    override val supportsLatest = true

    // keep the previous ID when lang was "en", so that preferences and manga bindings are not lost
    override val id by lazy {
        val key = "komga${if (suffix.isNotBlank()) " ($suffix)" else ""}/en/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    private val username by lazy { preferences.getString(PREF_USERNAME, "")!! }

    private val password by lazy { preferences.getString(PREF_PASSWORD, "")!! }

    private val defaultLibraries
        get() = preferences.getStringSet(PREF_DEFAULT_LIBRARIES, emptySet())!!

    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "TachiyomiKomga/${AppInfo.getVersionName()}")

    override val client: OkHttpClient =
        network.client.newBuilder()
            .authenticator { _, response ->
                if (response.request.header("Authorization") != null) {
                    null // Give up, we've already failed to authenticate.
                } else {
                    response.request.newBuilder()
                        .addHeader("Authorization", Credentials.basic(username, password))
                        .build()
                }
            }
            .dns(Dns.SYSTEM) // don't use DNS over HTTPS as it breaks IP addressing
            .build()

    override fun popularMangaRequest(page: Int): Request =
        searchMangaRequest(
            page,
            "",
            FilterList(
                SeriesSort(Filter.Sort.Selection(1, true)),
            ),
        )

    override fun popularMangaParse(response: Response): MangasPage =
        processSeriesPage(response, baseUrl)

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(
            page,
            "",
            FilterList(
                SeriesSort(Filter.Sort.Selection(3, false)),
            ),
        )

    override fun latestUpdatesParse(response: Response): MangasPage =
        processSeriesPage(response, baseUrl)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val collectionId = (filters.find { it is CollectionSelect } as? CollectionSelect)?.let {
            it.collections[it.state].id
        }

        val type = when {
            collectionId != null -> "collections/$collectionId/series"
            filters.find { it is TypeSelect }?.state == 1 -> "readlists"
            else -> "series"
        }

        val url = "$baseUrl/api/v1/$type?search=$query&page=${page - 1}&deleted=false".toHttpUrl().newBuilder()
        val filterList = filters.ifEmpty { getFilterList() }
        val defaultLibraries = defaultLibraries

        if (filterList.filterIsInstance<LibraryFilter>().isEmpty() && defaultLibraries.isNotEmpty()) {
            url.addQueryParameter("library_id", defaultLibraries.joinToString(","))
        }

        filterList.forEach { filter ->
            when (filter) {
                is UriFilter -> filter.addToUri(url)
                is Filter.Sort -> {
                    val state = filter.state ?: return@forEach

                    val sortCriteria = when (state.index) {
                        0 -> "relevance"
                        1 -> if (type == "series") "metadata.titleSort" else "name"
                        2 -> "createdDate"
                        3 -> "lastModifiedDate"
                        4 -> "random"
                        else -> return@forEach
                    } + "," + if (state.ascending) "asc" else "desc"

                    url.addQueryParameter("sort", sortCriteria)
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage =
        processSeriesPage(response, baseUrl)

    private fun processSeriesPage(response: Response, baseUrl: String): MangasPage {
        val data = if (response.isFromReadList()) {
            response.parseAs<PageWrapperDto<ReadListDto>>()
        } else {
            response.parseAs<PageWrapperDto<SeriesDto>>()
        }

        return MangasPage(data.content.map { it.toSManga(baseUrl) }, !data.last)
    }

    override fun getMangaUrl(manga: SManga) = manga.url.replace("/api/v1", "")

    override fun mangaDetailsRequest(manga: SManga) = GET(manga.url)

    override fun mangaDetailsParse(response: Response): SManga {
        return if (response.isFromReadList()) {
            response.parseAs<ReadListDto>().toSManga(baseUrl)
        } else {
            response.parseAs<SeriesDto>().toSManga(baseUrl)
        }
    }

    private val chapterNameTemplate
        get() = preferences.getString(PREF_CHAPTER_NAME_TEMPLATE, PREF_CHAPTER_NAME_TEMPLATE_DEFAULT)!!

    override fun getChapterUrl(chapter: SChapter) = chapter.url.replace("/api/v1/books", "/book")

    override fun chapterListRequest(manga: SManga): Request =
        GET("${manga.url}/books?unpaged=true&media_status=READY&deleted=false", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
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
                    url = "$baseUrl/api/v1/books/${book.id}"
                    name = book.getChapterName(chapterNameTemplate, isFromReadList)
                    scanlator = book.metadata.authors
                        .filter { it.role == "translator" }
                        .joinToString { it.name }
                    date_upload = when {
                        book.metadata.releaseDate != null -> parseDate(book.metadata.releaseDate)
                        book.created != null -> parseDateTime(book.created)

                        // XXX: `Book.fileLastModified` actually uses the server's running timezone,
                        // not UTC, even if the timestamp ends with a Z! We cannot determine the
                        // server's timezone, which is why this is a last resort option.
                        else -> parseDateTime(book.fileLastModified)
                    }
                }
            }
            .sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter) = GET("${chapter.url}/pages")

    override fun pageListParse(response: Response): List<Page> {
        val pages = response.parseAs<List<PageDto>>()

        return pages.map {
            val url = "${response.request.url}/${it.number}" +
                if (!SUPPORTED_IMAGE_TYPES.contains(it.mediaType)) {
                    "?convert=png"
                } else {
                    ""
                }

            Page(it.number, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers = headersBuilder().add("Accept", "image/*,*/*;q=0.8").build())
    }

    override fun getFilterList(): FilterList {
        fetchFilterOptions()

        val filters = mutableListOf<Filter<*>>(
            UnreadFilter(),
            InProgressFilter(),
            ReadFilter(),
            TypeSelect(),
            CollectionSelect(
                buildList {
                    add(CollectionFilterEntry("None"))
                    collections.forEach {
                        add(CollectionFilterEntry(it.name, it.id))
                    }
                },
            ),
            LibraryFilter(libraries, defaultLibraries),
            UriMultiSelectFilter(
                "Status",
                "status",
                listOf("Ongoing", "Ended", "Abandoned", "Hiatus").map {
                    UriMultiSelectOption(it, it.uppercase(Locale.ROOT))
                },
            ),
            UriMultiSelectFilter(
                "Genres",
                "genre",
                genres.map { UriMultiSelectOption(it) },
            ),
            UriMultiSelectFilter(
                "Tags",
                "tag",
                tags.map { UriMultiSelectOption(it) },
            ),
            UriMultiSelectFilter(
                "Publishers",
                "publisher",
                publishers.map { UriMultiSelectOption(it) },
            ),
        ).apply {
            if (fetchFilterStatus != FetchFilterStatus.FETCHED) {
                val message = if (fetchFilterStatus == FetchFilterStatus.NOT_FETCHED && fetchFiltersAttempts >= 3) {
                    "Failed to fetch filtering options from the server"
                } else {
                    "Press 'Reset' to show filtering options"
                }

                add(0, Filter.Header(message))
                add(1, Filter.Separator())
            }

            addAll(authors.map { (role, authors) -> AuthorGroup(role, authors.map { AuthorFilter(it) }) })
            add(SeriesSort())
        }

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        fetchFilterOptions()

        if (suffix.isEmpty()) {
            ListPreference(screen.context).apply {
                key = PREF_EXTRA_SOURCES_COUNT
                title = "Number of extra sources"
                summary = "Number of additional sources to create. There will always be at least one Komga source."
                entries = PREF_EXTRA_SOURCES_ENTRIES
                entryValues = PREF_EXTRA_SOURCES_ENTRIES

                setDefaultValue(PREF_EXTRA_SOURCES_DEFAULT)
                setOnPreferenceChangeListener { _, _ ->
                    Toast.makeText(screen.context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    true
                }
            }.also(screen::addPreference)
        }

        screen.addEditTextPreference(
            title = "Source display name",
            default = suffix,
            summary = displayName.ifBlank { "Here you can change the source displayed suffix" },
            key = PREF_DISPLAY_NAME,
            restartRequired = true,
        )
        screen.addEditTextPreference(
            title = "Address",
            default = "",
            summary = baseUrl.ifBlank { "The server address" },
            dialogMessage = "The address must not end with a forward slash.",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            validate = { it.toHttpUrlOrNull() != null && !it.endsWith("/") },
            validationMessage = "The URL is invalid, malformed, or ends with a slash",
            key = PREF_ADDRESS,
            restartRequired = true,
        )
        screen.addEditTextPreference(
            title = "Username",
            default = "",
            summary = username.ifBlank { "The user account email" },
            key = PREF_USERNAME,
            restartRequired = true,
        )
        screen.addEditTextPreference(
            title = "Password",
            default = "",
            summary = if (password.isBlank()) "The user account password" else "*".repeat(password.length),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            key = PREF_PASSWORD,
            restartRequired = true,
        )

        MultiSelectListPreference(screen.context).apply {
            key = PREF_DEFAULT_LIBRARIES
            title = "Default libraries"
            summary = buildString {
                append("Show content from selected libraries by default.")

                if (libraries.isEmpty()) {
                    append(" Exit and enter the settings menu to load options.")
                }
            }
            entries = libraries.map { it.name }.toTypedArray()
            entryValues = libraries.map { it.id }.toTypedArray()
            setDefaultValue(emptySet<String>())
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
    }

    private var libraries = emptyList<LibraryDto>()
    private var collections = emptyList<CollectionDto>()
    private var genres = emptySet<String>()
    private var tags = emptySet<String>()
    private var publishers = emptySet<String>()
    private var authors = emptyMap<String, List<AuthorDto>>() // roles to list of authors

    private var fetchFilterStatus = FetchFilterStatus.NOT_FETCHED
    private var fetchFiltersAttempts = 0
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun fetchFilterOptions() {
        if (baseUrl.isBlank() || fetchFilterStatus != FetchFilterStatus.NOT_FETCHED || fetchFiltersAttempts >= 3) {
            return
        }

        fetchFilterStatus = FetchFilterStatus.FETCHING
        fetchFiltersAttempts++

        scope.launch {
            try {
                libraries = client.newCall(GET("$baseUrl/api/v1/libraries")).await().parseAs()
                collections = client
                    .newCall(GET("$baseUrl/api/v1/collections?unpaged=true"))
                    .await()
                    .parseAs<PageWrapperDto<CollectionDto>>()
                    .content
                genres = client.newCall(GET("$baseUrl/api/v1/genres")).await().parseAs()
                tags = client.newCall(GET("$baseUrl/api/v1/tags")).await().parseAs()
                publishers = client.newCall(GET("$baseUrl/api/v1/publishers")).await().parseAs()
                authors = client
                    .newCall(GET("$baseUrl/api/v1/authors"))
                    .await()
                    .parseAs<List<AuthorDto>>()
                    .groupBy { it.role }
                fetchFilterStatus = FetchFilterStatus.FETCHED
            } catch (e: Exception) {
                fetchFilterStatus = FetchFilterStatus.NOT_FETCHED
                Log.e(logTag, "Failed to fetch filtering options", e)
            }
        }
    }

    fun Response.isFromReadList() = request.url.toString().contains("/api/v1/readlists")

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())

    private val logTag by lazy { "komga${if (suffix.isNotBlank()) ".$suffix" else ""}" }

    companion object {
        internal const val PREF_EXTRA_SOURCES_COUNT = "Number of extra sources"
        internal const val PREF_EXTRA_SOURCES_DEFAULT = "2"

        internal const val TYPE_SERIES = "Series"
        internal const val TYPE_READLISTS = "Read lists"
    }
}

private enum class FetchFilterStatus {
    NOT_FETCHED,
    FETCHING,
    FETCHED,
}

private val PREF_EXTRA_SOURCES_ENTRIES = (0..10).map { it.toString() }.toTypedArray()

private const val PREF_DISPLAY_NAME = "Source display name"
private const val PREF_ADDRESS = "Address"
private const val PREF_USERNAME = "Username"
private const val PREF_PASSWORD = "Password"
private const val PREF_DEFAULT_LIBRARIES = "Default libraries"
private const val PREF_CHAPTER_NAME_TEMPLATE = "Chapter name template"
private const val PREF_CHAPTER_NAME_TEMPLATE_DEFAULT = "{number} - {title} ({size})"

private val SUPPORTED_IMAGE_TYPES = listOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/jxl", "image/heif", "image/avif")
