package eu.kanade.tachiyomi.extension.ja.kadocomi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
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
        val isPage = urlString.contains("$cdnUrl/images/") && urlString.contains("&Key-Pair-Id=")
        val drmHash = request.url.fragment ?: ""

        if (isPage) removeFragmentFromRequestUrl(request)

        val response: Response = chain.proceed(request)

        if (isPage) {
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

    private val chapterUrlInterceptor: Interceptor = Interceptor { chain ->
        val request: Request = chain.request()
        if (request.url.toString().contains("$apiUrl/contents/viewer/")) removeFragmentFromRequestUrl(request)
        chain.proceed(request)
    }

    override val client = network.client.newBuilder()
        .addNetworkInterceptor(chapterUrlInterceptor)
        .addNetworkInterceptor(imageDescrambler)
        .build()

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
        val responseJson = JSONObject(response.body.string())
        val work = responseJson.getJSONObject("work")

        val workCode = work.getString("code")
        val mangaTitle = work.getString("title")
        val thumbnailUrl = getThumbnailUrl(work)
        val serializationStatus = work.getString("serializationStatus")
        val summary = work.getString("summary")
        val genreName = getGenres(work)
        val authors = work.getJSONArray("authors")

        var mangaAuthor: String? = null
        var mangaArtist: String? = null

        for (i in 0 until authors.length()) {
            val author = authors.getJSONObject(i)
            val role = author.getString("role")
            val name = author.getString("name")

            when (role) {
                in AUTHOR_ROLES -> {
                    mangaAuthor = name
                }
                in ARTIST_ROLES -> {
                    mangaArtist = name
                }
                in COMBINED_ROLES -> {
                    mangaAuthor = name
                    mangaArtist = name
                }
            }
        }

        return SManga.create().apply {
            url = "/detail/$workCode"
            title = mangaTitle
            thumbnail_url = thumbnailUrl
            author = mangaAuthor
            artist = mangaArtist
            description = summary
            genre = genreName
            status = when (serializationStatus.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "unknown" -> SManga.UNKNOWN
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun getGenres(work: JSONObject): String {
        val list: MutableList<String> = arrayListOf()

        val genreName = work.getJSONObject("genre").getString("name")
        val subGenreName = work.getJSONObject("subGenre").getString("name")
        val tags = work.getJSONArray("tags")

        list.add(genreName)
        list.add(subGenreName)
        for (i in 0 until tags.length()) {
            val tag = tags.getJSONObject(i)
            val tagName = tag.getString("name")
            list.add(tagName)
        }

        return list.joinToString()
    }

    // ============================== Chapters ===============================

    override fun getChapterUrl(chapter: SChapter): String {
        val fragment = chapter.url.substringAfterLast("#")
        val params = fragment.split("&")
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
        val responseJson = JSONObject(response.body.string())
        val work = responseJson.getJSONObject("work")
        val workCode = work.getString("code")

        val list: MutableList<SChapter> = arrayListOf()
        val episodes = responseJson.getJSONObject("latestEpisodes").getJSONArray("result")

        for (i in 0 until episodes.length()) {
            val episode = episodes.getJSONObject(i)
            val episodeId = episode.getString("id")
            val episodeCode = episode.getString("code")
            var episodeTitle = episode.getString("title")
            val dateUpload = episode.getString("updateDate")
            if (!episode.getBoolean("isActive")) {
                episodeTitle = "$LOCK $episodeTitle"
            }

            list.add(
                SChapter.create().apply {
                    url = "/api/contents/viewer?episodeId=$episodeId&imageSizeType=width%3A1284#workCode=$workCode&episodeCode=$episodeCode"
                    name = episodeTitle
                    date_upload = parseDate(dateUpload)
                    chapter_number = episode.getJSONObject("internal").getInt("episodeNo").toFloat()
                },
            )
        }

        return list
    }

    // ============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val responseJson = JSONObject(response.body.string())
        val manuscripts = responseJson.getJSONArray("manuscripts")

        val list: MutableList<Page> = arrayListOf()

        for (i in 0 until manuscripts.length()) {
            val manuscript = manuscripts.getJSONObject(i)
            val drmImageUrl = manuscript.getString("drmImageUrl").substringAfter(baseUrl)
            val drmHash = manuscript.getString("drmHash")
            val imageUrl = "$drmImageUrl#$drmHash" // hacky workaround to get drmHash to interceptor

            list.add(Page(i, imageUrl = imageUrl))
        }

        if (list.size < 1) {
            throw Exception("このチャプターは非公開です\nChapter is not available!")
        }

        return list
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun searchMangaParse(response: Response): MangasPage {
        val responseJson = JSONObject(response.body.string())
        val result = responseJson.getJSONArray("result")
        return MangasPage(searchResultsParse(responseJson), result.length() >= SEARCH_LIMIT)
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
        val responseJson = JSONObject(response.body.string())
        return MangasPage(searchResultsParse(responseJson), false)
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
        val responseJson = JSONObject(response.body.string())
        return MangasPage(searchResultsParse(responseJson), false)
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
        return manga.url.split("/").reversed().first()
    }

    private fun getThumbnailUrl(json: JSONObject): String {
        return if (json.has("bookCover")) json.getString("bookCover") else json.getString("thumbnail")
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

    private fun removeFragmentFromRequestUrl(request: Request): Request {
        return request.newBuilder().url(request.url.toString().substringBeforeLast("#")).build()
    }

    private fun searchResultsParse(responseJson: JSONObject): List<SManga> {
        val result = responseJson.getJSONArray("result")
        val list: MutableList<SManga> = arrayListOf()
        for (i in 0 until result.length()) {
            val manga = result.getJSONObject(i)

            list.add(
                SManga.create().apply {
                    url = "/detail/${manga.getString("code")}"
                    title = manga.getString("title")
                    thumbnail_url = getThumbnailUrl(manga)
                },
            )
        }
        return list
    }

    companion object {
        // inactive chapter icon
        private const val LOCK = "🔒"

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
