package eu.kanade.tachiyomi.multisrc.mangahub

import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
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
import keiyoushi.utils.tryParse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

abstract class MangaHub(
    override val name: String,
    final override val baseUrl: String,
    override val lang: String,
    private val mangaSource: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH),
) : HttpSource() {

    override val supportsLatest = true

    private var baseApiUrl = "https://api.mghcdn.com"
    private var baseCdnUrl = "https://imgx.mghcdn.com"
    private var baseThumbCdnUrl = "https://thumb.mghcdn.com"
    private val regex = Regex("mhub_access=([^;]+)")

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .setRandomUserAgent(
            userAgentType = UserAgentType.DESKTOP,
            filterInclude = listOf("chrome"),
        )
        .addInterceptor(::apiAuthInterceptor)
        .rateLimit(1)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
        .add("Accept-Language", "en-US,en;q=0.5")
        .add("DNT", "1")
        .add("Referer", "$baseUrl/")
        .add("Sec-Fetch-Dest", "document")
        .add("Sec-Fetch-Mode", "navigate")
        .add("Sec-Fetch-Site", "same-origin")
        .add("Upgrade-Insecure-Requests", "1")

    private fun graphQLHeader() = headersBuilder()
        .set("Accept", "application/json")
        .set("Content-Type", "application/json")
        .set("Origin", baseUrl)
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Site", "cross-site")
        .removeAll("Upgrade-Insecure-Requests")
        .build()

    open val json: Json by injectLazy()

    private fun postRequestGraphQL(query: String): Request {
        val body = buildJsonObject {
            put("query", query)
        }

        return POST("$baseApiUrl/graphql", graphQLHeader(), body.toString().toRequestBody())
    }

    private fun apiAuthInterceptor(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val cookie = client.cookieJar
            .loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == "mhub_access" && it.value.isNotEmpty() }

        val request =
            if (originalRequest.url.toString() == "$baseApiUrl/graphql" && cookie != null) {
                originalRequest.newBuilder()
                    .header("x-mhub-access", cookie.value)
                    .build()
            } else {
                originalRequest
            }

        return chain.proceed(request)
    }

    private fun refreshApiKey(chapter: SChapter) {
        val slug = "$baseUrl${chapter.url}"
            .toHttpUrlOrNull()
            ?.pathSegments
            ?.get(1)

        val url = if (slug != null) {
            "$baseUrl/manga/$slug".toHttpUrl()
        } else {
            baseUrl.toHttpUrl()
        }

        val oldKey = client.cookieJar
            .loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == "mhub_access" && it.value.isNotEmpty() }?.value

        for (i in 1..2) {
            // Clear key cookie
            val cookie = Cookie.parse(url, "mhub_access=; Max-Age=0; Path=/")!!
            client.cookieJar.saveFromResponse(url, listOf(cookie))

            // We try requesting again with param if the first one fails
            val query = if (i == 2) "?reloadKey=1" else ""

            try {
                val response = client.newCall(GET("$url$query", headers)).execute()
                val returnedKey = response.headers["set-cookie"]?.let { regex.find(it)?.groupValues?.get(1) }
                response.close() // Avoid potential resource leaks

                if (returnedKey != oldKey) break; // Break out of loop since we got an allegedly valid API key
            } catch (_: IOException) {
                throw IOException("An error occurred while obtaining a new API key") // Show error
            }
        }
    }

    data class SMangaDTO(
        val url: String,
        val title: String,
        val thumbnailUrl: String,
        val signature: String,
    )

    private fun ApiMangaSearchItem.toSignature(): String {
        val author = this.author
        val chNum = this.latestChapter
        val genres = this.genres

        return author + chNum + genres
    }

    private fun mangaRequest(page: Int, order: String): Request = postRequestGraphQL(SEARCH_QUERY(mangaSource, "", "all", order, (page - 1) * 30))

    // popular
    override fun popularMangaRequest(page: Int): Request = mangaRequest(page, "POPULAR")

    // often enough there will be nearly identical entries with slightly different
    // titles, URLs, and image names. in order to cut these "duplicates" down,
    // assign a "signature" based on author name, chapter number, and genres
    // if all of those are the same, then it it's the same manga
    override fun popularMangaParse(response: Response): MangasPage {
        val mangaList = json.decodeFromString<ApiSearchResponse>(response.body.string())

        val mangas = mangaList.data.search.rows.map {
            SMangaDTO(
                "$baseUrl/manga/${it.slug}",
                it.title,
                "$baseThumbCdnUrl/${it.image}",
                it.toSignature(),
            )
        }
            .distinctBy { it.signature }
            .map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.url)
                    title = it.title
                    thumbnail_url = it.thumbnailUrl
                }
            }

        return MangasPage(mangas, true)
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request = mangaRequest(page, "LATEST")

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/page/$page".toHttpUrl().newBuilder()
        url.addQueryParameter("q", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    val order = filter.values[filter.state]
                    url.addQueryParameter("order", order.key)
                }
                is GenreList -> {
                    val genre = filter.values[filter.state]
                    url.addQueryParameter("genre", genre.key)
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // manga details
    override fun mangaDetailsRequest(manga: SManga): Request = postRequestGraphQL(MANGA_DETAILS_QUERY(mangaSource, manga.url.removePrefix("/manga/")))

    override fun mangaDetailsParse(response: Response): SManga {
        val rawManga = json.decodeFromString<ApiMangaDetailsResponse>(response.body.string())
        val manga = SManga.create()

        manga.title = rawManga.data.manga.title!!
        manga.author = rawManga.data.manga.author
        manga.artist = rawManga.data.manga.artist
        manga.genre = rawManga.data.manga.genres
        manga.description = rawManga.data.manga.description
        manga.thumbnail_url = "$baseThumbCdnUrl/${rawManga.data.manga.image}"
        manga.status = when (rawManga.data.manga.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        // Add alternative title
        if (rawManga.data.manga.alternativeTitle != null) {
            manga.description = manga.description.orEmpty().let {
                if (it.isBlank()) {
                    "Alternative Name: ${rawManga.data.manga.alternativeTitle}"
                } else {
                    "$it\n\nAlternative Name: ${rawManga.data.manga.alternativeTitle}"
                }
            }
        }

        return manga
    }

    override fun getMangaUrl(manga: SManga): String = "${baseUrl}${manga.url}"

    // Chapters
    override fun chapterListRequest(manga: SManga): Request = postRequestGraphQL(MANGA_CHAPTER_LIST_QUERY(mangaSource, manga.url.removePrefix("/manga/")))

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList = json.decodeFromString<ApiMangaDetailsResponse>(response.body.string())

        return chapterList.data.manga.chapters!!.map {
            val chapter = SChapter.create()

            chapter.name = it.title
            chapter.url = "/${chapterList.data.manga.slug}/chapter-${it.number}"
            chapter.chapter_number = it.number
            chapter.scanlator = "#${if (it.number % 1 == 0f) it.number.toInt() else it.number}" // Title namings are very inconsistent, some of them don't have the numbers specified so we need a way to display it uniformly
            chapter.date_upload = dateFormat.tryParse(it.date)
            chapter
        }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter${chapter.url}"

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url.split("/")

        return postRequestGraphQL(PAGES_QUERY(mangaSource, chapterUrl[1], chapterUrl[2].substringAfter("-").toFloat()))
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        super.fetchPageList(chapter)
            .doOnError { refreshApiKey(chapter) }
            .retry(1)

    override fun pageListParse(response: Response): List<Page> {
        val chapterObject = json.decodeFromString<ApiChapterPagesResponse>(response.body.string())

        if (chapterObject.data?.chapter == null) {
            if (chapterObject.errors != null) {
                val errors = chapterObject.errors.joinToString("\n") { it.message }
                throw Exception(errors)
            }
            throw Exception("Unknown error while processing pages")
        }

        val pages = json.decodeFromString<ApiChapterPages>(chapterObject.data.chapter.pages)

        return pages.i.mapIndexed { i, page ->
            Page(i, "", "$baseCdnUrl/${pages.p}$page")
        }
    }

    // Image
    override fun imageUrlRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Sec-Fetch-Dest", "image")
            .set("Sec-Fetch-Mode", "no-cors")
            .set("Sec-Fetch-Site", "cross-site")
            .removeAll("Upgrade-Insecure-Requests")
            .build()

        return GET(page.url, newHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // filters
    private class Genre(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class Order(title: String, val key: String) : Filter.TriState(title) {
        override fun toString(): String {
            return name
        }
    }

    private class OrderBy(orders: Array<Order>) : Filter.Select<Order>("Order", orders, 0)
    private class GenreList(genres: Array<Genre>) : Filter.Select<Genre>("Genres", genres, 0)

    override fun getFilterList() = FilterList(
        OrderBy(orderBy),
        GenreList(genres),
    )

    private val orderBy = arrayOf(
        Order("Popular", "POPULAR"),
        Order("Updates", "LATEST"),
        Order("A-Z", "ALPHABET"),
        Order("New", "NEW"),
        Order("Completed", "COMPLETED"),
    )

    private val genres = arrayOf(
        Genre("All Genres", "all"),
        Genre("[no chapters]", "no-chapters"),
        Genre("4-Koma", "4-koma"),
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Award Winning", "award-winning"),
        Genre("Comedy", "comedy"),
        Genre("Cooking", "cooking"),
        Genre("Crime", "crime"),
        Genre("Demons", "demons"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Food", "food"),
        Genre("Game", "game"),
        Genre("Gender bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Kids", "kids"),
        Genre("Magic", "magic"),
        Genre("Magical Girls", "magical-girls"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("One shot", "one-shot"),
        Genre("Oneshot", "oneshot"),
        Genre("Parody", "parody"),
        Genre("Police", "police"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("School life", "school-life"),
        Genre("Sci fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shotacon", "shotacon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo ai", "shoujo-ai"),
        Genre("Shoujoai", "shoujoai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen ai", "shounen-ai"),
        Genre("Shounenai", "shounenai"),
        Genre("Slice of life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Space", "space"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Superhero", "superhero"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Vampire", "vampire"),
        Genre("Webtoon", "webtoon"),
        Genre("Webtoons", "webtoons"),
        Genre("Wuxia", "wuxia"),
        Genre("Yuri", "yuri"),
    )
}
