package eu.kanade.tachiyomi.extension.ar.mangacloud

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject

@Source
abstract class MangaCloud : HttpSource() {

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request {
        val url = FIRESTORE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", PAGE_SIZE.toString())
            .addQueryParameter("key", API_KEY)
            .apply {
                val token = lastPageToken
                if (token != null) addQueryParameter("pageToken", token)
            }
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val json = FirestoreParser.parseList(response)
        lastPageToken = json.nextPageToken
        return MangasPage(json.mangas.map { it.smanga }, json.nextPageToken != null)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = FIRESTORE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", PAGE_SIZE.toString())
            .addQueryParameter("key", API_KEY)
            .addQueryParameter("orderBy", "updatedAt desc")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val json = FirestoreParser.parseList(response)
        return MangasPage(json.mangas.map { it.smanga }, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        currentSearchQuery = query
        currentSortFilter = filters.firstInstanceOrNull<SortFilter>()?.selected.orEmpty()
        currentGenreFilter = filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty()
        currentStatusFilter = filters.firstInstanceOrNull<StatusFilter>()?.selected.orEmpty()
        val url = FIRESTORE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("pageSize", "300")
            .addQueryParameter("key", API_KEY)
            .apply {
                when (currentSortFilter) {
                    "new" -> addQueryParameter("orderBy", "createdAt desc")
                    "updated" -> addQueryParameter("orderBy", "updatedAt desc")
                }
            }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = currentSearchQuery.trim()
        val genre = currentGenreFilter
        val status = currentStatusFilter
        val json = FirestoreParser.parseList(response)
        val filtered = json.mangas.filter { data ->
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

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        val url = "$FIRESTORE_URL/$mangaId?key=$API_KEY".toHttpUrl()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = FirestoreParser.parseDetails(response)

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        val url = "$FIRESTORE_URL/$mangaId/chapters?pageSize=1000&key=$API_KEY&orderBy=index".toHttpUrl()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        var nextPageToken: String? = null
        var currentResponse = response

        do {
            val jsonString = currentResponse.body?.string() ?: break
            val json = JSONObject(jsonString)

            val newResponse = currentResponse.newBuilder()
                .body(jsonString.toResponseBody(null))
                .build()
            val chapters = FirestoreParser.parseChapters(newResponse)
            allChapters.addAll(chapters)

            nextPageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }

            if (nextPageToken != null) {
                val pathSegments = currentResponse.request.url.pathSegments
                val chaptersIndex = pathSegments.indexOf("chapters")
                if (chaptersIndex < 1) break
                val mangaId = pathSegments[chaptersIndex - 1]

                val urlBuilder = "$FIRESTORE_URL/$mangaId/chapters".toHttpUrl().newBuilder()
                    .addQueryParameter("pageSize", "1000")
                    .addQueryParameter("key", API_KEY)
                    .addQueryParameter("orderBy", "index")
                    .addQueryParameter("pageToken", nextPageToken)

                val newRequest = GET(urlBuilder.build(), headers)
                currentResponse = client.newCall(newRequest).execute()
            }
        } while (nextPageToken != null && currentResponse.isSuccessful)

        // ترتيب الفصول تنازلياً (الأحدث أولاً) حسب رقم الفصل
        allChapters.sortByDescending {
            it.chapter_number?.toString()?.toFloatOrNull() ?: 0f
        }

        return allChapters
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "${FIRESTORE_URL}/${chapter.url}?key=$API_KEY".toHttpUrl()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> = FirestoreParser.parsePages(response)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url.substringAfterLast("/")
        return "$baseUrl/manga/$mangaId"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.split("/")
        if (parts.size >= 2) {
            return "$baseUrl/manga/${parts[0]}/${parts[1]}"
        }
        return "$baseUrl/manga/${chapter.url}"
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter("ترتيب حسب", sortingList),
        StatusFilter("الحالة", statusList),
        GenreFilter("التصنيف", genreList),
    )

    private var lastPageToken: String? = null
    private var currentSearchQuery: String = ""
    private var currentSortFilter: String = ""
    private var currentGenreFilter: String = ""
    private var currentStatusFilter: String = ""

    companion object {
        private const val API_KEY = "AIzaSyAvdmgz_r_d89Eo8JBs9vAjUAJR451bMYU"
        private const val FIRESTORE_URL = "https://firestore.googleapis.com/v1/projects/ninja-brew-crew-4d79c/databases/(default)/documents/cloudmangas"
        private const val PAGE_SIZE = 20

        private val sortingList = arrayOf(
            Pair("الأشهر", "popular"),
            Pair("آخر التحديثات", "updated"),
            Pair("إضافات جديدة", "new"),
        )

        private val statusList = arrayOf(
            Pair("الكل", ""),
            Pair("مستمر", "ongoing"),
            Pair("مكتمل", "completed"),
        )

        private val genreList = arrayOf(
            Pair("الكل", ""),
            Pair("أكشن", "أكشن"),
            Pair("إعادة احياء", "إعادة احياء"),
            Pair("إيسيكاي", "إيسيكاي"),
            Pair("الفنون القتالية", "الفنون القتالية"),
            Pair("اتصال بالعالم الخارجي", "اتصال بالعالم الخارجي"),
            Pair("اثاره", "اثاره"),
            Pair("اسلحة خارقة", "اسلحة خارقة"),
            Pair("اسطوره", "اسطوره"),
            Pair("اميرة", "اميرة"),
            Pair("بزنس", "بزنس"),
            Pair("بطل غير اعتيادي", "بطل غير اعتيادي"),
            Pair("تبديل عوالم", "تبديل عوالم"),
            Pair("تجسيد", "تجسيد"),
            Pair("حريم", "حريم"),
            Pair("حياة مدرسية", "حياة مدرسية"),
            Pair("خوارق", "خوارق"),
            Pair("خيال", "خيال"),
            Pair("خيال علمي", "خيال علمي"),
            Pair("دراما", "دراما"),
            Pair("رعب", "رعب"),
            Pair("رومانسي", "رومانسي"),
            Pair("زومبي", "زومبي"),
            Pair("سحر", "سحر"),
            Pair("شريحة من الحياة", "شريحة من الحياة"),
            Pair("شونين", "شونين"),
            Pair("شياطين", "شياطين"),
            Pair("عصابات", "عصابات"),
            Pair("غموض", "غموض"),
            Pair("فتى خارق", "فتى خارق"),
            Pair("فنتازيا", "فنتازيا"),
            Pair("فنون قتاليه", "فنون قتاليه"),
            Pair("قراصنة", "قراصنة"),
            Pair("كوميدي", "كوميدي"),
            Pair("كوميديا", "كوميديا"),
            Pair("مانهوا", "مانهوا"),
            Pair("مانها", "مانها"),
            Pair("مانجا", "مانجا"),
            Pair("مغامرة", "مغامرة"),
            Pair("موسيقى", "موسيقى"),
            Pair("نظام", "نظام"),
            Pair("نفسي", "نفسي"),
            Pair("نهاية العالم", "نهاية العالم"),
            Pair("وحوش", "وحوش"),
            Pair("ويبتون", "ويبتون"),
            Pair("ون شوت", "ون شوت"),
        )
    }
}
