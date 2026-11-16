package eu.kanade.tachiyomi.extension.all.jellyfin

import android.os.Build
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.AppInfo
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
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import keiyoushi.zip.zipDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration

@Source
abstract class Jellyfin :
    KeiSource(),
    ConfigurableSource,
    UnmeteredSource {

    override val supportsFilterFetching = true

    private val preferences by getPreferencesLazy()

    // The server's own address, which can include the path a reverse proxy serves it under
    override val baseUrl get() = preferences.getString(PREF_ADDRESS, "")!!.trimEnd('/')

    private val username get() = preferences.getString(PREF_USERNAME, "")!!

    private val password get() = preferences.getString(PREF_PASSWORD, "")!!

    // The server lists its sessions and devices by this id
    private val deviceId: String
        get() = preferences.getString(PREF_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(PREF_DEVICE_ID, it).apply()
        }

    // The server sends camelCase JSON when asked for it
    private val apiHeaders get() = headers.newBuilder().set("Accept", "application/json; profile=\"CamelCase\"").build()

    private val pageReader by lazy { PageReader(File(applicationContext.cacheDir, "jellyfin_$id"), ::download) }

    // A whole book can take longer than the app's call timeout on a slow connection
    private val downloadClient by lazy { client.newBuilder().callTimeout(Duration.ZERO).build() }

    private fun download(id: String, target: File) {
        val request = Request.Builder().url(serverUrl("Items/$id/File")).headers(headers).cacheControl(NO_STORE).fetch(Fetch.XHR).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP error ${response.code}")
            target.outputStream().use { response.body.byteStream().copyTo(it) }
        }
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { pageReader.intercept(it) }
        .addInterceptor(::folderCoverIntercept)
        .addInterceptor(::authIntercept)
        .dns(Dns.SYSTEM) // DNS over HTTPS can't resolve LAN hosts

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != baseUrl.toHttpUrlOrNull()?.host) return chain.proceed(request)

        val token = preferences.getString(PREF_TOKEN, null) ?: logIn(chain, null)
        val response = chain.proceed(request.withAuthorization(token))
        if (response.code != 401) return response

        response.close()
        return chain.proceed(request.withAuthorization(logIn(chain, token)))
    }

    // Folders without an image of their own show their first book's cover
    private fun folderCoverIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.fragment != FOLDER_COVER) return chain.proceed(request)

        val folderId = request.url.pathSegments.let { it[it.size - 3] } // .../Items/{id}/Images/Primary
        val bookRequest = Request.Builder()
            .url(
                serverUrl("Items") {
                    addQueryParameter("ParentId", folderId)
                    addQueryParameter("Recursive", "true")
                    addQueryParameter("IncludeItemTypes", "Book")
                    addQueryParameter("ImageTypes", "Primary")
                    addQueryParameter("SortBy", BOOK_ORDER)
                    addQueryParameter("Limit", "1")
                },
            )
            .headers(apiHeaders)
            .fetch(Fetch.XHR)
            .build()
        val cover = chain.proceed(bookRequest).parseAs<ItemsDto>().items.firstOrNull()?.cover(baseUrl)
            ?: request.url.newBuilder().fragment(null).toString()
        return chain.proceed(request.newBuilder().url(cover).build())
    }

    private fun Request.withAuthorization(token: String?) = newBuilder().header("Authorization", authorization(token)).build()

    private fun authorization(token: String?) = buildString {
        append("MediaBrowser Client=\"Tachiyomi\", Device=\"${Build.MODEL.replace("\"", "")}\", DeviceId=\"$deviceId\", Version=\"${AppInfo.getVersionName()}\"")
        if (token != null) append(", Token=\"$token\"")
    }

    @Synchronized
    private fun logIn(chain: Interceptor.Chain, staleToken: String?): String {
        preferences.getString(PREF_TOKEN, null)?.takeIf { it != staleToken }?.let { return it }
        if (username.isBlank()) throw IOException("Set your server address, username and password in the extension settings")

        val request = Request.Builder()
            .url(serverUrl("Users/AuthenticateByName"))
            .headers(apiHeaders)
            .header("Authorization", authorization(null))
            .fetch(Fetch.XHR)
            .post(LoginDto(username, password).toJsonRequestBody())
            .build()
        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Jellyfin login failed, check your username and password")
        }
        val token = response.parseAs<AuthResultDto>().accessToken
        preferences.edit().putString(PREF_TOKEN, token).apply()
        return token
    }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    // The books added last, by their entry
    override suspend fun getLatestUpdates(page: Int) = getItemsPage(page, null, byBooks = true) {
        addQueryParameter("Recursive", "true")
        addQueryParameter("IncludeItemTypes", "Book")
        addQueryParameter("SortBy", "DateCreated,SortName")
        addQueryParameter("SortOrder", "Descending")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value
        val groups = filters.filterIsInstance<OptionGroup>().filter { it.selected.isNotEmpty() }
        val collectionId = filters.firstInstanceOrNull<CollectionFilter>()?.selectedId
        // Metadata is mostly on books, so a search, a filter or a sort on it finds books and folders and lists their entries
        val byBooks = query.isNotBlank() || status != null || groups.isNotEmpty() || sort.byBooks || collectionId != null
        val parentId = collectionId ?: filters.firstInstanceOrNull<LibraryFilter>()?.selectedId

        return getItemsPage(page, parentId, byBooks) {
            addQueryParameter("SortBy", listOf(sort.sortBy, "SortName").distinct().joinToString(","))
            addQueryParameter("SortOrder", sort.sortOrder)
            if (byBooks) {
                addQueryParameter("Recursive", "true")
                addQueryParameter("IncludeItemTypes", "Folder,Book")
            }
            if (query.isNotBlank()) addQueryParameter("SearchTerm", query.trim())
            status?.let { addQueryParameter("Filters", it) }
            groups.forEach { addQueryParameter(it.parameter, it.selected.joinToString(it.separator)) }
        }
    }

    // Each library's page, side by side: an entry is a folder or a book at the top of a library, as in the site's folder view
    private suspend fun getItemsPage(page: Int, parentId: String?, byBooks: Boolean, query: HttpUrl.Builder.() -> Unit): MangasPage = coroutineScope {
        // A page of books can all be from one entry, so they come in bigger pages
        val pageSize = if (byBooks) BOOKS_PAGE_SIZE else PAGE_SIZE
        val parentIds = parentId?.let(::listOf) ?: getLibraries().map { it.id }
        val pages = parentIds.map { id ->
            async {
                val url = serverUrl("Items") {
                    addQueryParameter("ParentId", id)
                    addQueryParameter("StartIndex", "${(page - 1) * pageSize}")
                    addQueryParameter("Limit", "$pageSize")
                    addQueryParameter("Fields", "ParentId,Path")
                    addQueryParameter("ExcludeItemTypes", "AudioBook")
                    addQueryParameter("EnableUserData", "false")
                    query()
                }
                client.get(url, apiHeaders, Fetch.XHR).parseAs<ItemsDto>()
            }
        }.awaitAll()

        val items = pages.flatMap { it.items }.filter { !it.isBook || it.isComic }
        val entries = if (byBooks) topLevel(items) else items
        MangasPage(entries.map { it.toSManga(baseUrl) }, pages.any { page * pageSize < it.totalRecordCount })
    }

    private suspend fun getLibraries() = client.get(serverUrl("UserViews"), apiHeaders, Fetch.XHR).parseAs<ItemsDto>()
        .items.filter { it.collectionType == "books" }

    private fun serverUrl(path: String, query: HttpUrl.Builder.() -> Unit = {}): HttpUrl {
        val server = baseUrl.toHttpUrlOrNull() ?: throw IOException("Set your server address in the extension settings")
        return server.newBuilder().addPathSegments(path).apply(query).build()
    }

    // The entries holding these items: the folder or book at the top of their library, below the library's own folder
    private suspend fun topLevel(items: List<ItemDto>): List<ItemDto> {
        val known = items.associateByTo(mutableMapOf()) { it.id }
        var missing = items.mapNotNull { it.parentId }.filterNot(known::containsKey).distinct()
        while (missing.isNotEmpty()) {
            val parents = getItems(missing)
            parents.associateByTo(known) { it.id }
            missing = parents.mapNotNull { it.parentId }.filterNot(known::containsKey).distinct()
        }

        return items.mapNotNull { item ->
            // Ends at the library's own folder, which is no entry, as are the libraries themselves
            val chain = generateSequence(item) { known[it.parentId] }.takeWhile { it.type !in ROOT_TYPES }.toList()
            chain.getOrNull(chain.size - 2)?.takeIf { it.type == "Folder" || it.isBook }
        }.distinctBy { it.id }
    }

    private suspend fun getItems(ids: List<String>): List<ItemDto> {
        val url = serverUrl("Items") {
            addQueryParameter("Ids", ids.joinToString(","))
            addQueryParameter("Fields", "ParentId,Path")
            addQueryParameter("EnableUserData", "false")
        }
        return client.get(url, apiHeaders, Fetch.XHR).parseAs<ItemsDto>().items
    }

    private suspend fun getItem(id: String) = client.get(serverUrl("Items/$id"), apiHeaders, Fetch.XHR).parseAs<ItemDto>()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrlOrNull()?.host) return null
        val id = url.fragment?.let { ITEM_ID.find(it) }?.groupValues?.get(1) ?: return null
        return topLevel(listOf(getItem(id))).firstOrNull()?.toSManga(baseUrl)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/web/#/details?id=${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/web/#/details?id=${chapter.url}"

    // Details come from the entry and its books, so both are fetched whatever is asked for
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val entry = getItem(manga.url)
        if (entry.isBook) return SMangaUpdate(entry.toSManga(baseUrl, emptyList()), listOfNotNull(entry.takeIf { it.isComic }?.toSChapter(null)))

        val comics = getBooks(entry.id).filter { it.isComic }
        // Books in subfolders are listed after the entry's own, each subfolder's together, named by their path
        val folders = if (comics.any { it.parentId != entry.id }) getFolders(entry.id).associateBy { it.id } else emptyMap()
        fun ItemDto.folderPath() = generateSequence(folders[parentId]) { folders[it.parentId] }.map { it.name }.toList()
            .reversed().joinToString(" / ").ifEmpty { null }
        val chapterList = comics
            .map { it to it.folderPath() }
            .sortedWith(compareBy(NATURAL_ORDER) { it.second.orEmpty() })
            .map { (book, path) -> book.toSChapter(path) }
            .reversed()

        return SMangaUpdate(entry.toSManga(baseUrl, comics), chapterList)
    }

    private suspend fun getBooks(parentId: String): List<ItemDto> {
        val url = serverUrl("Items") {
            addQueryParameter("ParentId", parentId)
            addQueryParameter("Recursive", "true")
            addQueryParameter("IncludeItemTypes", "Book")
            addQueryParameter("SortBy", BOOK_ORDER)
            addQueryParameter(
                "Fields",
                "ParentId,Path,Overview,Taglines,Genres,Tags,Studios,People,OriginalTitle,CommunityRating,OfficialRating,ExternalUrls,DateCreated,PremiereDate,ProductionYear",
            )
            addQueryParameter("EnableUserData", "false")
        }
        return client.get(url, apiHeaders, Fetch.XHR).parseAs<ItemsDto>().items
    }

    private suspend fun getFolders(parentId: String): List<ItemDto> {
        val url = serverUrl("Items") {
            addQueryParameter("ParentId", parentId)
            addQueryParameter("Recursive", "true")
            addQueryParameter("IncludeItemTypes", "Folder")
            addQueryParameter("Fields", "ParentId")
            addQueryParameter("EnableUserData", "false")
        }
        return client.get(url, apiHeaders, Fetch.XHR).parseAs<ItemsDto>().items
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val book = getItem(chapter.url)
        val fileUrl = serverUrl("Items/${book.id}/File").toString()
        return when (book.extension) {
            "cbz", "zip" -> client.zipDirectory(fileUrl).entries
                .filter { it.name.isImage() }
                .sortedWith(compareBy(NATURAL_ORDER) { it.name })
                .mapIndexed { index, entry ->
                    val data = ZipPageDto(entry.name, entry.localHeaderOffset, entry.compressedSize, entry.method, entry.localHeaderAllowance)
                    Page(index, imageUrl = "$fileUrl#$ZIP_PAGE${data.toJsonString()}")
                }
            "pdf" -> List(withContext(Dispatchers.IO) { pageReader.pdfPageCount(book.id) }) { Page(it, imageUrl = "$fileUrl#$PDF_PAGE$it") }
            else -> withContext(Dispatchers.IO) { pageReader.archivePages(book.id) }.mapIndexed { index, name -> Page(index, imageUrl = "$fileUrl#$FILE_PAGE$name") }
        }
    }

    override fun getImageRequest(page: Page): Request = pageReader.withZipRange(super.getImageRequest(page))

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val libraries = getLibraries()
        suspend fun <T> list(path: String, query: HttpUrl.Builder.() -> Unit, parse: (ItemsDto) -> T) = parse(client.get(serverUrl(path, query), apiHeaders, Fetch.XHR).parseAs<ItemsDto>())
        val filters = libraries.map { library ->
            async {
                val url = serverUrl("Items/Filters") {
                    addQueryParameter("IncludeItemTypes", "Folder,Book")
                    addQueryParameter("ParentId", library.id)
                }
                client.get(url, apiHeaders, Fetch.XHR).parseAs<FiltersDto>()
            }
        }
        val people = libraries.map { library ->
            async { list("Persons", { addQueryParameter("ParentId", library.id) }) { page -> page.items.map { NamedId(it.id, it.name) } } }
        }
        val studios = async {
            list("Studios", {
                addQueryParameter("IncludeItemTypes", "Book")
                addQueryParameter("Recursive", "true")
            }) { page -> page.items.map { NamedId(it.id, it.name) } }
        }
        val collections = async {
            list("Items", {
                addQueryParameter("IncludeItemTypes", "BoxSet")
                addQueryParameter("Recursive", "true")
                addQueryParameter("SortBy", "SortName")
            }) { page -> page.items.map { NamedId(it.id, it.name) } }
        }
        val all = filters.awaitAll()
        FilterData(
            libraries = libraries.map { NamedId(it.id, it.name) },
            collections = collections.await(),
            genres = all.flatMap { it.genres }.distinct().sorted(),
            tags = all.flatMap { it.tags }.distinct().sorted(),
            officialRatings = all.flatMap { it.officialRatings }.distinct().sorted(),
            years = all.flatMap { it.years }.distinct().sortedDescending(),
            studios = studios.await(),
            people = people.awaitAll().flatten().distinctBy { it.id }.sortedBy { it.name },
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        return FilterList(
            listOfNotNull<Filter<*>>(
                filterData?.libraries?.takeIf { it.size > 1 }?.let(::LibraryFilter),
                filterData?.collections?.takeIf { it.isNotEmpty() }?.let(::CollectionFilter),
                SortFilter(),
                StatusFilter(),
                filterData?.genres?.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
                filterData?.tags?.takeIf { it.isNotEmpty() }?.let(::TagFilter),
                filterData?.people?.takeIf { it.isNotEmpty() }?.let(::PeopleFilter),
                filterData?.studios?.takeIf { it.isNotEmpty() }?.let(::StudioFilter),
                filterData?.officialRatings?.takeIf { it.isNotEmpty() }?.let(::RatingFilter),
                filterData?.years?.takeIf { it.isNotEmpty() }?.let(::YearFilter),
            ),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addTextPreference(
            PREF_ADDRESS,
            "Server address",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            dialogMessage = "The address you open Jellyfin at, e.g. http://192.168.1.2:8096 or https://example.com/jellyfin",
        ) { it.toHttpUrlOrNull() != null }
        screen.addTextPreference(PREF_USERNAME, "Username", InputType.TYPE_CLASS_TEXT)
        screen.addTextPreference(
            PREF_PASSWORD,
            "Password",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            masked = true,
        )
    }

    private fun PreferenceScreen.addTextPreference(
        key: String,
        title: String,
        inputType: Int,
        masked: Boolean = false,
        dialogMessage: String? = null,
        isValid: (String) -> Boolean = { true },
    ) {
        fun summaryOf(value: String) = when {
            value.isBlank() -> "Not set"
            masked -> "•".repeat(value.length)
            else -> value
        }

        EditTextPreference(context).apply {
            this.key = key
            this.title = title
            dialogTitle = title
            this.dialogMessage = dialogMessage
            summary = summaryOf(preferences.getString(key, "")!!)
            setOnBindEditTextListener { it.inputType = inputType }
            setOnPreferenceChangeListener { _, newValue ->
                val value = newValue as String
                if (value.isNotBlank() && !isValid(value)) return@setOnPreferenceChangeListener false
                summary = summaryOf(value)
                // A new server or account must not be shadowed by the old session
                preferences.edit().remove(PREF_TOKEN).apply()
                true
            }
        }.also(::addPreference)
    }
}

private const val PAGE_SIZE = 50
private const val BOOKS_PAGE_SIZE = 200

// The site's order within a folder: volume, then number, then name
private const val BOOK_ORDER = "ParentIndexNumber,IndexNumber,SortName"

private val ROOT_TYPES = setOf("AggregateFolder", "UserRootFolder", "CollectionFolder")

private val NO_STORE = CacheControl.Builder().noStore().build()

// The item id in the web client's details and folder urls
private val ITEM_ID = Regex("""[?&](?:id|parentId)=([0-9a-f]{32})""")

private const val PREF_ADDRESS = "address"
private const val PREF_USERNAME = "username"
private const val PREF_PASSWORD = "password"
private const val PREF_TOKEN = "access_token"
private const val PREF_DEVICE_ID = "device_id"
