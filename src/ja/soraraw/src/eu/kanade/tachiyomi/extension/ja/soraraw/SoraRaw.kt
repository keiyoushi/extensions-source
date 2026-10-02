package eu.kanade.tachiyomi.extension.ja.soraraw

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class SoraRaw : HttpSource() {
    override val name = "SoraRaw"
    override val baseUrl = "https://soraraw.com"
    override val lang = "ja"
    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .addInterceptor(::buildIdOutdatedInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private var buildId: String = ""

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val pageApiUrl = "https://api.mangarawgo.site"

    private fun <T> withBuildId(observableFactory: () -> Observable<T>): Observable<T> {
        if (buildId.isNotBlank()) return observableFactory()

        return client.newCall(GET("$baseUrl/", headers)).asObservableSuccess()
            .flatMap { response ->
                response.use {
                    buildId = fetchBuildId(it.asJsoup())
                }
                observableFactory()
            }
    }

    // ============================ Database Builder ============================

    private var genreMap: Map<String, String>? = null

    private fun getGenreMap(): Map<String, String> {
        if (genreMap != null) return genreMap!!

        try {
            val request = GET("$baseUrl/genres.json", headers)
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val map = response.parseAs<List<GenreItemDto>>()
                        .mapNotNull { genre ->
                            val id = genre.id ?: genre.value ?: return@mapNotNull null
                            val name = genre.name ?: genre.label ?: return@mapNotNull null
                            id.toString() to name
                        }
                        .toMap()

                    genreMap = map
                    return map
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyMap()
    }

    private var searchDatabase: List<MangaDto>? = null
    private val searchPageSize = 24

    @Synchronized
    private fun getSearchDatabaseObservable(): Observable<List<MangaDto>> {
        searchDatabase?.let { return Observable.just(it) }

        return Observable.create { subscriber ->
            val mangas = mutableListOf<MangaDto>()
            val seenSlugs = mutableSetOf<String>()

            try {
                var page = 1
                while (!subscriber.isUnsubscribed) {
                    val response = client.newCall(GET("$baseUrl/mangas_$page.json", headers)).execute()

                    if (response.code == 404) {
                        response.close()
                        break
                    }
                    if (!response.isSuccessful) {
                        response.close()
                        throw java.io.IOException("HTTP error ${response.code}")
                    }

                    val dto = response.parseAs<MangaListDto>()
                    if (dto.list.isEmpty()) break

                    dto.list.forEach { manga ->
                        if (seenSlugs.add(manga.slug)) {
                            mangas.add(manga)
                        }
                    }

                    page++
                }

                searchDatabase = mangas
                subscriber.onNext(mangas)
                subscriber.onCompleted()
            } catch (e: Exception) {
                subscriber.onError(e)
            }
        }
    }

    // ============================== Filters ========================================

    override fun getFilterList() = FilterList(
        ContentFilter(),
        ModeFilter(),
        StatusFilter(),
        SortFilter(),
        GenreFilter(),
    )

    // ============================== Popular / Latest ===============================

    private fun parseNextDataMangas(response: Response): MangasPage {
        if (!response.isSuccessful) {
            throw java.io.IOException("HTTP error ${response.code}")
        }

        return parseNextDataMangas(response.body.string())
    }

    private fun parseNextDataMangas(jsonString: String): MangasPage {
        val element = jsonInstance.parseToJsonElement(jsonString)

        val mangas = mutableListOf<SManga>()
        val gMap = getGenreMap()

        var hasNextPage = false

        fun extractMangas(node: JsonElement) {
            if (node is kotlinx.serialization.json.JsonArray) {
                for (item in node) {
                    extractMangas(item)
                }
            } else if (node is JsonObject) {
                if (node.containsKey("total") && node.containsKey("current_page") && node.containsKey("total_page")) {
                    val current = node["current_page"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                    val total = node["total_page"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                    if (current < total) {
                        hasNextPage = true
                    }
                }

                if (node.containsKey("slug") && node.containsKey("id") && (node.containsKey("name") || node.containsKey("title"))) {
                    val slug = node["slug"]?.jsonPrimitive?.content ?: return
                    val name = node["name"]?.jsonPrimitive?.content ?: node["title"]?.jsonPrimitive?.content ?: return

                    mangas.add(
                        SManga.create().apply {
                            url = slug
                            title = name

                            val imgName = node["image"]?.jsonPrimitive?.content ?: node["img"]?.jsonPrimitive?.content
                            thumbnail_url = node["thumbnail"]?.jsonPrimitive?.content ?: if (imgName != null) {
                                "https://i.mangaraw.lat/$imgName"
                            } else {
                                node["cover_url"]?.jsonPrimitive?.content ?: node["cover"]?.jsonPrimitive?.content ?: ""
                            }

                            genre = node["genres"]?.let { genresNode ->
                                if (genresNode is kotlinx.serialization.json.JsonArray) {
                                    genresNode.mapNotNull { element ->
                                        if (element is JsonPrimitive) {
                                            if (element.isString) {
                                                element.content
                                            } else {
                                                gMap[element.content] ?: "Genre ${element.content}"
                                            }
                                        } else if (element is JsonObject) {
                                            element["name"]?.let { if (it is JsonPrimitive) it.content else null }
                                        } else {
                                            null
                                        }
                                    }.joinToString(", ")
                                } else {
                                    null
                                }
                            }
                        },
                    )
                } else {
                    for (value in node.values) {
                        extractMangas(value)
                    }
                }
            }
        }

        extractMangas(element)
        val uniqueMangas = mangas.distinctBy { it.url }

        // Fallback for pagination if not explicitly found in JSON but we got a full chunk
        if (!hasNextPage && uniqueMangas.size >= 24) {
            hasNextPage = true
        }

        return MangasPage(uniqueMangas, hasNextPage)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        if (page > 1) return Observable.just(MangasPage(emptyList(), false))
        return client.newCall(GET("$baseUrl/top/last7Days.json", headers)).asObservableSuccess()
            .map { response ->
                val dto = response.parseAs<TopMangasDto>()
                val gMap = getGenreMap()
                val mangas = dto.mangas.map { it.toSManga(gMap) }.distinctBy { it.url }
                MangasPage(mangas, false)
            }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = withBuildId {
        val path = if (page == 1) "newest.json" else "newest/page/$page.json?page=$page"
        val request = GET("$baseUrl/_next/data/$buildId/$path", headers)
        client.newCall(request).asObservableSuccess().map { parseNextDataMangas(it) }
    }

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.startsWith(SEARCH_PREFIX)) {
            val slug = query.substringAfter(SEARCH_PREFIX)

            return withBuildId {
                val request = mangaDetailsRequest(SManga.create().apply { url = slug })
                client.newCall(request).asObservableSuccess().map { detailsResponse ->
                    val details = mangaDetailsParse(detailsResponse)
                    MangasPage(listOf(details), false)
                }
            }
        }

        return getSearchDatabaseObservable().map { list ->
            val gMap = getGenreMap()
            val filtered = list
                .asSequence()
                .filter { manga -> manga.matchesQuery(query) }
                .filter { manga -> manga.matchesFilters(filters, gMap) }
                .toList()
                .sortFor(filters)

            val fromIndex = (page - 1) * searchPageSize
            val toIndex = minOf(fromIndex + searchPageSize, filtered.size)
            val mangas = if (fromIndex < filtered.size) {
                filtered.subList(fromIndex, toIndex).map { it.toSManga(gMap) }
            } else {
                emptyList()
            }

            MangasPage(mangas, toIndex < filtered.size)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    private fun MangaDto.matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true

        return name.contains(query, ignoreCase = true) ||
            altNames?.contains(query, ignoreCase = true) == true ||
            author?.contains(query, ignoreCase = true) == true ||
            artist?.contains(query, ignoreCase = true) == true
    }

    private fun MangaDto.matchesFilters(filters: FilterList, gMap: Map<String, String>): Boolean {
        filters.forEach { filter ->
            when (filter) {
                is ContentFilter -> when (filter.state) {
                    1 -> if (isAdult == "yes") return false
                    2 -> if (isAdult != "yes") return false
                }
                is ModeFilter -> when (filter.state) {
                    1 -> if (mode != "vertical") return false
                    2 -> if (mode != "horizontal") return false
                }
                is StatusFilter -> when (filter.state) {
                    1 -> if (type != "incomplete" && status != "ongoing") return false
                    2 -> if (type !in listOf("complete", "completed") && status != "completed") return false
                }
                is GenreFilter -> {
                    if (filter.state != 0) {
                        val genreName = filter.values[filter.state]
                        val genreId = gMap.entries.firstOrNull { it.value == genreName }?.key
                        if (genreId != null && genres?.any { it.matchesGenre(genreId, genreName, gMap) } != true) {
                            return false
                        }
                    }
                }
                else -> {}
            }
        }

        return true
    }

    private fun JsonElement.matchesGenre(
        genreId: String,
        genreName: String,
        gMap: Map<String, String>,
    ): Boolean = when (this) {
        is JsonPrimitive -> content == genreId || content == genreName || gMap[content] == genreName
        is JsonObject -> this["name"]?.jsonPrimitive?.contentOrNull == genreName ||
            this["id"]?.jsonPrimitive?.contentOrNull == genreId
        else -> false
    }

    private fun List<MangaDto>.sortFor(filters: FilterList): List<MangaDto> {
        val sortFilter = filters.filterIsInstance<SortFilter>().firstOrNull()

        return when (sortFilter?.state) {
            1 -> sortedByDescending { it.updatedAt ?: it.cPublishedAt ?: it.cPublished ?: "" }
            2 -> sortedByDescending { it.numberBookmark ?: 0 }
            else -> sortedByDescending { it.views ?: 0 }
        }
    }

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url.substringAfter("/manga/")}"

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = withBuildId {
        super.fetchMangaDetails(manga)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/manga/")
        val nextJsonUrl = "$baseUrl/_next/data/$buildId/manga/$slug.json"
        return GET(nextJsonUrl, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        if (!response.isSuccessful) {
            throw java.io.IOException("HTTP error ${response.code}")
        }
        val dto = response.parseAs<NextDataWrapperDto<MangaDetailsDto>>()
        val mangaDto = dto.pageProps.data.manga
        val gMap = getGenreMap()

        return SManga.create().apply {
            url = mangaDto.slug
            title = mangaDto.name

            var desc = ""
            if (mangaDto.rate != null && mangaDto.rate.jsonPrimitive.content != "null") {
                desc += "★ ${mangaDto.rate.jsonPrimitive.content}"
                if (mangaDto.numberRate != null && mangaDto.numberRate > 0) desc += " (${mangaDto.numberRate})"
                desc += ", "
            }
            if (mangaDto.views != null) desc += "${String.format(Locale.US, "%,d", mangaDto.views).replace(",", " ")}👁\n\n"

            if (!mangaDto.names.isNullOrEmpty()) {
                desc += mangaDto.names.joinToString(" / ") { it.name } + "\n\n"
            }

            val summary = mangaDto.description ?: mangaDto.summary
            if (summary != null) desc += org.jsoup.Jsoup.parse(summary).text() + "\n\n"

            if (mangaDto.content != null) {
                try {
                    val contentElement = jsonInstance.parseToJsonElement(mangaDto.content)
                    if (contentElement is JsonObject && contentElement.containsKey("blocks")) {
                        val blocks = contentElement["blocks"]?.jsonArray
                        blocks?.forEach { block ->
                            if (block is JsonObject && block["type"]?.jsonPrimitive?.content == "paragraph") {
                                val text = block["data"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                                if (text != null) desc += org.jsoup.Jsoup.parse(text).text() + "\n"
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore JSON parsing errors for content
                }
            }
            description = desc.trim()

            val imgName = mangaDto.image ?: mangaDto.img
            thumbnail_url = mangaDto.thumbnail ?: if (imgName != null) {
                "https://i.mangaraw.lat/$imgName"
            } else {
                mangaDto.coverUrl ?: mangaDto.cover ?: ""
            }

            author = mangaDto.author
            artist = mangaDto.artist ?: mangaDto.author
            genre = mangaDto.genres?.mapNotNull { element ->
                if (element is JsonPrimitive) {
                    if (element.isString) {
                        element.content
                    } else {
                        gMap[element.content] ?: "Genre ${element.content}"
                    }
                } else if (element is JsonObject) {
                    element["name"]?.let { if (it is JsonPrimitive) it.content else null }
                } else {
                    null
                }
            }?.joinToString(", ")
            status = when (mangaDto.status?.lowercase() ?: mangaDto.type?.lowercase()) {
                "ongoing", "incomplete" -> SManga.ONGOING
                "completed", "complete" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            initialized = true
        }
    }

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = withBuildId {
        super.fetchChapterList(manga)
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        if (!response.isSuccessful) {
            throw java.io.IOException("HTTP error ${response.code}")
        }
        val dto = response.parseAs<NextDataWrapperDto<MangaDetailsDto>>()
        val mangaDto = dto.pageProps.data.manga
        val mangaSlug = mangaDto.slug

        return mangaDto.chapters.map { chapter ->
            SChapter.create().apply {
                url = "/manga/$mangaSlug/${chapter.routeSlug(mangaSlug)}"
                name = chapter.displayName()

                val dateStr = chapter.publishedAt ?: chapter.updatedAt
                date_upload = dateFormat.tryParse(dateStr)
            }
        }
    }

    // =============================== Pages ================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = withBuildId {
        super.fetchPageList(chapter)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val path = chapter.url.removePrefix("/")
        val nextJsonUrl = "$baseUrl/_next/data/$buildId/$path.json"
        return GET(nextJsonUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        if (!response.isSuccessful) {
            throw java.io.IOException("HTTP error ${response.code}")
        }
        val wrapper = response.parseAs<NextDataWrapperDto<ChapterDetailsDto>>()
        val chapter = wrapper.pageProps.data.chapter

        // Fetch pages data JSON from api.mangarawgo.site
        val pagesJsonUrl = "$pageApiUrl/${chapter.mangaId}/${chapter.id}.json"
        val cryptedResponse = client.newCall(GET(pagesJsonUrl, headers)).execute()
        if (!cryptedResponse.isSuccessful) {
            cryptedResponse.close()
            throw Exception("Failed to fetch crypted pages: ${cryptedResponse.code}")
        }
        val cryptedDto = cryptedResponse.parseAs<CryptedPagesDto>()

        // Decrypt pages data structure
        val keyXor = "/fuCkYou!!!".toByteArray(StandardCharsets.UTF_8)
        val decodedD = b64Decode(cryptedDto.d)
        val decryptedDBytes = xor(decodedD, keyXor)
        val decryptedDString = String(decryptedDBytes, StandardCharsets.UTF_8)

        val pagesData = decryptedDString.parseAs<List<PageDataDto>>()

        val aesKey = chapter.uuid.hexToByteArray()
        val xorKey = "202508055d0db38bae2e86cc41649f90".toByteArray(StandardCharsets.UTF_8)

        return pagesData.mapIndexed { index, pageData ->
            val imageUrl = pageData.imageCandidates(chapter)
                .firstNotNullOfOrNull { (host, encryptedFileName) ->
                    runCatching {
                        decryptImageUrl(host, encryptedFileName, aesKey, xorKey)
                    }.getOrNull()
                }
                .orEmpty()
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================ Build ID Interceptor ============================

    private fun buildIdOutdatedInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (
            response.code == 404 &&
            request.url.run {
                host == baseUrl.removePrefix("https://") &&
                    pathSegments.getOrNull(0) == "_next" &&
                    pathSegments.getOrNull(1) == "data" &&
                    fragment != "DO_NOT_RETRY"
            } &&
            response.header("Content-Type")?.contains("text/html") != false
        ) {
            val document = response.asJsoup()
            buildId = fetchBuildId(document)

            val url = request.url.newBuilder()
                .setPathSegment(2, buildId)
                .fragment("DO_NOT_RETRY")
                .build()
            val newRequest = request.newBuilder()
                .url(url)
                .build()

            return chain.proceed(newRequest)
        }

        return response
    }

    private fun fetchBuildId(document: Document): String {
        val nextData = document.selectFirst("script#__NEXT_DATA__")?.data()
        if (nextData != null) {
            val dto = nextData.parseAs<NextDataDto>()
            return dto.buildId
        }

        val buildIdRegex = Regex("""/_next/static/([^/]+)/_buildManifest\.js""")
        val scriptSrc = document.select("script[src]").map { it.attr("src") }
        for (src in scriptSrc) {
            val match = buildIdRegex.find(src)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        throw Exception("Failed to find buildId in __NEXT_DATA__ or script sources")
    }

    // =============================== Helpers ===============================

    private fun b64Decode(data: String): ByteArray {
        val cleaned = data.replace('-', '+').replace('_', '/').trim()
        val padded = cleaned.padEnd(cleaned.length + (4 - cleaned.length % 4) % 4, '=')
        return Base64.decode(padded, Base64.DEFAULT)
    }

    private fun xor(data: ByteArray, key: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return result
    }

    private fun ChapterDto.displayName(): String {
        val chapterName = nameText()?.let { "Chapter $it" }
        val titleText = title?.takeIf { it.isNotBlank() }

        return when {
            chapterName != null && titleText != null -> "$chapterName - $titleText"
            titleText != null -> titleText
            chapterName != null -> chapterName
            else -> orderText()?.let { "Chapter $it" } ?: "Chapter $id"
        }
    }

    private fun ChapterDto.routeSlug(mangaSlug: String): String {
        val fallbackName = nameText() ?: orderText() ?: id.toString()
        val fallbackSlug = "ch-${fallbackName.replace(".", "-")}"
        val rawSlug = (path ?: cSlug ?: fallbackSlug)
            .substringAfter("/manga/$mangaSlug/", path ?: cSlug ?: fallbackSlug)
            .removePrefix("/")
            .removeSuffix("/")

        if (mangaSlug.isBlank()) return rawSlug

        val stripped = rawSlug.removePrefix("$mangaSlug-")
        return if (stripped.startsWith("ch-")) stripped else rawSlug
    }

    private fun ChapterDto.nameText(): String? = (name as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun ChapterDto.orderText(): String? = (order as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun PageDataDto.imageCandidates(chapter: ChapterDto): List<Pair<String?, String?>> = listOf(
        chapter.bHost to b,
        chapter.dHost to d,
        chapter.pHost to p,
        chapter.tHost to t,
    )

    private fun decryptImageUrl(
        host: String?,
        encryptedFileName: String?,
        aesKey: ByteArray,
        xorKey: ByteArray,
    ): String? {
        if (host.isNullOrBlank() || encryptedFileName.isNullOrBlank()) return null

        val ciphertext = xor(b64Decode(encryptedFileName), xorKey)
        val decryptedPathBytes = decryptAesCtr(ciphertext, aesKey)
        val path = String(decryptedPathBytes, StandardCharsets.UTF_8)

        return if (host.endsWith("/") || path.startsWith("/")) {
            "$host$path"
        } else {
            "$host/$path"
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        val clean = this.replace("-", "")
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) + Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun decryptAesCtr(ciphertext: ByteArray, key: ByteArray): ByteArray {
        val iv = ciphertext.copyOfRange(0, 16)
        val encrypted = ciphertext.copyOfRange(16, ciphertext.size)
        val secretKeySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, IvParameterSpec(iv))
        return cipher.doFinal(encrypted)
    }

    companion object {
        const val SEARCH_PREFIX = "slug:"
    }
}
