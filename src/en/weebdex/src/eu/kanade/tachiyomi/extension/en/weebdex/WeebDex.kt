package eu.kanade.tachiyomi.extension.en.weebdex

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class WeebDex : HttpSource() {
    override val name = "WeebDex"
    override val baseUrl = "https://api.weebdex.org"
    override val lang = "en"
    override val supportsLatest = true

    // Rate limit by WeebDex API is 5reqs/sec. Its toned down to 3 here just in case.
    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    // -------------------- Popular --------------------

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/manga".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "views")
            .addQueryParameter("order", "desc")
            .addQueryParameter("hasChapters", "1")
            .build()
        return GET(url.toString())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body.string()
        val json = JSONObject(body)
        val data = json.optJSONArray("data") ?: JSONArray()
        val mangas = mutableListOf<SManga>()
        for (i in 0 until data.length()) {
            val m = data.getJSONObject(i)
            mangas += mangaFromJson(m)
        }
        val page = json.optInt("page", 1)
        val limit = json.optInt("limit", mangas.size)
        val total = json.optInt("total", page * limit)
        val hasNext = page * limit < total

        return MangasPage(mangas, hasNext)
    }

    // -------------------- Latest --------------------
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/manga".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "updatedAt")
            .addQueryParameter("order", "desc")
            .addQueryParameter("hasChapters", "1")
            .build()
        return GET(url.toString())
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // -------------------- Search --------------------
    override fun getFilterList(): FilterList = buildFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/manga".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("title", query)
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is TagList -> {
                        filter.state.forEach { tag ->
                            if (tag.state) {
                                TAGS[tag.name]?.let { tagId ->
                                    urlBuilder.addQueryParameter("tag", tagId)
                                }
                            }
                        }
                    }
                    is TagsExcludeFilter -> {
                        filter.state.forEach { tag ->
                            if (tag.state) {
                                TAGS[tag.name]?.let { tagId ->
                                    urlBuilder.addQueryParameter("tagx", tagId)
                                }
                            }
                        }
                    }
                    is TagModeFilter -> urlBuilder.addQueryParameter("tmod", filter.state.toString())
                    is TagExcludeModeFilter -> urlBuilder.addQueryParameter("txmod", filter.state.toString())
                    else -> { /* Do Nothing */ }
                }
            }
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> urlBuilder.addQueryParameter("sort", filter.selected)
                is OrderFilter -> urlBuilder.addQueryParameter("order", filter.selected)
                is StatusFilter -> filter.selected?.let { urlBuilder.addQueryParameter("status", it) }
                is DemographicFilter -> filter.selected?.let { urlBuilder.addQueryParameter("demographic", it) }
                is ContentRatingFilter -> filter.selected?.let { urlBuilder.addQueryParameter("contentRating", it) }
                is LangFilter -> filter.query?.let { urlBuilder.addQueryParameter("lang", it) }
                is HasChaptersFilter -> if (filter.state) urlBuilder.addQueryParameter("hasChapters", "1")
                is YearFromFilter -> filter.state.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("yearFrom", it) }
                is YearToFilter -> filter.state.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("yearTo", it) }
                else -> { /* Do Nothing */ }
            }
        }

        return GET(urlBuilder.build().toString())
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // -------------------- Manga details --------------------

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val body = response.body.string()
        val obj = JSONObject(body)
        val m = obj.optJSONObject("data") ?: obj
        val rel = m.optJSONObject("relationships")

        return SManga.create().apply {
            title = m.optString("title")
            description = m.optString("description")
            status = parseStatus(m.optString("status"))
            thumbnail_url = buildCoverUrl(m)

            if (rel != null) {
                rel.optJSONArray("authors")?.let { author = jsonArrayToString(it) }
                rel.optJSONArray("artists")?.let { artist = jsonArrayToString(it) }
                rel.optJSONArray("tags")?.let { genre = jsonArrayToString(it) }
            }
        }
    }

    // -------------------- Chapters --------------------

    override fun chapterListRequest(manga: SManga): Request {
        // chapter list is paginated; get all pages
        return GET("$baseUrl${manga.url}/chapters?order=desc")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()

        val chapters = mutableListOf<SChapter>()

        // Recursively parse pages
        fun parsePage(json: JSONObject) {
            val arr = json.optJSONArray("data") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val ch = arr.getJSONObject(i)
                val s = SChapter.create().apply {
                    url = "/chapter/${ch.getString("id")}"
                    val chapTitle = ch.optString("title")
                    name = chapTitle.ifBlank { "Chapter ${ch.optString("chapter")}" }
                    date_upload = parseDate(ch.optString("published_at"))
                }
                chapters.add(s)
            }
            val page = json.optInt("page", 1)
            val limit = json.optInt("limit", 1)
            val total = json.optInt("total", 0)
            if (page * limit < total) {
                val nextUrl = response.request.url.newBuilder()
                    .setQueryParameter("page", (page + 1).toString())
                    .build()
                val nextResponse = client.newCall(GET(nextUrl)).execute()
                parsePage(JSONObject(nextResponse.body.string()))
            }
        }
        parsePage(JSONObject(body))
        return chapters
    }

    // -------------------- Pages --------------------

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl${chapter.url}")
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val obj = JSONObject(body)
        val data = obj.optJSONObject("data") ?: obj
        val pagesArray = data.optJSONArray("data_optimized") ?: data.optJSONArray("data") ?: JSONArray()
        val pages = mutableListOf<Page>()
        for (i in 0 until pagesArray.length()) {
            val p = pagesArray.getJSONObject(i)
            // pages in spec have 'name' field and images served from /data/{id}/{filename}
            val filename = p.optString("name")
            val chapterId = data.optString("id")
            val imageUrl = if (filename.isNotBlank() && chapterId.isNotBlank()) {
                "https://srv.notdelta.xyz/data/$chapterId/$filename"
            } else {
                p.optString("imageUrl", "")
            }
            pages.add(Page(i, imageUrl = imageUrl))
        }
        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // -------------------- Utilities --------------------

    private fun mangaFromJson(m: JSONObject): SManga {
        return SManga.create().apply {
            title = m.optString("title")
            url = "/manga/${m.getString("id")}"
            thumbnail_url = buildCoverUrl(m)
            description = m.optString("description")
        }
    }

    private fun buildCoverUrl(m: JSONObject): String? {
        val rel = m.optJSONObject("relationships") ?: return null
        val cover = rel.optJSONObject("cover") ?: return null
        val ext = cover.optString("ext", ".jpg")
        return "https://srv.notdelta.xyz/covers/${m.getString("id")}/${cover.getString("id")}$ext"
    }

    private fun jsonArrayToString(arr: JSONArray): String {
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            // tags/authors may be objects or strings; handle both
            val item = arr.get(i)
            if (item is JSONObject) {
                list.add(item.optString("name", item.optString("id")))
            } else {
                list.add(item.toString())
            }
        }
        return list.joinToString(", ")
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase(Locale.ROOT)) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        return try {
            // ISO8601 example: 2025-10-16T12:34:56.000Z
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            try {
                // fallback: 2024-03-22 17:03:52
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                fmt.parse(dateStr)?.time ?: 0L
            } catch (e2: Exception) {
                try {
                    // fallback: unix timestamp in seconds
                    val sec = dateStr.toLong()
                    sec * 1000
                } catch (e3: Exception) { 0L }
            }
        }
    }
}
