package eu.kanade.tachiyomi.extension.all.komga

import android.app.Application
import android.content.SharedPreferences
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
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
import eu.kanade.tachiyomi.source.online.HttpSource
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
import rx.Single
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.security.MessageDigest
import java.util.Locale

open class Komga(private val suffix: String = "") : ConfigurableSource, UnmeteredSource, HttpSource() {

    override val name by lazy { "Komga${displayName.ifBlank { suffix }.let { if (it.isNotBlank()) " ($it)" else "" }}" }

    override val lang = "all"

    override val baseUrl by lazy { preferences.getString(PREF_ADDRESS, "")!!.removeSuffix("/") }

    override val supportsLatest = true

    // keep the previous ID when lang was "en", so that preferences and manga bindings are not lost
    override val id by lazy {
        val key = "komga${if (suffix.isNotBlank()) " ($suffix)" else ""}/en/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    private val json: Json by injectLazy()

    internal val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val displayName by lazy { preferences.getString(PREF_DISPLAY_NAME, "")!! }
    private val username by lazy { preferences.getString(PREF_USERNAME, "")!! }
    private val password by lazy { preferences.getString(PREF_PASSWORD, "")!! }

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
            FilterList(SeriesSort()),
        )

    override fun popularMangaParse(response: Response): MangasPage =
        processSeriesPage(response)

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(
            page,
            "",
            FilterList(SeriesSort(Filter.Sort.Selection(2, false))),
        )

