package eu.kanade.tachiyomi.extension.all.komga

import android.content.SharedPreferences
import android.text.InputType
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
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.text.StringSubstitutor
import uy.kohesive.injekt.injectLazy
import java.util.Locale

@Source
abstract class Komga :
    KeiSource(),
    ConfigurableSource,
    UnmeteredSource {

    internal val preferences: SharedPreferences by getPreferencesLazy {
        val oldUrl = getString("Address", "").orEmpty()
        if (oldUrl.isEmpty()) return@getPreferencesLazy

        edit().apply {
            putString("overrideBaseUrl", oldUrl)
            remove("Address")
        }.apply()
    }

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

    override val supportsLatest = true

    private val username by lazy { preferences.getString(PREF_USERNAME, "")!! }

    private val password by lazy { preferences.getString(PREF_PASSWORD, "")!! }

    private val apiKey by lazy { preferences.getString(PREF_API_KEY, "")!! }

    private val defaultLibraries
        get() = preferences.getStringSet(PREF_DEFAULT_LIBRARIES, emptySet())!!

    private val json: Json by injectLazy()

    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", "TachiyomiKomga/${AppInfo.getVersionName()}")
        if (apiKey.isNotBlank()) {
            set("X-API-Key", apiKey)
        }
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
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

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = searchMangaUrl(
            page,
            "",
            FilterList(
                SeriesSort(Filter.Sort.Selection(1, true)),
            ),
        )

        return processSeriesPage(client.get(url), baseUrl)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = searchMangaUrl(
            page,
            "",
            FilterList(
                SeriesSort(Filter.Sort.Selection(3, false)),
            ),
        )

        return processSeriesPage(client.get(url), baseUrl)
    }

    private fun searchMangaUrl(page: Int, query: String, filters: FilterList): String {
        val collectionId = (filters.find { it is CollectionSelect } as? CollectionSelect)?.let {
            it.collections[it.state].id
        }

        val type = when {
            collectionId != null -> "collections/$collectionId/series"
            filters.find { it is TypeSelect }?.state == 1 -> "readlists"
            filters.find { it is TypeSelect }?.state == 2 -> "books"
            else -> "series"
        }

        val url = "$baseUrl/api/v1".toHttpUrl().newBuilder()
            .addPathSegments(type)
            .addQueryParameter("search", query)
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("deleted", "false")

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

        return url.build().toString()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = searchMangaUrl(page, query, filters)
        return processSeriesPage(client.get(url), baseUrl)
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
        val response = client.get(manga.url)

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

    override fun getChapterUrl(chapter: SChapter) = chapter.url.replace("/api/v1/books", "/book")

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val url = when {
            manga.url.isFromBook() -> "${manga.url}?unpaged=true&media_status=READY&deleted=false"
            else -> "${manga.url}/books?unpaged=true&media_status=READY&deleted=false"
        }
        val response = client.get(url)

        if (response.isFromBook()) {
            val book = response.parseAs<BookDto>()
            return listOf(
                SChapter.create().apply {
                    chapter_number = 1F
                    this.url = "$baseUrl/api/v1/books/${book.id}"
                    name = book.getChapterName(chapterNameTemplate, isFromReadList = true)
                    scanlator = book.metadata.authors
                        .filter { it.role == "translator" }
                        .joinToString { it.name }
                    date_upload = when {
                        book.metadata.releaseDate != null -> parseDate(book.metadata.releaseDate)
                        book.created != null -> parseDateTime(book.created)
                        else -> parseDateTime(book.fileLastModified)
                    }
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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = client.get("${chapter.url}/pages").parseAs<List<PageDto>>()

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

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers = headersBuilder().add("Accept", "image/*,*/*;q=0.8").build())

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()

        val filters = mutableListOf<Filter<*>>(
            UnreadFilter(),
            InProgressFilter(),
            ReadFilter(),
            TypeSelect(),
            CollectionSelect(
                buildList {
                    add(CollectionFilterEntry("None"))
                    filterData?.collections?.forEach {
                        add(CollectionFilterEntry(it.name, it.id))
                    }
                },
            ),
            LibraryFilter(filterData?.libraries.orEmpty(), defaultLibraries),
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
                filterData?.genres.orEmpty().map { UriMultiSelectOption(it) },
            ),
            UriMultiSelectFilter(
                "Tags",
                "tag",
                filterData?.tags.orEmpty().map { UriMultiSelectOption(it) },
            ),
            UriMultiSelectFilter(
                "Publishers",
                "publisher",
                filterData?.publishers.orEmpty().map { UriMultiSelectOption(it) },
            ),
        ).apply {
            addAll(filterData?.authors.orEmpty().map { (role, authors) -> AuthorGroup(role, authors.map { AuthorFilter(it) }) })
            add(SeriesSort())
        }

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addEditTextPreference(
            title = "Source display name",
            default = "",
            summary = displayName.ifBlank { "Here you can change the source display name" },
            key = PREF_DISPLAY_NAME,
            restartRequired = true,
        )
        // API key preference (takes precedence over username/password)
        screen.addEditTextPreference(
            title = "API key",
            default = "",
            summary = if (apiKey.isBlank()) "Optional: Use an API key for authentication" else "*".repeat(apiKey.length),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            key = PREF_API_KEY,
            restartRequired = true,
        )
        // Only show username/password if API key is not set
        if (apiKey.isBlank()) {
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
        }

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

    @Serializable
    class FilterData(
        val libraries: List<LibraryDto>,
        val collections: List<CollectionDto>,
        val genres: Set<String>,
        val tags: Set<String>,
        val publishers: Set<String>,
        val authors: Map<String, List<AuthorDto>>, // roles to list of authors
    )

    override suspend fun fetchFilterData(): JsonElement {
        assert(baseUrl.isNotBlank())

        val libraries = client.get("$baseUrl/api/v1/libraries").parseAs<List<LibraryDto>>()
        val collections = client.get("$baseUrl/api/v1/collections?unpaged=true").parseAs<PageWrapperDto<CollectionDto>>().content
        val genres = client.get("$baseUrl/api/v1/genres").parseAs<Set<String>>()
        val tags = client.get("$baseUrl/api/v1/tags").parseAs<Set<String>>()
        val publishers = client.get("$baseUrl/api/v1/publishers").parseAs<Set<String>>()
        val authors = client.get("$baseUrl/api/v1/authors").parseAs<List<AuthorDto>>().groupBy { it.role }

        return FilterData(
            libraries = libraries,
            collections = collections,
            genres = genres,
            tags = tags,
            publishers = publishers,
            authors = authors,
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
    }
}

// Order must match the source { } blocks in build.gradle.kts (used to label factory instances).
private val INSTANCE_IDS = listOf(4508733312114627536L, 8074481155021144106L, 5132811728275817394L)

private const val PREF_DISPLAY_NAME = "Source display name"
private const val PREF_USERNAME = "Username"
private const val PREF_PASSWORD = "Password"
private const val PREF_API_KEY = "API key"
private const val PREF_DEFAULT_LIBRARIES = "Default libraries"
private const val PREF_CHAPTER_NAME_TEMPLATE = "Chapter name template"
private const val PREF_CHAPTER_NAME_TEMPLATE_DEFAULT = "{number} - {title} ({size})"

private val SUPPORTED_IMAGE_TYPES = listOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/jxl", "image/heif", "image/avif")
