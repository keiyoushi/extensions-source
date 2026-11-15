package eu.kanade.tachiyomi.extension.en.lazymonkiscans

import android.util.Base64
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

@Source
abstract class LazyMonkiScans : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::storedImageInterceptor)

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        fetchMangas().sortedByDescending { it.views }.map { it.toSManga(it.coverUrl()) },
        false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = coroutineScope {
        val chapters = async { fetchDocuments("chapters", listOf("mangaId", "createdAt")) }
        val mangas = fetchMangas()
        val lastUpdate = chapters.await().groupBy { it.text("mangaId") }.mapValues { (_, list) -> list.maxOf { it.createdAt } }
        MangasPage(
            mangas.sortedByDescending { lastUpdate[it.id] ?: it.createdAt }.map { it.toSManga(it.coverUrl()) },
            false,
        )
    }

    // The site loads every series at once and filters them itself
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val mangas = fetchMangas()
            .filter { it.text("title")!!.contains(query.trim(), ignoreCase = true) }
            .filter { manga -> genre == null || manga.list("genres").any { it.text == genre } }
            .sortedByDescending { it.views }
        return MangasPage(mangas.map { it.toSManga(it.coverUrl()) }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.queryParameter("manga") ?: return null
        val manga = SManga.create().apply { this.url = "/?view=manga&manga=$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val id = getMangaUrl(manga).toHttpUrl().queryParameter("manga")!!
        val details = if (fetchDetails) {
            async {
                val document = client.get(documentUrl("mangas/$id"), Fetch.XHR).parseAs<DocumentDto>()
                document.toSMangaDetails(document.coverUrl())
            }
        } else {
            null
        }
        val chapterList = if (fetchChapters) {
            async {
                fetchDocuments("chapters", CHAPTER_FIELDS)
                    .filter { it.text("mangaId") == id }
                    .map { it.toSChapter(id) }
                    .sortedByDescending { it.chapter_number }
            }
        } else {
            null
        }
        SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = getChapterUrl(chapter).toHttpUrl().queryParameter("chapter")!!
        val document = client.get(documentUrl("chapters/$id", listOf("pages", "source", "externalId")), Fetch.XHR).parseAs<DocumentDto>()
        val imageUrls = if (document.text("source") == "mangadex") {
            // Chapters taken from MangaDex keep only its id: the site asks MangaDex@Home for the pages
            client.get("$MANGADEX_API/at-home/server/${document.text("externalId")}", mangaDexHeaders, Fetch.XHR).parseAs<AtHomeDto>().imageUrls
        } else {
            document.list("pages").mapNotNull { it.text ?: it.mapValue?.fields?.get("imageUrl")?.text }.map { page ->
                if (page.startsWith(STORED_IMAGE_PREFIX)) {
                    documentUrl("images/${page.removePrefix(STORED_IMAGE_PREFIX)}", listOf("data")).toString()
                } else {
                    page
                }
            }
        }
        return imageUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // MangaDex's API challenges browser agents, so it gets the agent its own extension sends
    private val mangaDexHeaders: Headers
        get() = headersBuilder().set("User-Agent", "Tachiyomi " + System.getProperty("http.agent")).build()

    private suspend fun fetchMangas(): List<DocumentDto> = fetchDocuments("mangas", MANGA_LIST_FIELDS)

    private suspend fun fetchDocuments(collection: String, fields: List<String>): List<DocumentDto> {
        val documents = mutableListOf<DocumentDto>()
        var pageToken: String? = null
        do {
            val url = documentUrl(collection, fields).newBuilder()
                .addQueryParameter("pageSize", "300")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = client.get(url, Fetch.XHR).parseAs<ListDto>()
            documents += page.documents
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return documents
    }

    private fun documentUrl(path: String, fields: List<String> = emptyList()): HttpUrl = "$DATA_URL/$path".toHttpUrl().newBuilder()
        .addQueryParameter("key", API_KEY)
        .apply { fields.forEach { addQueryParameter("mask.fieldPaths", it) } }
        .build()

    // Covers are links or images stored in the document itself
    private fun DocumentDto.coverUrl(): String? {
        val cover = text("coverUrl") ?: return null
        return if (cover.startsWith("data:")) documentUrl("mangas/$id", listOf("coverUrl")).toString() else cover
    }

    // Images stored in Firestore come as a data: URI inside a document
    private fun storedImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val field = request.url.queryParameter("mask.fieldPaths")
        if (request.url.host != FIRESTORE_HOST || field !in STORED_IMAGE_FIELDS) return chain.proceed(request)
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response
        val dataUri = response.parseAs<DocumentDto>().text(field!!)!!
        val mediaType = dataUri.substringAfter("data:").substringBefore(";").toMediaType()
        val bytes = Base64.decode(dataUri.substringAfter("base64,"), Base64.DEFAULT)
        return response.newBuilder()
            .header("Content-Type", mediaType.toString())
            .removeHeader("Content-Length")
            .body(bytes.toResponseBody(mediaType))
            .build()
    }

    companion object {
        private const val FIRESTORE_HOST = "firestore.googleapis.com"
        private const val DATA_URL = "https://$FIRESTORE_HOST/v1/projects/lazymonkiscan/databases/(default)/documents/artifacts/lazymonki-public/public/data"

        // The site's public Firebase key
        private const val API_KEY = "AIzaSyCf2juYah43MfbuEtGndbh08zzC2tN4m8E"

        private const val MANGADEX_API = "https://api.mangadex.org"

        private const val STORED_IMAGE_PREFIX = "firestore:img:"

        private val STORED_IMAGE_FIELDS = setOf("data", "coverUrl")

        private val MANGA_LIST_FIELDS = listOf("title", "coverUrl", "genres", "views", "createdAt")

        private val CHAPTER_FIELDS = listOf("mangaId", "number", "chapterNumber", "title", "type", "createdAt")
    }
}