    override fun latestUpdatesParse(response: Response): MangasPage =
        processSeriesPage(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        runCatching { fetchFilterOptions() }

        val collectionId = (filters.find { it is CollectionSelect } as? CollectionSelect)?.let {
            it.values[it.state].id
        }

        val type = when {
            collectionId != null -> "collections/$collectionId/series"
            filters.find { it is TypeSelect }?.state == 1 -> "readlists"
            else -> "series"
        }

        val url = "$baseUrl/api/v1/$type?search=$query&page=${page - 1}&deleted=false".toHttpUrl().newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is UnreadFilter -> {
                    if (filter.state) {
                        url.addQueryParameter("read_status", "UNREAD")
                        url.addQueryParameter("read_status", "IN_PROGRESS")
                    }
                }
                is InProgressFilter -> {
                    if (filter.state) {
                        url.addQueryParameter("read_status", "IN_PROGRESS")
                    }
                }
                is ReadFilter -> {
                    if (filter.state) {
                        url.addQueryParameter("read_status", "READ")
                    }
                }
                is LibraryGroup -> {
                    val libraryToInclude = filter.state.filter { it.state }.map { it.id }

                    if (libraryToInclude.isNotEmpty()) {
                        url.addQueryParameter("library_id", libraryToInclude.joinToString(","))
                    }
                }
                is StatusGroup -> {
                    val statusToInclude = filter.state.filter { it.state }.map { it.name.uppercase(Locale.ROOT) }

                    if (statusToInclude.isNotEmpty()) {
                        url.addQueryParameter("status", statusToInclude.joinToString(","))
                    }
                }
                is GenreGroup -> {
                    val genreToInclude = filter.state.filter { it.state }.map { it.name }

                    if (genreToInclude.isNotEmpty()) {
                        url.addQueryParameter("genre", genreToInclude.joinToString(","))
                    }
                }
                is TagGroup -> {
                    val tagToInclude = filter.state.filter { it.state }.map { it.name }

                    if (tagToInclude.isNotEmpty()) {
                        url.addQueryParameter("tag", tagToInclude.joinToString(","))
                    }
                }
                is PublisherGroup -> {
                    val publisherToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.state) {
                            publisherToInclude.add(content.name)
                        }
                    }
                    if (publisherToInclude.isNotEmpty()) {
                        url.addQueryParameter("publisher", publisherToInclude.joinToString(","))
                    }
                }
                is AuthorGroup -> {
                    val authorToInclude = filter.state.filter { it.state }.map { it.author }

                    authorToInclude.forEach {
                        url.addQueryParameter("author", "${it.name},${it.role}")
                    }
                }
                is Filter.Sort -> {
                    val state = filter.state ?: return@forEach

                    val sortCriteria = when (state.index) {
                        0 -> if (type == "series") "metadata.titleSort" else "name"
                        1 -> "createdDate"
                        2 -> "lastModifiedDate"
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
        processSeriesPage(response)

    override fun getMangaUrl(manga: SManga) = manga.url.replace("/api/v1", "")

    override fun mangaDetailsRequest(manga: SManga) = GET(manga.url)

    override fun mangaDetailsParse(response: Response): SManga {
        return if (response.fromReadList()) {
            response.parseAs<ReadListDto>().toSManga()
        } else {
            response.parseAs<SeriesDto>().toSManga()
        }
    }

    private val chapterNameTemplate by lazy {
        preferences.getString(PREF_CHAPTER_NAME_TEMPLATE, PREF_CHAPTER_NAME_TEMPLATE_DEFAULT)!!
    }

    override fun getChapterUrl(chapter: SChapter) = chapter.url.replace("/api/v1/books", "/book")

    override fun chapterListRequest(manga: SManga): Request =
        GET("${manga.url}/books?unpaged=true&media_status=READY&deleted=false", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val page = response.parseAs<PageWrapperDto<BookDto>>().content

        val r = page.mapIndexed { index, book ->
            SChapter.create().apply {
                chapter_number = if (!response.fromReadList()) book.metadata.numberSort else index + 1F
                url = "$baseUrl/api/v1/books/${book.id}"
                scanlator = book.metadata.authors.groupBy({ it.role }, { it.name })["translator"]?.joinToString()
                date_upload = book.metadata.releaseDate?.let { parseDate(it) }
                    ?: parseDateTime(book.fileLastModified)

                val values = hashMapOf(
                    "title" to book.metadata.title,
                    "seriesTitle" to book.seriesTitle,
                    "number" to book.metadata.number,
                    "createdDate" to book.created,
                    "releaseDate" to book.metadata.releaseDate,
                    "size" to book.size,
                    "sizeBytes" to book.sizeBytes.toString(),
                )
                val sub = StringSubstitutor(values, "{", "}")

                name = (if (!response.fromReadList()) "" else "${book.seriesTitle} ") + sub.replace(chapterNameTemplate)
            }
        }
        return r.sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET("${chapter.url}/pages")

    override fun pageListParse(response: Response): List<Page> {
        val pages = response.parseAs<List<PageDto>>()

        return pages.map {
            val url = "${response.request.url}/${it.number}" +
                if (!supportedImageTypes.contains(it.mediaType)) {
                    "?convert=png"
                } else {
                    ""
                }
            Page(
                index = it.number,
                imageUrl = url,
            )
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        val filters = try {
            mutableListOf<Filter<*>>(
                UnreadFilter(),
                InProgressFilter(),
                ReadFilter(),
                TypeSelect(),
                CollectionSelect(listOf(CollectionFilterEntry("None")) + collections.map { CollectionFilterEntry(it.name, it.id) }),
                LibraryGroup(libraries.map { LibraryFilter(it.id, it.name) }.sortedBy { it.name.lowercase(Locale.ROOT) }),
                StatusGroup(listOf("Ongoing", "Ended", "Abandoned", "Hiatus").map { StatusFilter(it) }),
                GenreGroup(genres.map { GenreFilter(it) }),
                TagGroup(tags.map { TagFilter(it) }),
                PublisherGroup(publishers.map { PublisherFilter(it) }),
            ).also { list ->
                if (collections.isEmpty() && libraries.isEmpty() && genres.isEmpty() && tags.isEmpty() && publishers.isEmpty()) {
                    list.add(0, Filter.Header("Press 'Reset' to show filtering options"))
                    list.add(1, Filter.Separator())
                }

                list.addAll(authors.map { (role, authors) -> AuthorGroup(role, authors.map { AuthorFilter(it) }) })
                list.add(SeriesSort())
            }
        } catch (e: Exception) {
            Log.e(logTag, "error while creating filter list", e)
            emptyList()
        }

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        if (suffix.isBlank()) {
            ListPreference(screen.context).apply {
                key = PREF_EXTRA_SOURCES_COUNT
                title = "Number of extra sources"
                summary = "Number of additional sources to create. There will always be at least one Komga source."
                entries = PREF_EXTRA_SOURCES_ENTRIES
                entryValues = PREF_EXTRA_SOURCES_ENTRIES

                setDefaultValue(PREF_EXTRA_SOURCES_DEFAULT)
                setOnPreferenceChangeListener { _, newValue ->
                    try {
                        val setting = preferences.edit().putString(PREF_EXTRA_SOURCES_COUNT, newValue as String).commit()
                        Toast.makeText(screen.context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                        setting
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
            }.also(screen::addPreference)
        }

        screen.addEditTextPreference(
            title = "Source display name",
            default = suffix,
            summary = displayName.ifBlank { "Here you can change the source displayed suffix" },
            key = PREF_DISPLAY_NAME,
        )
        screen.addEditTextPreference(
            title = "Address",
            default = "",
            summary = baseUrl.ifBlank { "The server address" },
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            validate = { it.toHttpUrlOrNull() != null },
            validationMessage = "The URL is invalid or malformed",
            key = PREF_ADDRESS,
        )
        screen.addEditTextPreference(
            title = "Username",
            default = "",
            summary = username.ifBlank { "The user account email" },
            key = PREF_USERNAME,
        )
        screen.addEditTextPreference(
            title = "Password",
            default = "",
            summary = if (password.isBlank()) "The user account password" else "*".repeat(password.length),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            key = PREF_PASSWORD,
        )

        EditTextPreference(screen.context).apply {
            key = PREF_CHAPTER_NAME_TEMPLATE
            title = "Chapter title format"
            summary = "Customize how chapter names appear. Chapters in read lists will always be prefixed by the series' name."
            dialogMessage = """
            |Supported placeholders:
            |- {title}: Chapter name
            |- {seriesTitle}: Series name
            |- {number}: Chapter number
            |- {createdDate}: Chapter creation date
            |- {releaseDate}: Chapter release date
            |- {size}: Chapter file size (formatted)
            |- {sizeBytes}: Chapter file size (in bytes)
            """.trimMargin()

            setDefaultValue(PREF_CHAPTER_NAME_TEMPLATE_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(PREF_CHAPTER_NAME_TEMPLATE, newValue as String).commit()
                    Toast.makeText(screen.context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }.also(screen::addPreference)
    }

    private var libraries = emptyList<LibraryDto>()
    private var collections = emptyList<CollectionDto>()
    private var genres = emptySet<String>()
    private var tags = emptySet<String>()
    private var publishers = emptySet<String>()
    private var authors = emptyMap<String, List<AuthorDto>>() // roles to list of authors

    private class TypeSelect : Filter.Select<String>("Search for", arrayOf(TYPE_SERIES, TYPE_READLISTS))
    private class LibraryFilter(val id: String, name: String) : Filter.CheckBox(name, false)
    private class LibraryGroup(libraries: List<LibraryFilter>) : Filter.Group<LibraryFilter>("Libraries", libraries)
    private class CollectionSelect(collections: List<CollectionFilterEntry>) : Filter.Select<CollectionFilterEntry>("Collection", collections.toTypedArray())
    private class SeriesSort(selection: Selection? = null) : Filter.Sort("Sort", arrayOf("Alphabetically", "Date added", "Date updated"), selection ?: Selection(0, true))
    private class StatusFilter(name: String) : Filter.CheckBox(name, false)
    private class StatusGroup(filters: List<StatusFilter>) : Filter.Group<StatusFilter>("Status", filters)
    private class UnreadFilter : Filter.CheckBox("Unread", false)
    private class InProgressFilter : Filter.CheckBox("In Progress", false)
    private class ReadFilter : Filter.CheckBox("Read", false)
    private class GenreFilter(genre: String) : Filter.CheckBox(genre, false)
    private class GenreGroup(genres: List<GenreFilter>) : Filter.Group<GenreFilter>("Genres", genres)
    private class TagFilter(tag: String) : Filter.CheckBox(tag, false)
    private class TagGroup(tags: List<TagFilter>) : Filter.Group<TagFilter>("Tags", tags)
    private class PublisherFilter(publisher: String) : Filter.CheckBox(publisher, false)
    private class PublisherGroup(publishers: List<PublisherFilter>) : Filter.Group<PublisherFilter>("Publishers", publishers)
    private class AuthorFilter(val author: AuthorDto) : Filter.CheckBox(author.name, false)
    private class AuthorGroup(role: String, authors: List<AuthorFilter>) : Filter.Group<AuthorFilter>(role.replaceFirstChar { it.titlecase() }, authors)

    private data class CollectionFilterEntry(
        val name: String,
        val id: String? = null,
    ) {
        override fun toString() = name
    }

    private fun PreferenceScreen.addEditTextPreference(
        title: String,
        default: String,
        summary: String,
        inputType: Int? = null,
        validate: ((String) -> Boolean)? = null,
        validationMessage: String? = null,
        key: String = title,
    ) {
        val preference = EditTextPreference(context).apply {
            this.key = key
            this.title = title
            this.summary = summary
            this.setDefaultValue(default)
            dialogTitle = title

            setOnBindEditTextListener { editText ->
                if (inputType != null) {
                    editText.inputType = inputType
                }

                if (validate != null) {
                    editText.addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                            override fun afterTextChanged(editable: Editable?) {
                                requireNotNull(editable)

                                val text = editable.toString()

                                val isValid = text.isBlank() || validate(text)

                                editText.error = if (!isValid) validationMessage else null
                                editText.rootView.findViewById<Button>(android.R.id.button1)
                                    ?.isEnabled = editText.error == null
                            }
                        },
                    )
                }
            }

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(this.key, newValue as String).commit()
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        addPreference(preference)
    }

    private var fetchFiltersFailed = false

    private var fetchFiltersAttempts = 0

    private fun fetchFilterOptions() {
        if (baseUrl.isBlank()) {
            return
        }

        if (fetchFiltersAttempts > 3 || (fetchFiltersAttempts > 0 && !fetchFiltersFailed)) {
            return
        }

        Single.fromCallable {
            val result = runCatching {
                libraries = client.newCall(GET("$baseUrl/api/v1/libraries")).execute().parseAs()
                collections = client
                    .newCall(GET("$baseUrl/api/v1/collections?unpaged=true"))
                    .execute()
                    .parseAs<PageWrapperDto<CollectionDto>>()
                    .content
                genres = client.newCall(GET("$baseUrl/api/v1/genres")).execute().parseAs()
                tags = client.newCall(GET("$baseUrl/api/v1/tags")).execute().parseAs()
                publishers = client.newCall(GET("$baseUrl/api/v1/publishers")).execute().parseAs()
                authors = client
                    .newCall(GET("$baseUrl/api/v1/authors"))
                    .execute()
                    .parseAs<List<AuthorDto>>()
                    .groupBy { it.role }
            }
                .onFailure {
                    Log.e(logTag, "Could not fetch filtering options", it)
                }

            fetchFiltersFailed = result.isFailure
            fetchFiltersAttempts++
        }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe()
    }

    private fun processSeriesPage(response: Response): MangasPage {
        return if (response.fromReadList()) {
            val data = response.parseAs<PageWrapperDto<ReadListDto>>()

            MangasPage(data.content.map { it.toSManga() }, !data.last)
        } else {
            val data = response.parseAs<PageWrapperDto<SeriesDto>>()

            MangasPage(data.content.map { it.toSManga() }, !data.last)
        }
    }

    private fun SeriesDto.toSManga(): SManga =
        SManga.create().apply {
            title = metadata.title
            url = "$baseUrl/api/v1/series/$id"
            thumbnail_url = "$url/thumbnail"
            status = when {
                metadata.status == "ENDED" && metadata.totalBookCount != null && booksCount < metadata.totalBookCount -> SManga.PUBLISHING_FINISHED
                metadata.status == "ENDED" -> SManga.COMPLETED
                metadata.status == "ONGOING" -> SManga.ONGOING
                metadata.status == "ABANDONED" -> SManga.CANCELLED
                metadata.status == "HIATUS" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            genre = (metadata.genres + metadata.tags + booksMetadata.tags).distinct().joinToString(", ")
            description = metadata.summary.ifBlank { booksMetadata.summary }
            booksMetadata.authors.groupBy { it.role }.let { map ->
                author = map["writer"]?.map { it.name }?.distinct()?.joinToString()
                artist = map["penciller"]?.map { it.name }?.distinct()?.joinToString()
            }
        }

    private fun ReadListDto.toSManga(): SManga =
        SManga.create().apply {
            title = name
            description = summary
            url = "$baseUrl/api/v1/readlists/$id"
            thumbnail_url = "$url/thumbnail"
            status = SManga.UNKNOWN
        }

    private fun Response.fromReadList() = request.url.toString().contains("/api/v1/readlists")

    private fun parseDate(date: String?): Long = runCatching {
        KomgaHelper.formatterDate.parse(date!!)!!.time
    }.getOrDefault(0L)

    private fun parseDateTime(date: String?) = if (date == null) {
        0L
    } else {
        runCatching {
            KomgaHelper.formatterDateTime.parse(date)!!.time
        }
            .getOrElse {
                KomgaHelper.formatterDateTimeMilli.parse(date)?.time ?: 0L
            }
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    private val logTag = "komga${if (suffix.isNotBlank()) ".$suffix" else ""}"

    companion object {
        internal const val PREF_EXTRA_SOURCES_COUNT = "Number of extra sources"
        internal const val PREF_EXTRA_SOURCES_DEFAULT = "2"
        private val PREF_EXTRA_SOURCES_ENTRIES = (0..10).map { it.toString() }.toTypedArray()

        private const val PREF_DISPLAY_NAME = "Source display name"
        private const val PREF_ADDRESS = "Address"
        private const val PREF_USERNAME = "Username"
        private const val PREF_PASSWORD = "Password"
        private const val PREF_CHAPTER_NAME_TEMPLATE = "Chapter name template"
        private const val PREF_CHAPTER_NAME_TEMPLATE_DEFAULT = "{number} - {title} ({size})"

        private val supportedImageTypes = listOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/jxl", "image/heif", "image/avif")

        private const val TYPE_SERIES = "Series"
        private const val TYPE_READLISTS = "Read lists"
    }
}
