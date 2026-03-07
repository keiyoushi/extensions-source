package eu.kanade.tachiyomi.extension.ja.rawuwu

import eu.kanade.tachiyomi.extension.ja.rawuwu.dto.ChapterPageResponseDto
import eu.kanade.tachiyomi.extension.ja.rawuwu.dto.MangaDetailResponseDto
import eu.kanade.tachiyomi.extension.ja.rawuwu.dto.RawUwUResponseDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class RawUwU : HttpSource() {

    override val name = "Raw UwU"
    override val baseUrl = "https://rawuwu.net"
    override val lang = "ja"
    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // --- BROWSE (POPULAR / LATEST / SEARCH) ---

    override fun getFilterList() = FilterList(
        GenreFilter(),
        StatusFilter(),
        SortFilter(),
    )

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/spa/genre/all".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "most_viewed")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/spa/latest-manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotEmpty()) {
        val url = "$baseUrl/spa/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())
            .build()
        GET(url, headers)
    } else {
        val genreFilter = filters.firstInstance<GenreFilter>()
        val statusFilter = filters.firstInstance<StatusFilter>()
        val sortFilter = filters.firstInstance<SortFilter>()

        val genreCode = genreFilter.toCode()
        val url = "$baseUrl/spa/genre/$genreCode".toHttpUrl().newBuilder()

        statusFilter.toValue().takeIf { it.isNotEmpty() }?.let {
            url.addQueryParameter("status", it)
        }
        sortFilter.toValue().takeIf { it.isNotEmpty() }?.let {
            url.addQueryParameter("sort", it)
        }

        url.addQueryParameter("page", page.toString())

        GET(url.build(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListResponse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListResponse(response)
    override fun searchMangaParse(response: Response): MangasPage = parseMangaListResponse(response)

    private fun parseMangaListResponse(response: Response): MangasPage {
        val result = response.parseAs<RawUwUResponseDto>()

        val mangas = result.manga_list?.map { manga ->
            SManga.create().apply {
                url = "/raw/${manga.manga_id}"
                title = manga.manga_name
                thumbnail_url = manga.manga_cover_img
            }
        } ?: emptyList()

        val hasNextPage = result.pagi?.button?.next?.let { it > 0 } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    // --- DETAILS ---

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.split("/").last().filter { it.isDigit() }
        return GET("$baseUrl/spa/manga/$id")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailResponseDto>()

        return SManga.create().apply {
            val detail = result.detail ?: throw Exception("Could not find manga details")
            title = detail.manga_name
            thumbnail_url = detail.manga_cover_img_full
                ?: detail.manga_cover_img ?: ""

            val descriptionText = detail.manga_description ?: ""
            val altName = detail.manga_others_name ?: ""

            description = buildString {
                append(descriptionText)
                append("\n\n")

                if (altName.isNotEmpty()) {
                    append("Alternative Names: ")
                    append("\n• ")
                    append(altName.replace(",", "\n• "))
                }
            }

            author = result.authors?.joinToString { it.author_name }
            genre = result.tags?.joinToString { it.tag_name }

            val isActive = detail.manga_status
            status = when (isActive) {
                true -> SManga.COMPLETED
                false -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    // --- CHAPTERS ---

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.split("/").last().filter { it.isDigit() }
        return GET("$baseUrl/spa/manga/$id", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = response.parseAs<MangaDetailResponseDto>()
        val mangaId = json.detail?.manga_id ?: ""
        val chaptersArray = json.chapters ?: return emptyList()

        return chaptersArray.map { chapter ->
            SChapter.create().apply {
                val num = chapter.chapter_number ?: ""
                url = "/read/$mangaId/chapter-$num"
                val title = chapter.chapter_title ?: ""
                name = if (title.isNotEmpty()) "Ch. $num - $title" else "Chapter $num"
                date_upload = parseDate(chapter.chapter_date_published ?: "")
            }
        }
    }

    // --- PAGES ---

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = chapter.url.trim('/').split("/")
        val mangaId = segments[1]
        val chapterNum = segments[2].removePrefix("chapter-")

        return GET("$baseUrl/spa/manga/$mangaId/$chapterNum", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ChapterPageResponseDto>()

        val chapterDetail = result.chapter_detail
        val serverUrl = chapterDetail?.server ?: ""
        val htmlContent = chapterDetail?.chapter_content ?: ""

        val document = org.jsoup.Jsoup.parseBodyFragment(htmlContent)

        return document.select("img").mapIndexed { i, img ->
            val rawPath = img.attr("data-src")
                .ifEmpty { img.attr("src") }
                .removePrefix("/")

            Page(i, "", "$serverUrl/$rawPath")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .parse(dateStr)?.time ?: 0
        } catch (_: Exception) {
            0
        }
    }
}

class GenreFilter :
    Filter.Select<String>(
        "Genre",
        arrayOf(
            "All", "Action", "Adaptions", "Adult", "Adventure", "Alternative World", "Animals", "Animated", "Comedy", "Cooking", "Crime", "Drama", "Ecchi", "Elves", "Fantasy", "Food", "Game", "Gender Bender", "Girls' Love", "Harem", "Hentai", "Historical", "Horror", "Isekai", "Josei", "Loli", "Lolicon", "Magic", "Manhua", "Manhwa", "Martial Arts", "Mature", "Mecha", "Medical", "Moe", "Mystery", "One shot", "Oneshot", "Philosophical", "Police", "Psychological", "Romance", "School Life", "Sci-fi", "Seinen", "Shotacon", "Shoujo", "Shoujo Ai", "Shounen", "Shounen Ai", "Slice of Life", "Smut", "Sports", "Supernatural", "Thriller", "Tragedy", "Trap (crossdressing)", "War", "Webtoons", "Yaoi", "Yuri",
        ),
    ) {
    fun toCode() = when (state) {
        0 -> "all"
        1 -> "85" // Action
        2 -> "163" // Adaptions
        3 -> "139" // Adult
        4 -> "86" // Adventure
        5 -> "149" // Alternative World
        6 -> "168" // Animals
        7 -> "140" // Animated
        8 -> "87" // Comedy
        9 -> "134" // Cooking
        10 -> "165" // Crime
        11 -> "114" // Drama
        12 -> "88" // Ecchi
        13 -> "150" // Elves
        14 -> "89" // Fantasy
        15 -> "152" // Food
        16 -> "155" // Game
        17 -> "111" // Gender Bender
        18 -> "167" // Girls' Love
        19 -> "90" // Harem
        20 -> "169" // Hentai
        21 -> "115" // Historical
        22 -> "127" // Horror
        23 -> "144" // Isekai
        24 -> "130" // Josei
        25 -> "91" // Loli
        26 -> "148" // Lolicon
        27 -> "151" // Magic
        28 -> "128" // Manhua
        29 -> "125" // Manhwa
        30 -> "126" // Martial Arts
        31 -> "112" // Mature
        32 -> "143" // Mecha
        33 -> "132" // Medical
        34 -> "141" // Moe
        35 -> "121" // Mystery
        36 -> "142" // One shot
        37 -> "157" // Oneshot
        38 -> "170" // Philosophical
        39 -> "166" // Police
        40 -> "119" // Psychological
        41 -> "106" // Romance
        42 -> "108" // School Life
        43 -> "146" // Sci-fi
        44 -> "107" // Seinen
        45 -> "154" // Shotacon
        46 -> "120" // Shoujo
        47 -> "131" // Shoujo Ai
        48 -> "118" // Shounen
        49 -> "109" // Shounen Ai
        50 -> "92" // Slice of Life
        51 -> "123" // Smut
        52 -> "124" // Sports
        53 -> "93" // Supernatural
        54 -> "164" // Thriller
        55 -> "135" // Tragedy
        56 -> "138" // Trap (crossdressing)
        57 -> "153" // War
        58 -> "116" // Webtoons
        59 -> "161" // Yaoi
        60 -> "110" // Yuri
        else -> "all"
    }
}

class StatusFilter : Filter.Select<String>("Status", arrayOf("All", "Ongoing", "Completed")) {
    fun toValue() = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        else -> ""
    }
}

class SortFilter : Filter.Select<String>("Sort", arrayOf("Update Date", "Popular", "Popular Today")) {
    fun toValue() = when (state) {
        1 -> "most_viewed"
        2 -> "most_viewed_today"
        else -> ""
    }
}
