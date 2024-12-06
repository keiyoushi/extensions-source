package eu.kanade.tachiyomi.extension.ja.kadocomi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.experimental.xor

class KadoComi : HttpSource() {

    override val name = "カドコミ" // KadoComi, formerly Comic Walker

    override val baseUrl = "https://comic-walker.com"

    private val apiUrl = "https://comic-walker.com/api"

    private val cdnUrl = "https://cdn.comic-walker.com"

    override val lang = "ja"

    override val supportsLatest = true

    private val imageDescrambler: Interceptor = Interceptor { chain ->
        val request: Request = chain.request()
        val urlString = request.url.toString()
        val drmHash = request.url.fragment ?: ""

        val response: Response = chain.proceed(request)

        if (urlString.contains("$cdnUrl/images/") && urlString.contains("&Key-Pair-Id=")) {
            val oldBody = response.body.bytes()
            val descrambled = descrambleImage(oldBody, drmHash.decodeHex())
            val newBody = descrambled.toResponseBody("image/jpeg".toMediaTypeOrNull())
            response.newBuilder()
                .body(newBody)
                .build()
        } else {
            response
        }
    }

    override val client = network.client.newBuilder()
        .addNetworkInterceptor(imageDescrambler)
        .build()

    private val json: Json by injectLazy()

    // ============================== Manga Details ===============================

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/detail/${getWorkCode(manga)}"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("contents")
            addPathSegment("details")
            addPathSegment("work")
            addQueryParameter("workCode", getWorkCode(manga))
        }

        return GET(url.build(), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val details = json.decodeFromString<KadoComiWorkDto>(response.body.string())

        var mangaAuthor: String? = null
        var mangaArtist: String? = null

        details.work.authors?.forEach {
            when (it.role) {
                in AUTHOR_ROLES -> {
                    mangaAuthor = it.name
                }
                in ARTIST_ROLES -> {
                    mangaArtist = it.name
                }
                in COMBINED_ROLES -> {
                    mangaAuthor = it.name
                    mangaArtist = it.name
                }
            }
        }

        return SManga.create().apply {
            url = "/detail/${details.work.code}"
            title = details.work.title
            thumbnail_url = getThumbnailUrl(details.work)
            author = mangaAuthor
            artist = mangaArtist
            description = details.work.summary
            genre = getGenres(details.work)
            status = when (details.work.serializationStatus.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "unknown" -> SManga.UNKNOWN
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun getGenres(work: KadoComiWork): String {
        return listOfNotNull(work.genre?.name, work.subGenre?.name)
            .plus(work.tags.orEmpty().map { it.name })
            .joinToString()
    }

    // ============================== Chapters ===============================

    override fun getChapterUrl(chapter: SChapter): String {
        // fragment contains two parameters in the format: #workCode={workCode}&episodeCode={episodeCode}"
        // fragment comes from the URL as a single string, so manipulate to acquire the relevant values
        val fragment = "$baseUrl${chapter.url}".toHttpUrl().fragment
        val params = fragment!!.split("&")
        val workCode = params[0].split("=")[1]
        val episodeCode = params[1].split("=")[1]
        return "$baseUrl/detail/$workCode/episodes/$episodeCode"
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("contents")
            addPathSegment("details")
            addPathSegment("work")
            addQueryParameter("workCode", getWorkCode(manga))
        }

        return GET(url.build(), headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val details = json.decodeFromString<KadoComiWorkDto>(response.body.string())
        val workCode = details.work.code

        return details.latestEpisodes?.result.orEmpty().map { episode ->
            SChapter.create().apply {
                url = "/api/contents/viewer?episodeId=${episode.id}&imageSizeType=width%3A1284#workCode=$workCode&episodeCode=${episode.code}"
                name = "${if (!episode.isActive) LOCK else ""} ${episode.title}"
                date_upload = parseDate(episode.updateDate)
                chapter_number = episode.internal.episodeNo.toFloat()
            }
        }
    }

    // ============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val viewer = json.decodeFromString<KadoComiViewerDto>(response.body.string())

        val pages = viewer.manuscripts.mapIndexed { idx, manuscript ->
            Page(idx, imageUrl = "${manuscript.drmImageUrl.substringAfter(baseUrl)}#${manuscript.drmHash}")
        }

        if (pages.isEmpty()) {
            throw Exception("このチャプターは非公開です\nChapter is not available!")
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun searchMangaParse(response: Response): MangasPage {
        val results = json.decodeFromString<KadoComiSearchResultsDto>(response.body.string())
        return MangasPage(searchResultsParse(results), results.result.size >= SEARCH_LIMIT)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val offset = (SEARCH_LIMIT * page) - SEARCH_LIMIT

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addPathSegment("keywords")
            addQueryParameter("keywords", query)
            addQueryParameter("limit", SEARCH_LIMIT.toString())
            addQueryParameter("offset", offset.toString())
            addQueryParameter("sortBy", "popularity")
        }

        return GET(url.build(), headers)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesParse(response: Response): MangasPage {
        val results = json.decodeFromString<KadoComiSearchResultsDto>(response.body.string())
        return MangasPage(searchResultsParse(results), false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("series")
            addPathSegment("new")
            addQueryParameter("limit", NEW_LIMIT.toString())
        }

        return GET(url.build(), headers)
    }

    // ============================== Popular ===============================

    override fun popularMangaParse(response: Response): MangasPage {
        val results = json.decodeFromString<KadoComiSearchResultsDto>(response.body.string())
        return MangasPage(searchResultsParse(results), false)
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("ranking")
            addQueryParameter("limit", RANKING_LIMIT.toString())
        }

        return GET(url.build(), headers)
    }

    // ============================= Utilities ==============================

    private fun getWorkCode(manga: SManga): String {
        return manga.url.substringAfterLast("/")
    }

    private fun getThumbnailUrl(work: KadoComiWork): String {
        return work.bookCover ?: work.thumbnail
    }

    // https://stackoverflow.com/a/66614516
    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }

        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun descrambleImage(imageByteArray: ByteArray, hashByteArray: ByteArray): ByteArray {
        return imageByteArray.mapIndexed { idx, byte ->
            byte xor hashByteArray[idx % hashByteArray.size]
        }.toByteArray()
    }

    private fun searchResultsParse(results: KadoComiSearchResultsDto): List<SManga> {
        return results.result.map {
            SManga.create().apply {
                url = "/detail/${it.code}"
                title = it.title
                thumbnail_url = getThumbnailUrl(it)
            }
        }
    }

    companion object {
        // inactive chapter icon
        private const val LOCK = "🔒 "

        // date formatting
        private fun parseDate(dateStr: String): Long {
            return try {
                dateFormat.parse(dateStr)!!.time
            } catch (_: ParseException) {
                0L
            }
        }

        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH)
        }

        // search limits, mimics site functionality
        private const val SEARCH_LIMIT = 20
        private const val RANKING_LIMIT = 50
        private const val NEW_LIMIT = 100

        // author/artist roles
        private val AUTHOR_ROLES = arrayOf("原作")
        private val ARTIST_ROLES = arrayOf("漫画", "作画")
        private val COMBINED_ROLES = arrayOf("著者")
    }
}
