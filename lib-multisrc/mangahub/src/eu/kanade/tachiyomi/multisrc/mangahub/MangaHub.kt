package eu.kanade.tachiyomi.multisrc.mangahub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.GraphQLException
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

abstract class MangaHub : KeiSource() {

    abstract val mangaSource: String

    private val baseApiUrl get() = "https://api.mghcdn.com"
    private val baseCdnUrl get() = "https://imgx.mghcdn.com"
    private val baseThumbCdnUrl get() = "https://thumb.mghcdn.com"
    private val apiRegex = Regex("mhub_access=([^;]+)")
    private val spaceRegex = Regex("\\s+")
    private val apiErrorRegex = Regex("""rate\s*limit|api\s*key""")

    override fun Headers.Builder.configureHeaders() = this
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
        .set("Accept-Language", "en-US,en;q=0.5")
        .set("DNT", "1")
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "same-origin")
        .set("Upgrade-Insecure-Requests", "1")

    private val apiHeaders get() = headersBuilder()
        .set("Accept", "application/json")
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Site", "cross-site")
        .removeAll("Upgrade-Insecure-Requests")

    private fun accessCookie(): Cookie? = client.cookieJar
        .loadForRequest(baseUrl.toHttpUrl())
        .firstOrNull { it.name == "mhub_access" && it.value.isNotEmpty() }

    private suspend fun <T> fetchGraphQL(
        query: String,
        refreshUrl: String? = null,
        parse: (Response) -> T,
    ): T {
        val body = graphQLBody(query = query)

        return try {
            parse(apiRequest(body))
        } catch (e: Throwable) {
            val shouldRefresh = e is MangaHubCookieNotFound ||
                (e is GraphQLException && apiErrorRegex.containsMatchIn(e.message ?: ""))

            if (!shouldRefresh) throw e

            refreshApiKey(refreshUrl)
            parse(apiRequest(body))
        }
    }

    private suspend fun apiRequest(body: RequestBody): Response {
        val cookie = accessCookie() ?: throw MangaHubCookieNotFound()

        val requestHeaders = apiHeaders
            .set("x-mhub-access", cookie.value)
            .build()

        return client.post("$baseApiUrl/graphql", requestHeaders, body)
    }

    private class MangaHubCookieNotFound : IOException("mhub_access cookie not found")

    private val refreshMutex = Mutex()
    private var lastRefresh = 0L

    private suspend fun refreshApiKey(refreshUrl: String? = null) = refreshMutex.withLock {
        if (System.currentTimeMillis() - lastRefresh < 10_000) return@withLock

        val url = refreshUrl?.toHttpUrl()
            ?: "$baseUrl/chapter/martial-peak/chapter-${Random.nextInt(1000, 3000)}".toHttpUrl()
        val oldKey = accessCookie()?.value

        val refreshHeaders = headersBuilder()
            .set("Referer", "$baseUrl/manga/${url.pathSegments[1]}")
            .build()

        for (i in 1..2) {
            val cookie = Cookie.parse(url, "mhub_access=; Max-Age=0; Path=/")!!
            client.cookieJar.saveFromResponse(url, listOf(cookie))

            val query = if (i == 2) "?reloadKey=1" else ""
            val response = try {
                client.get("$url$query", refreshHeaders, ensureSuccess = false)
            } catch (_: Throwable) {
                throw Exception("An error occurred while obtaining a new API key")
            }
            val returnedKey = response.headers["set-cookie"]
                ?.let { apiRegex.find(it)?.groupValues?.get(1) }
            response.close()

            if (returnedKey != oldKey) break // Got an allegedly valid API key
        }

        lastRefresh = System.currentTimeMillis()
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, order = "POPULAR")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, order = "LATEST")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var order = "POPULAR"
        var genres = "all"

        filters.forEach { filter ->
            when (filter) {
                is OrderBy -> order = filter.values[filter.state].key
                is GenreList -> genres = filter.included.joinToString(",").takeIf { it.isNotBlank() } ?: "all"
                else -> {}
            }
        }

        return getMangaList(page, order, query, genres)
    }

    private suspend fun getMangaList(page: Int, order: String, query: String = "", genres: String = "all"): MangasPage {
        val rows = fetchGraphQL(searchQuery(mangaSource, query, genres, order, page)) {
            it.parseGraphQLAs<ApiSearchObject>()
        }.search!!.rows

        val mangas = rows.map {
            SManga.create().apply {
                url = "/manga/${it.slug}"
                title = it.title
                thumbnail_url = it.image?.takeIf(String::isNotBlank)?.let { image -> "$baseThumbCdnUrl/$image" }
            }
        }

        return MangasPage(mangas, rows.size == 30)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val slug = when (url.pathSegments.firstOrNull()) {
            "manga", "chapter" -> url.pathSegments.getOrNull(1)
            else -> null
        }?.takeIf { it.isNotEmpty() } ?: return null

        return fetchGraphQL(
            mangaQuery(mangaSource, slug),
            refreshUrl = "$baseUrl/manga/$slug",
        ) { it.parseGraphQLAs<ApiMangaObject>() }.manga!!
            .toSManga()
            .apply { this.url = "/manga/$slug" }
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val mangaGenres = manga.genre
            ?.split(",")
            ?.map { it.trim().lowercase() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: return emptyList()

        val filters = getFilterList()
        filters.firstInstanceOrNull<GenreList>()?.apply {
            state
                .filter { it.name.lowercase() in mangaGenres }
                .forEach { it.state = true }
        } ?: return emptyList()
        filters.firstInstance<OrderBy>().apply { state = Random.nextInt(orderBy.size) }

        return getSearchMangaList(page = 1, query = "", filters = filters).mangas
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.removePrefix("/manga/")
        val data = fetchGraphQL(
            mangaQuery(mangaSource, slug),
            refreshUrl = "$baseUrl${manga.url}",
        ) { it.parseGraphQLAs<ApiMangaObject>() }.manga!!

        return SMangaUpdate(
            manga = data.toSManga(),
            chapters = data.toChapterList(),
        )
    }

    private fun ApiMangaData.toSManga() = SManga.create().apply {
        title = this@toSManga.title!!
        author = this@toSManga.author
        artist = this@toSManga.artist
        genre = genres
        thumbnail_url = image?.takeIf(String::isNotBlank)?.let { image -> "$baseThumbCdnUrl/$image" }
        status = when (this@toSManga.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        description = buildString {
            this@toSManga.description?.let(::append)

            val altTitles = alternativeTitle
                ?.split(";")
                ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .orEmpty()

            if (altTitles.isNotEmpty()) {
                if (isNotBlank()) append("\n\n")
                append("Alternative Names:\n")
                append(altTitles.joinToString("\n") { "- $it" })
            }
        }
    }

    private fun ApiMangaData.toChapterList(): List<SChapter> = chapters!!.map {
        SChapter.create().apply {
            val numberString = it.number.toString().removeSuffix(".0")

            name = generateChapterName(it.title.trim().replace(spaceRegex, " "), numberString)
            url = "/$slug/chapter-${it.number}"
            chapter_number = it.number
            date_upload = Instant.parseOrNull(it.date)?.toEpochMilliseconds() ?: 0L
        }
    }.asReversed()

    private fun generateChapterName(title: String, number: String): String = when {
        title.contains(number) -> title
        title.isNotBlank() -> "Chapter $number - $title"
        else -> "Chapter $number"
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter${chapter.url}"

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, number) = chapter.url.split("/").let {
            it[1] to it[2].substringAfter("-").toFloat()
        }

        val chapterObject = fetchGraphQL(
            pagesQuery(mangaSource, slug, number),
            refreshUrl = "$baseUrl/chapter${chapter.url}",
        ) { it.parseGraphQLAs<ApiChapterData>() }.chapter!!
        val pages = chapterObject.pages.parseAs<ApiChapterPages>()

        // We'll update the cookie here to match the browser's "recently" opened chapter.
        // This mimics how the browser works and gives us more chance to receive a valid API key upon refresh
        val now = System.currentTimeMillis()
        val baseHttpUrl = baseUrl.toHttpUrl()
        val recently = buildJsonObject {
            putJsonObject(now.toString()) {
                put("mangaID", chapterObject.mangaID)
                put("number", chapterObject.chapterNumber)
            }
        }.toString()

        val recentlyCookie = Cookie.Builder()
            .domain(baseHttpUrl.host)
            .name("recently")
            .value(URLEncoder.encode(recently, "utf-8"))
            .expiresAt(now.plus(60.days.inWholeMilliseconds))
            .build()

        client.cookieJar.saveFromResponse(baseHttpUrl, listOf(recentlyCookie))

        // Best-effort logging to further increase the chance of a valid API key
        logChapterView(slug, chapterObject.chapterNumber)

        return pages.images.mapIndexed { i, page ->
            Page(i, imageUrl = "$baseCdnUrl/${pages.page}$page")
        }
    }

    // Mimics the browser logging a chapter view
    private fun logChapterView(slug: String, chapterNumber: Float) {
        GET("https://api.ipify.org?format=json").enqueue { ipResponse ->
            val ip = ipResponse.parseAs<PublicIPResponse>().ip
            GET("$baseUrl/action/logHistory2/$slug/$chapterNumber?browserID=$ip", headers).enqueue()
        }
    }

    private fun Request.enqueue(onResponse: (Response) -> Unit = Response::close) {
        client.newCall(this).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                try {
                    onResponse(response)
                } catch (_: Throwable) {
                    response.close()
                }
            }
        })
    }

    // Filters
    private val orderBy = arrayOf(
        Order("Popular", "POPULAR"),
        Order("Updates", "LATEST"),
        Order("A-Z", "ALPHABET"),
        Order("New", "NEW"),
        Order("Completed", "COMPLETED"),
    )

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/search").asJsoup()

        return document.select("a.genre-label")
            .map { GenreDto(it.text(), it.attr("href").substringAfterLast("/")) }
            .distinctBy { it.key }
            .sortedBy { it.name }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()
            ?.map { Genre(it.name, it.key) }
            .orEmpty()

        return FilterList(
            buildList {
                if (genres.isNotEmpty()) {
                    add(GenreList(genres))
                }
                add(OrderBy(orderBy))
            },
        )
    }
}
