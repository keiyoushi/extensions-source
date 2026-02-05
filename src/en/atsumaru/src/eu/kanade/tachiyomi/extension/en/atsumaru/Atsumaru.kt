package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class Atsumaru : HttpSource() {

    override val versionId = 2

    override val name = "Atsumaru"

    override val baseUrl = "https://atsu.moe"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .addInterceptor { chain ->
            val request = chain.request()
            println("Sending Request: ${request.url}")
            if (request.body != null) {
                val buffer = okio.Buffer()
                request.body!!.writeTo(buffer)
                println("Request Payload: ${buffer.readUtf8()}")
            }

            val response = chain.proceed(request)
            if (!response.isSuccessful) {
                println("Error Code: ${response.code}")
                println("Error Body: ${response.peekBody(Long.MAX_VALUE).string()}")
            }
            response
        }
        .build()

    private val json: Json by injectLazy()

    private fun apiHeadersBuilder() = headersBuilder().apply {
        add("Accept", "*/*")
        add("Host", "atsu.moe")
        add("Content-Type", "application/json")
    }

    private val apiHeaders by lazy { apiHeadersBuilder().build() }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/infinite/trending?page=${page - 1}&types=Manga,Manwha,Manhua,OEL", apiHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<BrowseMangaDto>().items

        return MangasPage(data.map { it.toSManga(baseUrl) }, true)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/infinite/recentlyUpdated?page=${page - 1}&types=Manga,Manwha,Manhua,OEL", apiHeaders)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored when using text search!"),
        Filter.Separator(),
        GenreFilter(getGenresList()),
        TypeFilter(getTypesList()),
        StatusFilter(getStatusList()),
        YearFilter(),
        MinChaptersFilter(),
        Filter.Separator(),
        SortFilter(),
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val jsonPayload = buildJsonPayload(page - 1, filters, query)
        val requestBody = jsonPayload.toRequestBody("application/json".toMediaType())

        return POST("$baseUrl/api/explore/filteredView", apiHeaders, requestBody)
    }

    private fun buildJsonPayload(page: Int, filters: FilterList, query: String): String {
        var genreId: String? = null
        val typesList = mutableListOf<String>()
        val statusesList = mutableListOf<String>()
        var year: Int? = null
        var minChapters: Int? = null
        var sort = "trending"

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.state > 0) genreId = filter.genres[filter.state - 1].id
                }

                is TypeFilter -> {
                    if (filter.state > 0) typesList.add(filter.types[filter.state - 1].id)
                }

                is StatusFilter -> {
                    if (filter.state > 0) statusesList.add(filter.statuses[filter.state - 1].id)
                }

                is YearFilter -> {
                    if (filter.state.isNotEmpty()) year = filter.state.toIntOrNull()
                }

                is MinChaptersFilter -> {
                    if (filter.state.isNotEmpty()) minChapters = filter.state.toIntOrNull()
                }

                is SortFilter -> {
                    sort = SortFilter.VALUES[filter.state!!.index]
                }

                else -> {}
            }
        }

        val jsonObject = buildJsonObject {
            put("page", page)
            put("sort", sort)

            putJsonObject("filter") {
                if (query.isNotEmpty()) {
                    put("search", query)
                }

                putJsonArray("types") {
                    if (typesList.isEmpty()) {
                        add("Manga")
                        add("Manwha")
                        add("Manhua")
                        add("OEL")
                    } else {
                        typesList.forEach { add(it) }
                    }
                }

                if (statusesList.isNotEmpty()) {
                    putJsonArray("statuses") {
                        statusesList.forEach { add(it) }
                    }
                }

                if (genreId != null) {
                    putJsonArray("includedTags") {
                        add(genreId)
                    }
                }

                if (year != null) put("year", year)
                if (minChapters != null) put("minChapters", minChapters)
            }
        }

        return jsonObject.toString()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body.string()

        return if (body.contains("\"hits\"")) {
            val data = json.decodeFromString<SearchResultsDto>(body)
            MangasPage(data.hits.map { it.document.toSManga(baseUrl) }, data.hasNextPage())
        } else {
            val data = json.decodeFromString<BrowseMangaDto>(body)
            MangasPage(data.items.map { it.toSManga(baseUrl) }, true)
        }
    }

    private class GenreFilter(val genres: List<Genre>) :
        Filter.Select<String>(
            "Genre",
            arrayOf("None") + genres.map { it.name }.toTypedArray(),
        )

    private class TypeFilter(val types: List<Type>) :
        Filter.Select<String>(
            "Manga Type",
            arrayOf("None") + types.map { it.name }.toTypedArray(),
        )

    private class StatusFilter(val statuses: List<Status>) :
        Filter.Select<String>(
            "Publishing Status",
            arrayOf("None") + statuses.map { it.name }.toTypedArray(),
        )

    private class YearFilter : Filter.Text("Year (e.g., 2024)")

    private class MinChaptersFilter : Filter.Text("Minimum Chapters")

    private class SortFilter :
        Filter.Sort(
            "Sort By",
            arrayOf("Title", "Popularity", "Trending", "Date Added", "Release Date"),
            Selection(0, true),
        ) {
        companion object {
            val VALUES = arrayOf("title", "", "trending", "createdAt", "released")
        }
    }

    private data class Genre(val name: String, val id: String)
    private data class Type(val name: String, val id: String)
    private data class Status(val name: String, val id: String)

    private fun getGenresList() = listOf(
        Genre("Action", "Ip0"),
        Genre("Adult", "oU1"),
        Genre("Adventure", "wY2"),
        Genre("Avant Garde", "6n3"),
        Genre("Award Winning", "6f4"),
        Genre("Boys Love", "Dw5"),
        Genre("Comedy", "pr6"),
        Genre("Doujinshi", "CA7"),
        Genre("Drama", "ME8"),
        Genre("Ecchi", "Gf9"),
        Genre("Erotica", "2S10"),
        Genre("Fantasy", "yv11"),
        Genre("Gender Bender", "Zw12"),
        Genre("Girls Love", "8613"),
        Genre("Gourmet", "jk14"),
        Genre("Harem", "hg15"),
        Genre("Hentai", "d416"),
        Genre("Historical", "qW17"),
        Genre("Horror", "NH18"),
        Genre("Josei", "Uq19"),
        Genre("Lolicon", "XZ20"),
        Genre("Mahou Shoujo", "n421"),
        Genre("Martial Arts", "XO22"),
        Genre("Mature", "Gi23"),
        Genre("Mecha", "N824"),
        Genre("Music", "Eh25"),
        Genre("Mystery", "Xz26"),
        Genre("Psychological", "FV27"),
        Genre("Romance", "Ex28"),
        Genre("School Life", "Zu29"),
        Genre("Sci-Fi", "3j30"),
        Genre("Seinen", "pw31"),
        Genre("Shotacon", "rv32"),
        Genre("Shoujo", "4W33"),
        Genre("Shoujo Ai", "hM34"),
        Genre("Shounen", "W935"),
        Genre("Shounen Ai", "DE36"),
        Genre("Slice of Life", "YX37"),
        Genre("Smut", "ZB38"),
        Genre("Sports", "NC39"),
        Genre("Supernatural", "hT40"),
        Genre("Suspense", "WM41"),
        Genre("Thriller", "e742"),
        Genre("Tragedy", "tn43"),
        Genre("Yaoi", "7D44"),
        Genre("Yuri", "po45"),
    )

    private fun getTypesList() = listOf(
        Type("Manga", "Manga"),
        Type("Manhwa", "Manwha"),
        Type("Manhua", "Manhua"),
        Type("OEL", "OEL"),
    )

    private fun getStatusList() = listOf(
        Status("Ongoing", "Ongoing"),
        Status("Completed", "Completed"),
        Status("Hiatus", "Hiatus"),
        Status("Canceled", "Canceled"),
    )

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api/manga/page?id=${manga.url}", apiHeaders)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaObjectDto>().mangaPage.toSManga(baseUrl)

    // ============================== Chapters ==============================

    private fun fetchChaptersRequest(mangaId: String, page: Int): Request = GET("$baseUrl/api/manga/chapters?id=$mangaId&filter=all&sort=desc&page=$page", apiHeaders)

    override fun chapterListRequest(manga: SManga): Request = fetchChaptersRequest(manga.url, 0)

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = response.request.url.queryParameter("id")!!
        val chapterList = mutableListOf<ChapterDto>()

        var result = response.parseAs<ChapterListDto>()
        chapterList.addAll(result.chapters)

        while (result.hasNextPage()) {
            result = client.newCall(fetchChaptersRequest(mangaId, result.page + 1)).execute().parseAs()
            chapterList.addAll(result.chapters)
        }

        return chapterList.map { it.toSChapter(mangaId) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, name) = chapter.url.split("/")
        return "$baseUrl/read/$slug/$name"
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        val (slug, name) = chapter.url.split("/")
        val url = "$baseUrl/api/read/chapter".toHttpUrl().newBuilder()
            .addQueryParameter("mangaId", slug)
            .addQueryParameter("chapterId", name)

        return GET(url.build(), apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<PageObjectDto>().readChapter.pages.mapIndexed { index, page ->
        Page(index, imageUrl = baseUrl + page.image)
    }

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
