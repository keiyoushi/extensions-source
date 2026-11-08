package eu.kanade.tachiyomi.extension.es.aquellosquetraducen

import android.icu.text.Collator
import android.icu.text.RuleBasedCollator
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.Normalizer
import java.util.Locale

@Source
abstract class AquellosQueTraducen : KeiSource() {

    override val supportsLatest = false

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(fetchWorks().map { it.toSManga() }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val types = filters.firstInstance<TypeFilter>().selected
        val selectedGenres = filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty()
        val statuses = filters.firstInstance<StatusFilter>().selected
        val search = query.trim().foldAccents()
        val works = fetchWorks().filter { work ->
            work.title.foldAccents().contains(search) &&
                (types.isEmpty() || work.type in types) &&
                (selectedGenres.isEmpty() || work.genres.any { genre -> selectedGenres.any { it.equals(genre, ignoreCase = true) } }) &&
                (statuses.isEmpty() || work.status in statuses)
        }
        return MangasPage(works.map { it.toSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = when (url.pathSegments.singleOrNull()) {
            "obra.html" -> url.queryParameter("id")
            "lector.html" -> url.queryParameter("obra")
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return null
        return fetchWork(id).takeUnless { it.isNovel }?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val work = fetchWork(getMangaUrl(manga).toHttpUrl().queryParameter("id")!!)
        return SMangaUpdate(
            work.toSManga(),
            if (fetchChapters) fetchChapterList(work) else chapters,
        )
    }

    private suspend fun fetchChapterList(work: WorkDto): List<SChapter> {
        val folderId = work.folderId ?: return emptyList()
        return listFiles("'$folderId' in parents and mimeType = '$FOLDER_MIME_TYPE' and trashed = false", "files(id,name,createdTime)")
            .sortedWith(compareByDescending(nameOrder) { it.name })
            .map { it.toSChapter(work.id) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val folderId = getChapterUrl(chapter).toHttpUrl().queryParameter("cap")!!
        return listFiles("'$folderId' in parents and mimeType contains 'image/' and trashed = false", "files(id,name)")
            .sortedWith(compareBy(nameOrder) { it.name })
            .mapIndexed { index, file -> Page(index, imageUrl = driveImageUrl(file.id)) }
    }

    override suspend fun fetchFilterData(): JsonElement = fetchWorks()
        .flatMap { it.genres }
        .distinctBy { it.lowercase() }
        .sortedWith(nameOrder)
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            TypeFilter(),
            data?.parseAs<List<String>>()?.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
            StatusFilter(),
        ),
    )

    // The catalogue is a single Firestore collection, ordered like the site's popular list: most liked first
    private suspend fun fetchWorks(): List<WorkDto> = buildList {
        var pageToken: String? = null
        do {
            val url = "$FIRESTORE_URL/obras".toHttpUrl().newBuilder()
                .addQueryParameter("key", FIREBASE_API_KEY)
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = client.get(url, Fetch.XHR).parseAs<WorksDto>()
            addAll(page.documents)
            pageToken = page.nextPageToken
        } while (pageToken != null)
    }.filterNot { it.isNovel }
        .sortedWith(compareByDescending<WorkDto> { it.likes }.thenBy(nameOrder) { it.title })

    private suspend fun fetchWork(id: String): WorkDto {
        val url = "$FIRESTORE_URL/obras".toHttpUrl().newBuilder()
            .addPathSegment(id)
            .addQueryParameter("key", FIREBASE_API_KEY)
            .build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    // Chapters are Drive folders and pages the image files in them, listed with the site's Drive API key
    private suspend fun listFiles(query: String, fields: String): List<FileDto> {
        val url = DRIVE_API_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("fields", fields)
            .addQueryParameter("pageSize", "1000")
            .addQueryParameter("key", DRIVE_API_KEY)
            .build()
        return client.get(url, Fetch.XHR).parseAs<FilesDto>().files
    }

    private fun String.foldAccents() = Normalizer.normalize(this, Normalizer.Form.NFD).replace(diacriticsRegex, "").lowercase()

    companion object {
        private const val FIRESTORE_URL = "https://firestore.googleapis.com/v1/projects/aquellosquetraducen-40706/databases/(default)/documents"
        private const val FIREBASE_API_KEY = "AIzaSyCOL_GtBxev3zWFf2wKHppkCeO60EElMnM"
        private const val DRIVE_API_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_API_KEY = "AIzaSyAJQ2iPO6fJKzBqsfQ81N9pm9Brr4L_rOU"
        private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        private val diacriticsRegex = Regex("""\p{Mn}+""")

        // The site sorts names like the browser's localeCompare with { numeric: true, sensitivity: "base" }
        private val nameOrder: Collator = (Collator.getInstance(Locale.ROOT) as RuleBasedCollator).apply {
            strength = Collator.PRIMARY
            numericCollation = true
        }.freeze()
    }
}
