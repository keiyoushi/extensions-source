package eu.kanade.tachiyomi.multisrc.mangahub

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.GraphQLException
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.get
import keiyoushi.utils.getArray
import keiyoushi.utils.getString
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import java.net.URLEncoder
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

abstract class MangaHub : KeiSource() {

    abstract val mangaSource: String

    private val baseApiUrl get() = "https://api.mghcdn.com"
    private val baseCdnUrl get() = "https://imgx.mghcdn.com"
    private val baseThumbCdnUrl get() = "https://thumb.mghcdn.com"

    override fun OkHttpClient.Builder.configureClient() = addCookie { listOf("mhub_access" to apiKey) }

    private var apiKey: String = client.cookieJar
        .loadForRequest(baseUrl.toHttpUrl())
        .firstOrNull { it.name == "mhub_access" && it.value.isNotEmpty() }?.value
        ?: generateRandomKey()

    private val apiRegex = Regex("mhub_access=([^;]+)")
    private val spaceRegex = Regex("\\s+")
    private val apiErrorRegex = Regex("""rate\s*limit|api\s*key\s*(?:invalid)?|encryption(?:\s*error)?""", RegexOption.IGNORE_CASE)

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

    private suspend inline fun <reified T> fetchGraphQL(
        query: String,
        refreshUrl: String? = null,
    ): T {
        return try {
            apiRequest(graphQLBody(query = query)).parseGraphQLAs<T>()
        } catch (e: GraphQLException) {
            if (!isApiError(e)) throw e

            val oldKey = apiKey
            refreshApiKey(refreshUrl, oldKey)

            if (apiKey != oldKey) {
                try {
                    return apiRequest(graphQLBody(query = query)).parseGraphQLAs<T>()
                } catch (e2: GraphQLException) {
                    if (!isApiError(e2)) throw e2
                }
            }

            apiKey = generateRandomKey()
            apiRequest(graphQLBody(query = query)).parseGraphQLAs<T>()
        }
    }

    private fun isApiError(e: GraphQLException): Boolean = apiErrorRegex.containsMatchIn(e.message.orEmpty())

    private fun generateRandomKey(): String = Random.nextBytes(16).toHexString()

    private suspend fun apiRequest(body: RequestBody): Response {
        val requestHeaders = apiHeaders
            .set("x-mhub-access", apiKey)
            .build()

        return client.post("$baseApiUrl/graphql", requestHeaders, body)
    }

    private val refreshMutex = Mutex()

    private suspend fun refreshApiKey(refreshUrl: String?, oldKey: String) = refreshMutex.withLock {
        if (apiKey != oldKey) return@withLock

        val url = refreshUrl?.toHttpUrl()
            ?: "$baseUrl/chapter/martial-peak/chapter-${Random.nextInt(1000, 3000)}".toHttpUrl()

        val refreshHeaders = headersBuilder()
            .set("Referer", "$baseUrl/manga/${url.pathSegments[1]}")
            .build()

        for (i in 1..2) {
            val query = if (i == 1) "?reloadKey=1" else ""
            val response = client.get("$url$query", refreshHeaders, ensureSuccess = false)
            val returnedKey = response.headers("Set-Cookie")
                .firstNotNullOfOrNull { apiRegex.find(it)?.groupValues?.get(1) }
            response.close()

            if (!returnedKey.isNullOrEmpty() && returnedKey != oldKey) {
                apiKey = returnedKey
                return@withLock
            }
        }
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
        val rows = fetchGraphQL<ApiSearchObject>(searchQuery(mangaSource, query, genres, order, page))
            .search!!.rows

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

        return fetchGraphQL<ApiMangaObject>(
            mangaQuery(mangaSource, slug),
            refreshUrl = "$baseUrl/manga/$slug",
        ).manga!!
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
        val data = fetchGraphQL<ApiMangaObject>(
            mangaQuery(mangaSource, slug),
            refreshUrl = "$baseUrl${manga.url}",
        ).manga!!

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
            date_upload = Instant.tryParse(it.date)
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

        val chapterObject = fetchGraphQL<ApiChapterData>(
            pagesQuery(mangaSource, slug, number),
            refreshUrl = "$baseUrl/chapter${chapter.url}",
        ).chapter!!

        val pagesString = if (chapterObject.pages.startsWith("enc:v1")) {
            decryptPages(chapterObject.pages)
        } else {
            chapterObject.pages
        }

        // We'll update the cookie here to match the browser's "recently" opened chapter.
        // This mimics how the browser works and gives us more chance to receive a valid API key upon refresh
        val now = Clock.System.now()
        val baseHttpUrl = baseUrl.toHttpUrl()
        val recently = buildJsonObject {
            putJsonObject(now.toEpochMilliseconds().toString()) {
                put("mangaID", chapterObject.mangaID)
                put("number", chapterObject.chapterNumber)
            }
        }.toString()

        val recentlyCookie = Cookie.Builder()
            .domain(baseHttpUrl.host)
            .name("recently")
            .value(URLEncoder.encode(recently, "utf-8"))
            .expiresAt((now + 60.days).toEpochMilliseconds())
            .build()

        client.cookieJar.saveFromResponse(baseHttpUrl, listOf(recentlyCookie))

        val pageUrls: List<String> = when (val jsonPages = pagesString.parseAs<JsonElement>()) {
            is JsonObject if "i" in jsonPages -> {
                val prefix = jsonPages.getString("p")
                jsonPages.getArray("i").map { "$prefix${it.string}" }
            }
            is JsonArray -> {
                jsonPages.map { it.string }
            }
            is JsonObject -> {
                jsonPages.values.map { it.string }
            }
            else -> {
                emptyList()
            }
        }

        return pageUrls.mapIndexed { i, path ->
            val url = if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                "$baseCdnUrl/${path.removePrefix("/")}"
            }
            Page(i, imageUrl = url)
        }
    }

    private suspend fun decryptPages(pages: String): String {
        val cryptoParams = client.get("$baseUrl/api/chapter-crypto", apiHeaders.build())
            .parseAs<ChapterCryptoDto>()

        val parts = pages.split(":")
        val keyId = parts[2]
        val iv = parts[3]
        val authTag = parts[4]
        val ciphertext = parts[5]

        val keyData = cryptoParams.keys?.get(keyId)
            ?: cryptoParams.key?.takeIf { cryptoParams.keyId == null || cryptoParams.keyId == keyId }
            ?: throw GeneralSecurityException("Key not found for keyId: $keyId")
        val keyBytes = Base64.decode(keyData, Base64.URL_SAFE)
        val ivBytes = Base64.decode(iv, Base64.URL_SAFE)
        val authTagBytes = Base64.decode(authTag, Base64.URL_SAFE)
        val cipherBytes = Base64.decode(ciphertext, Base64.URL_SAFE)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, ivBytes))
        }

        return cipher.doFinal(cipherBytes + authTagBytes).decodeToString()
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
