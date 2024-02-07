package eu.kanade.tachiyomi.extension.all.komga

import android.app.Application
import android.content.SharedPreferences
import android.text.InputType
import android.util.Log
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.extension.all.komga.KomgaUtils.addEditTextPreference
import eu.kanade.tachiyomi.extension.all.komga.KomgaUtils.isFromReadList
import eu.kanade.tachiyomi.extension.all.komga.KomgaUtils.parseAs
import eu.kanade.tachiyomi.extension.all.komga.KomgaUtils.toSManga
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
import okhttp3.Credentials
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Single
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

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
        KomgaUtils.processSeriesPage(response, baseUrl)

    override fun latestUpdatesRequest(page: Int): Request =
        searchMangaRequest(
            page,
            "",
            FilterList(SeriesSort(Filter.Sort.Selection(3, false))),
        )

    override fun latestUpdatesParse(response: Response): MangasPage =
        KomgaUtils.processSeriesPage(response, baseUrl)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        runCatching { fetchFilterOptions() }

        val collectionId = (filters.find { it is CollectionSelect } as? CollectionSelect)?.let {
            it.collections[it.state].id
        }

        val type = when {
            collectionId != null -> "collections/$collectionId/series"
            filters.find { it is TypeSelect }?.state == 1 -> "readlists"
            else -> "series"
        }

        val url = "$baseUrl/api/v1/$type?search=$query&page=${page - 1}&deleted=false".toHttpUrl().newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is UriFilter -> filter.addToUri(url)
                is Filter.Sort -> {
                    val state = filter.state ?: return@forEach

                    val sortCriteria = when (state.index) {
                        1 -> if (type == "series") "metadata.titleSort" else "name"
                        2 -> "createdDate"
                        3 -> "lastModifiedDate"
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
        KomgaUtils.processSeriesPage(response, baseUrl)

    override fun getMangaUrl(manga: SManga) = manga.url.replace("/api/v1", "")

    override fun mangaDetailsRequest(manga: SManga) = GET(manga.url)

    override fun mangaDetailsParse(response: Response): SManga {
        return if (response.isFromReadList()) {
            response.parseAs<ReadListDto>().toSManga(baseUrl)
        } else {
            response.parseAs<SeriesDto>().toSManga(baseUrl)
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
        val isFromReadList = response.isFromReadList()
        val r = page.mapIndexed { index, book ->
            SChapter.create().apply {
                chapter_number = if (!isFromReadList) book.metadata.numberSort else index + 1F
                url = "$baseUrl/api/v1/books/${book.id}"
                name = KomgaUtils.formatChapterName(book, chapterNameTemplate, isFromReadList)
                scanlator = book.metadata.authors.filter { it.role == "translator" }.joinToString { it.name }
                date_upload = book.metadata.releaseDate?.let { KomgaUtils.parseDate(it) }
                    ?: KomgaUtils.parseDateTime(book.fileLastModified)
            }
        }

        return r.sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter) = GET("${chapter.url}/pages")

    override fun pageListParse(response: Response): List<Page> {
        val pages = response.parseAs<List<PageDto>>()

        return pages.map {
            val url = "${response.request.url}/${it.number}" +
                if (!supportedImageTypes.contains(it.mediaType)) {
                    "?convert=png"
                } else {
                    ""
                }

            Page(it.number, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
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
            UriMultiSelectFilter(
                "Libraries",
                "library_id",
                libraries.map { UriMultiSelectOption(it.name, it.id) },
            ),
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
            if (collections.isEmpty() && libraries.isEmpty() && genres.isEmpty() && tags.isEmpty() && publishers.isEmpty()) {
                add(0, Filter.Header("Press 'Reset' to show filtering options"))
                add(1, Filter.Separator())
            }

            addAll(authors.map { (role, authors) -> AuthorGroup(role, authors.map { AuthorFilter(it) }) })
            add(SeriesSort())
        }

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
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
                Toast.makeText(screen.context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    private var libraries = emptyList<LibraryDto>()
    private var collections = emptyList<CollectionDto>()
    private var genres = emptySet<String>()
    private var tags = emptySet<String>()
    private var publishers = emptySet<String>()
    private var authors = emptyMap<String, List<AuthorDto>>() // roles to list of authors

    private var fetchFiltersFailed = false

    private var fetchFiltersAttempts = 0

    private val fetchFiltersLock = ReentrantReadWriteLock()

    private fun fetchFilterOptions() {
        if (baseUrl.isBlank()) {
            return
        }

        Single.fromCallable {
            fetchFiltersLock.read {
                if (fetchFiltersAttempts > 3 || (fetchFiltersAttempts > 0 && !fetchFiltersFailed)) {
                    return@fromCallable
                }
            }

            fetchFiltersLock.write {
                fetchFiltersFailed = try {
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
                    false
                } catch (e: Exception) {
                    Log.e(logTag, "Could not fetch filter options", e)
                    true
                }

                fetchFiltersAttempts++
            }
        }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.io())
            .subscribe()
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

        internal const val TYPE_SERIES = "Series"
        internal const val TYPE_READLISTS = "Read lists"
    }
}
