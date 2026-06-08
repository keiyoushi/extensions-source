package eu.kanade.tachiyomi.extension.ja.soraraw

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
import uy.kohesive.injekt.injectLazy
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

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::buildIdOutdatedInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private var buildId: String = ""

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val pageApiUrl = "https://api.mangarawgo.site"

    // ============================ Database Builder ============================

    private var genreMap: Map<String, String>? = null

    private fun getGenreMap(): Map<String, String> {
        if (genreMap != null) return genreMap!!

        try {
            val request = GET("$baseUrl/genres.json", headers)
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body.string()
                val map = mutableMapOf<String, String>()
                val element = json.parseToJsonElement(jsonStr)
                if (element is kotlinx.serialization.json.JsonArray) {
                    for (item in element) {
                        if (item is JsonObject) {
                            val id = item["id"]?.jsonPrimitive?.content ?: item["value"]?.jsonPrimitive?.content
                            val name = item["name"]?.jsonPrimitive?.content ?: item["label"]?.jsonPrimitive?.content
                            if (id != null && name != null) {
                                map[id] = name
                            }
                        }
                    }
                } else if (element is JsonObject) {
                    for ((key, value) in element) {
                        if (value is JsonPrimitive) {
                            map[key] = value.content
                        } else if (value is JsonObject && value.containsKey("name")) {
                            map[key] = value["name"]?.jsonPrimitive?.content ?: ""
                        }
                    }
                }
                genreMap = map
                return map
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyMap()
    }

    private var fullDatabase: List<MangaDto>? = null
    private val pageSize = 50

    @Synchronized
    private fun getFullDatabaseObservable(): Observable<List<MangaDto>> {
        if (fullDatabase != null) return Observable.just(fullDatabase!!)

        return client.newCall(GET("$baseUrl/", headers)).asObservableSuccess()
            .flatMap { response ->
                response.close()
                Observable.create<List<MangaDto>> { subscriber ->
                    val allMangas = mutableListOf<MangaDto>()
                    val seenIds = mutableSetOf<String>()

                    try {
                        for (page in 1..100) {
                            val request = GET("$baseUrl/mangas_$page.json", headers)
                            val jsonResponse = client.newCall(request).execute()

                            if (jsonResponse.code == 404) {
                                jsonResponse.close()
                                break
                            }
                            if (!jsonResponse.isSuccessful) {
                                jsonResponse.close()
                                break
                            }

                            val bodyStr = jsonResponse.body.string()
                            val dto = try {
                                json.decodeFromString<MangaListDto>(bodyStr)
                            } catch (e: Exception) {
                                break
                            }

                            if (dto.list.isEmpty()) break

                            var addedNew = false
                            for (manga in dto.list) {
                                val slug = manga.slug ?: continue
                                if (seenIds.add(slug)) {
                                    addedNew = true
                                    allMangas.add(manga)
                                }
                            }

                            if (!addedNew) break
                        }

                        fullDatabase = allMangas
                        subscriber.onNext(allMangas)
                        subscriber.onCompleted()
                    } catch (e: Exception) {
                        if (allMangas.isNotEmpty()) {
                            fullDatabase = allMangas
                            subscriber.onNext(allMangas)
                            subscriber.onCompleted()
                        } else {
                            subscriber.onError(e)
                        }
                    }
                }
            }
    }

    private fun MangaDto.toSManga(gMap: Map<String, String>): SManga {
        val mangaDto = this
        return SManga.create().apply {
            url = "/manga/${mangaDto.slug ?: ""}"
            title = mangaDto.name ?: ""
            description = mangaDto.description ?: mangaDto.summary

            val imgName = mangaDto.image ?: mangaDto.img
            thumbnail_url = mangaDto.thumbnail ?: if (imgName != null) {
                "https://i.mangaraw.lat/$imgName"
            } else {
                mangaDto.cover_url ?: mangaDto.cover ?: ""
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
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
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

    private class ContentFilter : Filter.Select<String>("コンテンツ", arrayOf("すべて", "一般", "18+"))
    private class ModeFilter : Filter.Select<String>("表示モード", arrayOf("すべて", "縦", "横"))
    private class StatusFilter : Filter.Select<String>("ステータス", arrayOf("すべて", "連載中", "完結"))
    private class SortFilter : Filter.Select<String>("並び順", arrayOf("閲覧数", "更新", "保存"))

    private class GenreFilter :
        Filter.Select<String>(
            "Genre",
            arrayOf(
                "All", "日本漫画", "ファンタジー", "コメディ", "ロマンス", "アクション", "ドラマ",
                "青年", "冒険", "少年", "日常", "フルカラー", "恋愛", "学園生活", "SMARTOON",
                "超自然", "ハーレム", "独占配信", "少女", "異世界", "更新中", "オリジナル",
                "ミステリー", "女性マンガ", "ホラー", "歴史", "転生", "SF", "心理", "青年マンガ",
                "成熟", "女性", "ラブコメ", "コミカライズ", "スポーツ", "学園", "少女マンガ",
                "少年マンガ", "現代", "ギャグ・コメディ", "完結", "王様・貴族", "悲劇", "復讐",
                "胸キュン", "異能力", "ラブストーリー", "魔法", "百合",
            ),
        )

    // ============================== Popular / Latest ===============================

    private fun parseNextDataMangas(response: Response): MangasPage {
        if (!response.isSuccessful) {
            throw java.io.IOException("HTTP error ${response.code}")
        }
        val jsonString = response.body.string()
        val element = json.parseToJsonElement(jsonString)

        val mangas = mutableListOf<SManga>()
        val gMap = getGenreMap()

        var hasNextPage = false

        fun extractMangas(node: kotlinx.serialization.json.JsonElement) {
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
                            url = "/manga/$slug"
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
                val dto = json.decodeFromString<TopMangasDto>(response.body.string())
                val gMap = getGenreMap()
                val mangas = dto.mangas.map { it.toSManga(gMap) }.distinctBy { it.url }
                MangasPage(mangas, false)
            }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        val path = if (page == 1) "newest.json" else "newest/page/$page.json?page=$page"
        return client.newCall(GET("$baseUrl/", headers)).asObservableSuccess()
            .flatMap { response ->
                response.close()
                val request = GET("$baseUrl/_next/data/$buildId/$path", headers)
                client.newCall(request).asObservableSuccess().map { parseNextDataMangas(it) }
            }
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
            val request = mangaDetailsRequest(SManga.create().apply { url = "/manga/$slug" })

            return client.newCall(GET("$baseUrl/", headers)).asObservableSuccess()
                .flatMap { response ->
                    response.close()
                    client.newCall(request).asObservableSuccess().map { detailsResponse ->
                        val details = mangaDetailsParse(detailsResponse)
                        MangasPage(listOf(details), false)
                    }
                }
        }

        return getFullDatabaseObservable().map { list ->
            var filtered = list
            val gMap = getGenreMap()

            if (query.isNotBlank()) {
                filtered = filtered.filter {
                    it.name?.contains(query, ignoreCase = true) == true ||
                        it.alt_names?.contains(query, ignoreCase = true) == true
                }
            }

            for (filter in filters) {
                when (filter) {
                    is ContentFilter -> {
                        when (filter.state) {
                            1 -> filtered = filtered.filter { it.is_adult == "no" }
                            2 -> filtered = filtered.filter { it.is_adult == "yes" }
                        }
                    }
                    is ModeFilter -> {
                        when (filter.state) {
                            1 -> filtered = filtered.filter { it.mode == "vertical" }
                            2 -> filtered = filtered.filter { it.mode == "horizontal" }
                        }
                    }
                    is StatusFilter -> {
                        when (filter.state) {
                            1 -> filtered = filtered.filter { it.type == "incomplete" || it.status == "ongoing" }
                            2 -> filtered = filtered.filter { it.type == "complete" || it.type == "completed" || it.status == "completed" }
                        }
                    }
                    is GenreFilter -> {
                        if (filter.state != 0) {
                            val genreName = filter.values[filter.state]
                            val genreIdStr = gMap.entries.find { it.value == genreName }?.key
                            if (genreIdStr != null) {
                                filtered = filtered.filter { manga ->
                                    manga.genres?.any { element ->
                                        if (element is JsonPrimitive) {
                                            element.content == genreIdStr || element.content == genreName
                                        } else {
                                            false
                                        }
                                    } == true
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            val sortFilter = filters.filterIsInstance<SortFilter>().firstOrNull()
            filtered = when (sortFilter?.state) {
                1 -> filtered.sortedByDescending { it.c_published ?: it.updated_at ?: it.c_published_at ?: "" }
                2 -> filtered.sortedByDescending { it.number_bookmark ?: 0 }
                else -> filtered.sortedByDescending { it.views ?: 0 }
            }

            val start = (page - 1) * pageSize
            val end = minOf(start + pageSize, filtered.size)
            val pagedList = if (start < filtered.size) filtered.subList(start, end) else emptyList()

            val mappedList = pagedList.map { it.toSManga(gMap) }
            MangasPage(mappedList, end < filtered.size)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = client.newCall(GET("$baseUrl/", headers)).asObservableSuccess()
        .flatMap { response ->
            response.close()
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
        val dto = json.decodeFromString<NextDataWrapperDto<MangaDetailsDto>>(response.body.string())
        val mangaDto = dto.pageProps.data.manga
        val gMap = getGenreMap()

        return SManga.create().apply {
            url = "/manga/${mangaDto.slug ?: ""}"
            title = mangaDto.name ?: ""

            var desc = ""
            if (mangaDto.rate != null && mangaDto.rate.jsonPrimitive.content != "null") {
                desc += "★ ${mangaDto.rate.jsonPrimitive.content}"
                if (mangaDto.number_rate != null && mangaDto.number_rate > 0) desc += " (${mangaDto.number_rate})"
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
                    val contentElement = json.parseToJsonElement(mangaDto.content)
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
                mangaDto.cover_url ?: mangaDto.cover ?: ""
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
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            initialized = true
        }
    }

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(GET("$baseUrl/", headers)).asObservableSuccess()
        .flatMap { response ->
            response.close()
            super.fetchChapterList(manga)
        }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        if (!response.isSuccessful) {
            throw java.io.IOException("HTTP error ${response.code}")
        }
        val dto = json.decodeFromString<NextDataWrapperDto<MangaDetailsDto>>(response.body.string())
        val mangaDto = dto.pageProps.data.manga
        val mangaSlug = mangaDto.slug.orEmpty()

        return mangaDto.chapters.map { chapter ->
            SChapter.create().apply {
                url = "/manga/$mangaSlug/${chapter.routeSlug(mangaSlug)}"
                name = chapter.displayName()

                val dateStr = chapter.published_at ?: chapter.updated_at
                date_upload = try {
                    if (dateStr != null) dateFormat.parse(dateStr)?.time ?: 0L else 0L
                } catch (e: Exception) {
                    0L
                }
            }
        }
    }

    // =============================== Pages ================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(GET("$baseUrl/", headers)).asObservableSuccess()
        .flatMap { response ->
            response.close()
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
        val wrapper = json.decodeFromString<NextDataWrapperDto<ChapterDetailsDto>>(response.body.string())
        val chapter = wrapper.pageProps.data.chapter

        // Fetch pages data JSON from api.mangarawgo.site
        val pagesJsonUrl = "$pageApiUrl/${chapter.manga_id}/${chapter.id}.json"
        val cryptedResponse = client.newCall(GET(pagesJsonUrl, headers)).execute()
        if (!cryptedResponse.isSuccessful) {
            cryptedResponse.close()
            throw Exception("Failed to fetch crypted pages: ${cryptedResponse.code}")
        }
        val cryptedDto = json.decodeFromString<CryptedPagesDto>(cryptedResponse.body.string())

        // Decrypt pages data structure
        val keyXor = "/fuCkYou!!!".toByteArray(StandardCharsets.UTF_8)
        val decodedD = b64Decode(cryptedDto.d)
        val decryptedDBytes = xor(decodedD, keyXor)
        val decryptedDString = String(decryptedDBytes, StandardCharsets.UTF_8)

        val pagesData = json.decodeFromString<List<PageDataDto>>(decryptedDString)

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
            val dto = json.decodeFromString<NextDataDto>(nextData)
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
        val rawSlug = (path ?: c_slug ?: fallbackSlug)
            .substringAfter("/manga/$mangaSlug/", path ?: c_slug ?: fallbackSlug)
            .removePrefix("/")
            .removeSuffix("/")

        if (mangaSlug.isBlank()) return rawSlug

        val stripped = rawSlug.removePrefix("$mangaSlug-")
        return if (stripped.startsWith("ch-")) stripped else rawSlug
    }

    private fun ChapterDto.nameText(): String? = (name as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun ChapterDto.orderText(): String? = (order as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun PageDataDto.imageCandidates(chapter: ChapterDto): List<Pair<String?, String?>> = listOf(
        chapter._b to b,
        chapter._d to d,
        chapter._p to p,
        chapter._t to t,
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
