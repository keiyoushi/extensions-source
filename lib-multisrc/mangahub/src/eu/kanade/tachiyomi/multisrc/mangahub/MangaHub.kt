package eu.kanade.tachiyomi.multisrc.mangahub

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.randomua.UserAgentType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class MangaHub(
    override val name: String,
    final override val baseUrl: String,
    override val lang: String,
    private val mangaSource: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH),
) : HttpSource(), ConfigurableSource {

    override val supportsLatest = true

    private val baseApiUrl = "https://api.mghcdn.com"
    private val baseCdnUrl = "https://imgx.mghcdn.com"
    private val baseThumbCdnUrl = "https://thumb.mghcdn.com"
    private val regex = Regex("mhub_access=([^;]+)")

    private val preferences: SharedPreferences by getPreferencesLazy()

    private fun SharedPreferences.getUseGenericTitlePref(): Boolean = getBoolean(
        PREF_USE_GENERIC_TITLE,
        false,
    )

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .setRandomUserAgent(
            userAgentType = UserAgentType.DESKTOP,
            filterInclude = listOf("chrome"),
        )
        .addInterceptor(::apiAuthInterceptor)
        .addInterceptor(::graphQLApiInterceptor)
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

    private fun postRequestGraphQL(query: String): Request {
        val requestHeaders = headersBuilder()
            .set("Accept", "application/json")
            .set("Content-Type", "application/json")
            .set("Origin", baseUrl)
            .set("Sec-Fetch-Dest", "empty")
            .set("Sec-Fetch-Mode", "cors")
            .set("Sec-Fetch-Site", "cross-site")
            .removeAll("Upgrade-Insecure-Requests")
            .build()

        val body = buildJsonObject {
            put("query", query)
        }

        return POST("$baseApiUrl/graphql", requestHeaders, body.toString().toRequestBody())
            .newBuilder()
            .tag(GraphQLTag())
            .build()
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

    private fun graphQLApiInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // We won't intercept non-graphql requests (like image retrieval)
        if (!request.hasGraphQLTag()) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)

        // We don't care about the data, only the possible error associated with it
        // If we encounter an error, we'll intercept it and throw an error for app to catch
        val apiResponse = response.peekBody(Long.MAX_VALUE).string().parseAs<ApiResponseError>()
        if (apiResponse.errors != null) {
            response.close() // Avoid leaks
            val errors = apiResponse.errors.joinToString("\n") { it.message }
            throw IOException(errors)
        }

        // Everything works fine
        return response
    }

    private fun Request.hasGraphQLTag(): Boolean {
        return this.tag() is GraphQLTag
    }

    private fun refreshApiKey(chapter: SChapter) {
        val now = Calendar.getInstance().time.time

        val url = "$baseUrl/chapter${chapter.url}".toHttpUrl()
        val oldKey = client.cookieJar
            .loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == "mhub_access" && it.value.isNotEmpty() }?.value

        // With the recent changes on how refresh API token works, we are now apparently required to have
        // a cookie for recently when requesting for a new one. Not having this will result in a hit or miss.
        val recently = buildJsonObject {
            putJsonObject((now - (0..3600).random()).toString()) {
                put("mangaID", (1..42_000).random())
                put("number", (1..20).random())
            }
        }.toString()

        val recentlyCookie = Cookie.Builder()
            .domain(url.host)
            .name("recently")
            .value(URLEncoder.encode(recently, "utf-8"))
            .expiresAt(now + 2 * 60 * 60 * 24 * 31) // +2 months
            .build()

        for (i in 1..2) {
            // Clear key cookie
            val cookie = Cookie.parse(url, "mhub_access=; Max-Age=0; Path=/")!!
            client.cookieJar.saveFromResponse(url, listOf(cookie, recentlyCookie))

            // We try requesting again with param if the first one fails
            val query = if (i == 2) "?reloadKey=1" else ""

            try {
                val response = client.newCall(
                    GET(
                        "$url$query",
                        headers.newBuilder()
                            .set("Referer", "$baseUrl/manga/${url.pathSegments[1]}")
                            .build(),
                    ),
                ).execute()
                val returnedKey = response.headers["set-cookie"]?.let { regex.find(it)?.groupValues?.get(1) }
                response.close() // Avoid potential resource leaks

                if (returnedKey != oldKey) break // Break out of loop since we got an allegedly valid API key
            } catch (_: IOException) {
                throw IOException("An error occurred while obtaining a new API key") // Show error
            }
        }

        // Sometimes, the new API key is still invalid. To ensure that the token will be fresh and available to use,
        // we have to mimic how the browser site works. To put it simply, we will send a GET request that indicates what
        // manga and chapter were browsing. If this succeeded, the API key that we use will be revalidated (assuming that we got an expired one.)
        // We first need to obtain our public IP first since it is required as a query.
        val ipRequest = client.newCall(GET("https://api.ipify.org?format=json")).execute()
        val ip = ipRequest.parseAs<PublicIPResponse>().ip

        // We'll log our action to the site to revalidate the API key in case we got an expired one
        client.newCall(GET("$baseUrl/action/logHistory2/${url.pathSegments[1]}/${chapter.chapter_number}?browserID=$ip")).execute()
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

    private fun mangaRequest(page: Int, order: String): Request {
        return postRequestGraphQL(searchQuery(mangaSource, "", "all", order, page))
    }

    // popular
    override fun popularMangaRequest(page: Int): Request = mangaRequest(page, "POPULAR")

    // often enough there will be nearly identical entries with slightly different
    // titles, URLs, and image names. in order to cut these "duplicates" down,
    // assign a "signature" based on author name, chapter number, and genres
    // if all of those are the same, then it it's the same manga
    override fun popularMangaParse(response: Response): MangasPage {
        val mangaList = response.parseAs<ApiSearchResponse>()

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

        // Entries have a max of 30 per request
        return MangasPage(mangas, mangaList.data.search.rows.count() == 30)
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return mangaRequest(page, "LATEST")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var order = "POPULAR"
        var genres = "all"

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    order = filter.values[filter.state].key
                }
                is GenreList -> {
                    genres = filter.included.joinToString(",").takeIf { it.isNotBlank() } ?: "all"
                }
                else -> {}
            }
        }

        return postRequestGraphQL(searchQuery(mangaSource, query, genres, order, page))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // manga details
    override fun mangaDetailsRequest(manga: SManga): Request {
        return postRequestGraphQL(mangaDetailsQuery(mangaSource, manga.url.removePrefix("/manga/")))
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val rawManga = response.parseAs<ApiMangaDetailsResponse>()

        return SManga.create().apply {
            title = rawManga.data.manga.title!!
            author = rawManga.data.manga.author
            artist = rawManga.data.manga.artist
            genre = rawManga.data.manga.genres
            thumbnail_url = "$baseThumbCdnUrl/${rawManga.data.manga.image}"
            status = when (rawManga.data.manga.status) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            description = buildString {
                rawManga.data.manga.description?.let(::append)

                // Add alternative title
                val altTitle = rawManga.data.manga.alternativeTitle
                if (!altTitle.isNullOrBlank()) {
                    if (isNotBlank()) append("\n\n")
                    append("Alternative Name: $altTitle")
                }
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        return postRequestGraphQL(mangaChapterListQuery(mangaSource, manga.url.removePrefix("/manga/")))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList = response.parseAs<ApiMangaDetailsResponse>()
        val useGenericTitle = preferences.getUseGenericTitlePref()

        return chapterList.data.manga.chapters!!.map {
            SChapter.create().apply {
                val numberString = "${if (it.number % 1 == 0f) it.number.toInt() else it.number}"

                name = if (!useGenericTitle) {
                    generateChapterName(it.title.trim().replace("\n", " "), numberString)
                } else {
                    generateGenericChapterName(numberString)
                }

                url = "/${chapterList.data.manga.slug}/chapter-${it.number}"
                chapter_number = it.number
                date_upload = dateFormat.tryParse(it.date)
            }
        }.reversed() // The response is sorted in ASC format so we need to reverse it
    }

    private fun generateChapterName(title: String, number: String): String {
        return if (title.contains(number)) {
            title
        } else if (title.isNotBlank()) {
            "Chapter $number - $title"
        } else {
            generateGenericChapterName(number)
        }
    }

    private fun generateGenericChapterName(number: String): String {
        return "Chapter $number"
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter${chapter.url}"

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url.split("/")

        return postRequestGraphQL(pagesQuery(mangaSource, chapterUrl[1], chapterUrl[2].substringAfter("-").toFloat()))
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        super.fetchPageList(chapter)
            .doOnError { refreshApiKey(chapter) }
            .retry(1)

    override fun pageListParse(response: Response): List<Page> {
        val chapterObject = response.parseAs<ApiChapterPagesResponse>()
        val pages = chapterObject.data.chapter.pages.parseAs<ApiChapterPages>()

        return pages.images.mapIndexed { i, page ->
            Page(i, "", "$baseCdnUrl/${pages.page}$page")
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
    private class Genre(title: String, val key: String) : Filter.CheckBox(title) {
        fun getGenreKey(): String {
            return key
        }

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
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres) {
        val included: List<String>
            get() = state.filter { it.state }.map { it.getGenreKey() }
    }

    override fun getFilterList() = FilterList(
        GenreList(genres),
        OrderBy(orderBy),
    )

    private val orderBy = arrayOf(
        Order("Popular", "POPULAR"),
        Order("Updates", "LATEST"),
        Order("A-Z", "ALPHABET"),
        Order("New", "NEW"),
        Order("Completed", "COMPLETED"),
    )

    private val genres = listOf(
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Adult", "adult"),
        Genre("Drama", "drama"),
        Genre("Historical", "historical"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Romance", "romance"),
        Genre("Ecchi", "ecchi"),
        Genre("Supernatural", "supernatural"),
        Genre("Webtoons", "webtoons"),
        Genre("Manhwa", "manhwa"),
        Genre("Fantasy", "fantasy"),
        Genre("Harem", "harem"),
        Genre("Shounen", "shounen"),
        Genre("Manhua", "manhua"),
        Genre("Mature", "mature"),
        Genre("Seinen", "seinen"),
        Genre("Sports", "sports"),
        Genre("School Life", "school-life"),
        Genre("Smut", "smut"),
        Genre("Mystery", "mystery"),
        Genre("Psychological", "psychological"),
        Genre("Shounen ai", "shounen-ai"),
        Genre("Slice of life", "slice-of-life"),
        Genre("Shoujo ai", "shoujo-ai"),
        Genre("Cooking", "cooking"),
        Genre("Horror", "horror"),
        Genre("Tragedy", "tragedy"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Yuri", "yuri"),
        Genre("Yaoi", "yaoi"),
        Genre("Shoujo", "shoujo"),
        Genre("Gender bender", "gender-bender"),
        Genre("Josei", "josei"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Magic", "magic"),
        Genre("4-Koma", "4-koma"),
        Genre("Music", "music"),
        Genre("Webtoon", "webtoon"),
        Genre("Isekai", "isekai"),
        Genre("Game", "game"),
        Genre("Award Winning", "award-winning"),
        Genre("Oneshot", "oneshot"),
        Genre("Demons", "demons"),
        Genre("Military", "military"),
        Genre("Police", "police"),
        Genre("Super Power", "super-power"),
        Genre("Food", "food"),
        Genre("Kids", "kids"),
        Genre("Magical Girls", "magical-girls"),
        Genre("Wuxia", "wuxia"),
        Genre("Superhero", "superhero"),
        Genre("Thriller", "thriller"),
        Genre("Crime", "crime"),
        Genre("Philosophical", "philosophical"),
        Genre("Adaptation", "adaptation"),
        Genre("Full Color", "full-color"),
        Genre("Crossdressing", "crossdressing"),
        Genre("Reincarnation", "reincarnation"),
        Genre("Manga", "manga"),
        Genre("Cartoon", "cartoon"),
        Genre("Survival", "survival"),
        Genre("Comic", "comic"),
        Genre("English", "english"),
        Genre("Harlequin", "harlequin"),
        Genre("Time Travel", "time-travel"),
        Genre("Traditional Games", "traditional-games"),
        Genre("Reverse Harem", "reverse-harem"),
        Genre("Animals", "animals"),
        Genre("Aliens", "aliens"),
        Genre("Loli", "loli"),
        Genre("Video Games", "video-games"),
        Genre("Monsters", "monsters"),
        Genre("Office Workers", "office-workers"),
        Genre("system", "system"),
        Genre("Villainess", "villainess"),
        Genre("Zombies", "zombies"),
        Genre("Vampires", "vampires"),
        Genre("Violence", "violence"),
        Genre("Monster Girls", "monster-girls"),
        Genre("Anthology", "anthology"),
        Genre("Ghosts", "ghosts"),
        Genre("Delinquents", "delinquents"),
        Genre("Post-Apocalyptic", "post-apocalyptic"),
        Genre("Xianxia", "xianxia"),
        Genre("Xuanhuan", "xuanhuan"),
        Genre("R-18", "r-18"),
        Genre("Cultivation", "cultivation"),
        Genre("Rebirth", "rebirth"),
        Genre("Gore", "gore"),
        Genre("Russian", "russian"),
        Genre("Samurai", "samurai"),
        Genre("Ninja", "ninja"),
        Genre("Revenge", "revenge"),
        Genre("Cheat Systems", "cheat-systems"),
        Genre("Dungeons", "dungeons"),
        Genre("Overpowered", "overpowered"),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_USE_GENERIC_TITLE
            title = "Use generic title"
            summary = "Use generic chapter title (\"Chapter 'x'\") instead of the given one.\nNote: May require manga entry to be refreshed."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_USE_GENERIC_TITLE = "pref_use_generic_title"
    }
}
