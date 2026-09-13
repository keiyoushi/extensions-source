package eu.kanade.tachiyomi.extension.ar.mangacloud

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class MangaCloud : KeiSource() {

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = FIRESTORE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", PAGE_SIZE.toString())
            .addQueryParameter("key", API_KEY)
            .apply {
                val token = lastPageToken
                if (token != null) addQueryParameter("pageToken", token)
            }
            .build()
        val response = client.get(url)
        val json = FirestoreParser.parseList(response)
        lastPageToken = json.nextPageToken
        return MangasPage(json.mangas.map { it.smanga }, json.nextPageToken != null)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = FIRESTORE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", PAGE_SIZE.toString())
            .addQueryParameter("key", API_KEY)
            .addQueryParameter("orderBy", "updatedAt desc")
            .build()
        val response = client.get(url)
        val json = FirestoreParser.parseList(response)
        return MangasPage(json.mangas.map { it.smanga }, false)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected.orEmpty()
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty()
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected.orEmpty()

        val url = FIRESTORE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", "300")
            .addQueryParameter("key", API_KEY)
            .apply {
                when (sort) {
                    "new" -> addQueryParameter("orderBy", "createdAt desc")
                    "updated" -> addQueryParameter("orderBy", "updatedAt desc")
                }
            }
            .build()

        val response = client.get(url)
        val json = FirestoreParser.parseList(response)
        val allMangas = json.mangas.toMutableList()

        var nextPageToken = json.nextPageToken
        while (nextPageToken != null) {
            val nextUrl = FIRESTORE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("pageSize", "300")
                .addQueryParameter("key", API_KEY)
                .addQueryParameter("pageToken", nextPageToken)
                .build()
            val nextResponse = client.get(nextUrl)
            val nextJson = FirestoreParser.parseList(nextResponse)
            allMangas.addAll(nextJson.mangas)
            nextPageToken = nextJson.nextPageToken
        }

        val filtered = allMangas.filter { data ->
            val matchesQuery = if (query.isNotBlank()) {
                val q = query.lowercase()
                data.smanga.title.lowercase().contains(q) ||
                    data.altTitles.any { it.lowercase().contains(q) } ||
                    data.genres.any { it.lowercase().contains(q) } ||
                    data.smanga.description?.lowercase()?.contains(q) == true
            } else {
                true
            }
            val matchesGenre = if (genre.isNotBlank()) {
                data.genres.any { it.lowercase().contains(genre.lowercase()) }
            } else {
                true
            }
            val matchesStatus = if (status.isNotBlank()) {
                data.statusString == status
            } else {
                true
            }
            matchesQuery && matchesGenre && matchesStatus
        }
        return MangasPage(filtered.map { it.smanga }, false)
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val mangaId = url.pathSegments.lastOrNull() ?: return null
        val firestoreUrl = "$FIRESTORE_URL/$mangaId?key=$API_KEY".toHttpUrl()
        val response = client.get(firestoreUrl)
        return FirestoreParser.parseDetails(response)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDetailDeferred = if (fetchDetails) {
            async {
                val url = "$FIRESTORE_URL/${manga.url}?key=$API_KEY".toHttpUrl()
                val response = client.get(url)
                FirestoreParser.parseDetails(response)
            }
        } else {
            null
        }

        val chapterListDeferred = if (fetchChapters) {
            async { fetchAllChapters(manga.url) }
        } else {
            null
        }

        val mangaDetail = mangaDetailDeferred?.await() ?: manga
        val chapterList = chapterListDeferred?.await() ?: chapters

        SMangaUpdate(mangaDetail, chapterList)
    }

    private suspend fun fetchAllChapters(mangaId: String): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        var nextPageToken: String? = null

        do {
            val urlBuilder = "$FIRESTORE_URL/$mangaId/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("key", API_KEY)
                .addQueryParameter("orderBy", "index")
            if (nextPageToken != null) {
                urlBuilder.addQueryParameter("pageToken", nextPageToken)
            }
            val response = client.get(urlBuilder.build())
            val result = response.parseAs<FirestoreListResponse>()
            val chapters = result.documents?.mapNotNull { doc ->
                val docName = doc.name ?: return@mapNotNull null
                val chapterNum = docName.substringAfterLast("/")
                val num = chapterNum.toIntOrNull() ?: 0
                SChapter.create().apply {
                    url = docName.substringAfterLast("cloudmangas/")
                    name = "الفصل $num"
                    chapter_number = num.toFloat()
                }
            } ?: emptyList()
            allChapters.addAll(chapters)
            nextPageToken = result.nextPageToken
        } while (nextPageToken != null)

        allChapters.sortByDescending {
            it.chapter_number?.toString()?.toFloatOrNull() ?: 0f
        }

        return allChapters
    }

    // ============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "${FIRESTORE_URL}/${chapter.url}?key=$API_KEY".toHttpUrl()
        val response = client.get(url)
        return FirestoreParser.parsePages(response)
    }

    // ============================== URLs ================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.split("/")
        if (parts.size >= 2) {
            return "$baseUrl/manga/${parts[0]}/${parts[1]}"
        }
        return "$baseUrl/manga/${chapter.url}"
    }

    // ============================== Filters =============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter("ترتيب حسب", sortingList),
        StatusFilter("الحالة", statusList),
        GenreFilter("التصنيف", genreList),
    )

    // ============================== State ===============================

    private var lastPageToken: String? = null

    companion object {
        private const val API_KEY = "AIzaSyAvdmgz_r_d89Eo8JBs9vAjUAJR451bMYU"
        private const val FIRESTORE_URL = "https://firestore.googleapis.com/v1/projects/ninja-brew-crew-4d79c/databases/(default)/documents/cloudmangas"
        private const val PAGE_SIZE = 20
    }
}
