package eu.kanade.tachiyomi.multisrc.libgroup

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.random.Random

abstract class LibGroup(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
) : ConfigurableSource, HttpSource() {

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_${id}_2", 0x0000)
    }

    override val supportsLatest = true

    private fun imageContentTypeIntercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)
        val urlRequest = originalRequest.url.toString()
        val possibleType = urlRequest.substringAfterLast("/").substringBefore("?").split(".")
        return if (urlRequest.contains("/chapters/") and (possibleType.size == 2)) {
            val realType = possibleType[1]
            val image = response.body.byteString().toResponseBody("image/$realType".toMediaType())
            response.newBuilder().body(image).build()
        } else {
            response
        }
    }
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .connectTimeout(5, TimeUnit.MINUTES)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addNetworkInterceptor { imageContentTypeIntercept(it) }
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 419) {
                throw IOException("HTTP error ${response.code}. Проверьте сайт. Для завершения авторизации необходимо перезапустить приложение с полной остановкой.")
            }
            if (response.code == 404) {
                throw IOException("HTTP error ${response.code}. Проверьте сайт. Попробуйте авторизоваться через WebView\uD83C\uDF0E︎ и обновите список глав.")
            }
            return@addInterceptor response
        }
        .build()

    private val userAgentMobile = "Mozilla/5.0 (Linux; Android 10; SM-G980F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Mobile Safari/537.36"

    private val userAgentRandomizer = "${Random.nextInt().absoluteValue}"

    protected var csrfToken: String = ""

    override fun headersBuilder() = Headers.Builder().apply {
        // User-Agent required for authorization through third-party accounts (mobile version for correct display in WebView)
        add("User-Agent", userAgentMobile)
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        add("Referer", baseUrl)
    }

    private fun imgHeader() = Headers.Builder().apply {
        add("User-Agent", userAgentMobile)
        add("Accept", "image/avif,image/webp,*/*")
        add("Referer", baseUrl)
    }.build()

    protected fun catalogHeaders() = Headers.Builder()
        .apply {
            add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36 Edg/100.0.$userAgentRandomizer")
            add("Accept", "application/json, text/plain, */*")
            add("X-Requested-With", "XMLHttpRequest")
            add("x-csrf-token", csrfToken)
        }
        .build()

    // Latest
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used") // popularMangaRequest()
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        if (csrfToken.isEmpty()) {
            return client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .flatMap { response ->
                    // Obtain token
                    val resBody = response.body.string()
                    csrfToken = "_token\" content=\"(.*)\"".toRegex().find(resBody)!!.groups[1]!!.value
                    return@flatMap fetchLatestMangaFromApi(page)
                }
        }
        return fetchLatestMangaFromApi(page)
    }

    private fun fetchLatestMangaFromApi(page: Int): Observable<MangasPage> {
        return client.newCall(POST("$baseUrl/filterlist?dir=desc&sort=last_chapter_at&page=$page&chapters[min]=1", catalogHeaders()))
            .asObservableSuccess()
            .map { response ->
                latestUpdatesParse(response)
            }
    }

    override fun latestUpdatesParse(response: Response) =
        popularMangaParse(response)

    // Popular
    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        if (csrfToken.isEmpty()) {
            return client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .flatMap { response ->
                    // Obtain token
                    val resBody = response.body.string()
                    csrfToken = "_token\" content=\"(.*)\"".toRegex().find(resBody)!!.groups[1]!!.value
                    return@flatMap fetchPopularMangaFromApi(page)
                }
        }
        return fetchPopularMangaFromApi(page)
    }

    private fun fetchPopularMangaFromApi(page: Int): Observable<MangasPage> {
        return client.newCall(POST("$baseUrl/filterlist?dir=desc&sort=views&page=$page&chapters[min]=1", catalogHeaders()))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val resBody = response.body.string()
        val result = json.decodeFromString<JsonObject>(resBody)
        val items = result["items"]!!.jsonObject
        val popularMangas = items["data"]?.jsonArray?.map { popularMangaFromElement(it) }
        if (popularMangas != null) {
            val hasNextPage = items["next_page_url"]?.jsonPrimitive?.contentOrNull != null
            return MangasPage(popularMangas, hasNextPage)
        }
        return MangasPage(emptyList(), false)
    }

    // Popular cross Latest
    private fun popularMangaFromElement(el: JsonElement) = SManga.create().apply {
        val slug = el.jsonObject["slug"]!!.jsonPrimitive.content
        title = when {
            isEng.equals("rus") && el.jsonObject["rus_name"]?.jsonPrimitive?.content.orEmpty().isNotEmpty() -> el.jsonObject["rus_name"]!!.jsonPrimitive.content
            isEng.equals("eng") && el.jsonObject["eng_name"]?.jsonPrimitive?.content.orEmpty().isNotEmpty() -> el.jsonObject["eng_name"]!!.jsonPrimitive.content
            else -> el.jsonObject["name"]!!.jsonPrimitive.content
        }
        thumbnail_url = if (el.jsonObject["coverImage"] != null) {
            el.jsonObject["coverImage"]!!.jsonPrimitive.content
        } else {
            "/uploads/cover/" + slug + "/cover/" + el.jsonObject["cover"]!!.jsonPrimitive.content + "_250x350.jpg"
        }
        if (!thumbnail_url!!.contains("://")) {
            thumbnail_url = baseUrl + thumbnail_url
        }
        url = "/$slug"
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val dataStr = document
            .toString()
            .substringAfter("window.__DATA__ = ")
            .substringBefore("window._SITE_COLOR_")
            .substringBeforeLast(";")

        val dataManga = json.decodeFromString<JsonObject>(dataStr)["manga"]

        val manga = SManga.create()

        val body = document.select("div.media-info-list").first()!!
        val rawCategory = document.select(".media-short-info a.media-short-info__item").text()
        val category = when {
            rawCategory == "Комикс западный" -> "Комикс"
            rawCategory.isNotBlank() -> rawCategory
            else -> "Манга"
        }

        val rawAgeStop = document.select(".media-short-info .media-short-info__item[data-caution]").text()

        val ratingValue = document.select(".media-rating__value").last()!!.text().toFloat()
        val ratingVotes = document.select(".media-rating__votes").last()!!.text()
        val ratingStar = when {
            ratingValue > 9.5 -> "★★★★★"
            ratingValue > 8.5 -> "★★★★✬"
            ratingValue > 7.5 -> "★★★★☆"
            ratingValue > 6.5 -> "★★★✬☆"
            ratingValue > 5.5 -> "★★★☆☆"
            ratingValue > 4.5 -> "★★✬☆☆"
            ratingValue > 3.5 -> "★★☆☆☆"
            ratingValue > 2.5 -> "★✬☆☆☆"
            ratingValue > 1.5 -> "★☆☆☆☆"
            ratingValue > 0.5 -> "✬☆☆☆☆"
            else -> "☆☆☆☆☆"
        }
        val genres = document.select(".media-tags > a").map { it.text().capitalize() }
        manga.title = when {
            isEng.equals("rus") && dataManga!!.jsonObject["rusName"]?.jsonPrimitive?.content.orEmpty().isNotEmpty() -> dataManga.jsonObject["rusName"]!!.jsonPrimitive.content
            isEng.equals("eng") && dataManga!!.jsonObject["engName"]?.jsonPrimitive?.content.orEmpty().isNotEmpty() -> dataManga.jsonObject["engName"]!!.jsonPrimitive.content
            else -> dataManga!!.jsonObject["name"]!!.jsonPrimitive.content
        }
        manga.thumbnail_url = document.select(".media-header__cover").attr("src")
        manga.author = body.select("div.media-info-list__title:contains(Автор) + div a").joinToString { it.text() }
        manga.artist = body.select("div.media-info-list__title:contains(Художник) + div a").joinToString { it.text() }

        val statusTranslate = body.select("div.media-info-list__title:contains(Статус перевода) + div").text().lowercase(Locale.ROOT)
        val statusTitle = body.select("div.media-info-list__title:contains(Статус тайтла) + div").text().lowercase(Locale.ROOT)

        manga.status = if (document.html().contains("paper empty section")
        ) {
            SManga.LICENSED
        } else {
            when {
                statusTranslate.contains("завершен") && statusTitle.contains("приостановлен") || statusTranslate.contains("заморожен") || statusTranslate.contains("заброшен") -> SManga.ON_HIATUS
                statusTranslate.contains("завершен") && statusTitle.contains("выпуск прекращён") -> SManga.CANCELLED
                statusTranslate.contains("продолжается") -> SManga.ONGOING
                statusTranslate.contains("завершен") -> SManga.COMPLETED
                else -> when (statusTitle) {
                    "онгоинг" -> SManga.ONGOING
                    "анонс" -> SManga.ONGOING
                    "завершён" -> SManga.COMPLETED
                    "приостановлен" -> SManga.ON_HIATUS
                    "выпуск прекращён" -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
        }
        manga.genre = category + ", " + rawAgeStop + ", " + genres.joinToString { it.trim() }

        val altName = if (dataManga.jsonObject["altNames"]?.jsonArray.orEmpty().isNotEmpty()) {
            "Альтернативные названия:\n" + dataManga.jsonObject["altNames"]!!.jsonArray.joinToString(" / ") { it.jsonPrimitive.content } + "\n\n"
        } else {
            ""
        }

        val mediaNameLanguage = when {
            isEng.equals("eng") && dataManga.jsonObject["rusName"]?.jsonPrimitive?.content.orEmpty().isNotEmpty() -> dataManga.jsonObject["rusName"]!!.jsonPrimitive.content + "\n"
            isEng.equals("rus") && dataManga.jsonObject["engName"]?.jsonPrimitive?.content.orEmpty().isNotEmpty() -> dataManga.jsonObject["engName"]!!.jsonPrimitive.content + "\n"
            else -> ""
        }
        manga.description = mediaNameLanguage + ratingStar + " " + ratingValue + " (голосов: " + ratingVotes + ")\n" + altName + document.select(".media-description__text").text()
        return manga
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservable().doOnNext { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404 && response.asJsoup().select(".m-menu__sign-in").isNotEmpty()) throw Exception("HTTP error ${response.code}. Для просмотра 18+ контента необходима авторизация через WebView\uD83C\uDF0E︎") else throw Exception("HTTP error ${response.code}")
                }
            }
            .map { response ->
                mangaDetailsParse(response)
            }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val rawAgeStop = document.select(".media-short-info .media-short-info__item[data-caution]").text()
        if (rawAgeStop == "18+" && document.select(".m-menu__sign-in").isNotEmpty()) {
            throw Exception("Для просмотра 18+ контента необходима авторизация через WebView\uD83C\uDF0E︎")
        }
        val redirect = document.html()
        if (redirect.contains("paper empty section")) {
            throw Exception("Лицензировано - Нет глав")
        }
        val dataStr = document
            .toString()
            .substringAfter("window.__DATA__ = ")
            .substringBefore("window._SITE_COLOR_")
            .substringBeforeLast(";")

        val data = json.decodeFromString<JsonObject>(dataStr)
        val chaptersList = data["chapters"]!!.jsonObject["list"]?.jsonArray
        val slug = data["manga"]!!.jsonObject["slug"]!!.jsonPrimitive.content
        val branches = data["chapters"]!!.jsonObject["branches"]!!.jsonArray.reversed()
        val teams = data["chapters"]!!.jsonObject["teams"]!!.jsonArray
        val sortingList = preferences.getString(SORTING_PREF, "ms_mixing")
        val auth = data["auth"]!!.jsonPrimitive.content
        val userId = if (auth == "true") data["user"]!!.jsonObject["id"]!!.jsonPrimitive.content else "not"

        val chapters: List<SChapter>? = if (branches.isNotEmpty()) {
            sortChaptersByTranslator(sortingList, chaptersList, slug, userId, branches)
        } else {
            chaptersList
                ?.filter { it.jsonObject["status"]?.jsonPrimitive?.intOrNull != 2 && it.jsonObject["price"]?.jsonPrimitive?.intOrNull == 0 }
                ?.map { chapterFromElement(it, sortingList, slug, userId, null, null, teams, chaptersList) }
        }

        return chapters ?: emptyList()
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservable().doOnNext { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404 && response.asJsoup().select(".m-menu__sign-in").isNotEmpty()) throw Exception("HTTP error ${response.code}. Для просмотра 18+ контента необходима авторизация через WebView\uD83C\uDF0E︎") else throw Exception("HTTP error ${response.code}")
                }
            }
            .map { response ->
                chapterListParse(response)
            }
    }

    private fun sortChaptersByTranslator
    (sortingList: String?, chaptersList: JsonArray?, slug: String, userId: String, branches: List<JsonElement>): List<SChapter>? {
        var chapters: List<SChapter>? = null
        val volume = "(?<=/v)[0-9]+(?=/c[0-9]+)".toRegex()
        val tempChaptersList = mutableListOf<SChapter>()
        for (currentBranch in branches.withIndex()) {
            val branch = branches[currentBranch.index]
            val teamId = branch.jsonObject["id"]!!.jsonPrimitive.int
            val teams = branch.jsonObject["teams"]!!.jsonArray
            val isActive = teams.filter { it.jsonObject["is_active"]?.jsonPrimitive?.intOrNull == 1 }
            val teamsBranch = if (isActive.size == 1) {
                isActive[0].jsonObject["name"]?.jsonPrimitive?.contentOrNull
            } else if (teams.isNotEmpty() && isActive.isEmpty()) {
                teams[0].jsonObject["name"]?.jsonPrimitive?.contentOrNull
            } else {
                "Неизвестный"
            }
            chapters = chaptersList
                ?.filter { it.jsonObject["branch_id"]?.jsonPrimitive?.intOrNull == teamId && it.jsonObject["status"]?.jsonPrimitive?.intOrNull != 2 }
                ?.map { chapterFromElement(it, sortingList, slug, userId, teamId, branches) }
            when (sortingList) {
                "ms_mixing" -> {
                    chapters?.let {
                        if ((tempChaptersList.size < it.size) && !groupTranslates.contains(teamsBranch.toString())) {
                            tempChaptersList.addAll(0, it)
                        } else {
                            tempChaptersList.addAll(it)
                        }
                    }
                    chapters = tempChaptersList.distinctBy { volume.find(it.url)?.value + "--" + it.chapter_number }.sortedWith(compareBy({ -it.chapter_number }, { volume.find(it.url)?.value }))
                }
                "ms_combining" -> {
                    if (!groupTranslates.contains(teamsBranch.toString())) {
                        chapters?.let { tempChaptersList.addAll(it) }
                    }
                    chapters = tempChaptersList
                }
            }
        }
        return chapters
    }

    private fun chapterFromElement
    (chapterItem: JsonElement, sortingList: String?, slug: String, userId: String, teamIdParam: Int? = null, branches: List<JsonElement>? = null, teams: List<JsonElement>? = null, chaptersList: JsonArray? = null): SChapter {
        val chapter = SChapter.create()

        val volume = chapterItem.jsonObject["chapter_volume"]!!.jsonPrimitive.int
        val number = chapterItem.jsonObject["chapter_number"]!!.jsonPrimitive.content
        val chapterScanlatorId = chapterItem.jsonObject["chapter_scanlator_id"]!!.jsonPrimitive.int
        val isScanlatorId = teams?.filter { it.jsonObject["id"]?.jsonPrimitive?.intOrNull == chapterScanlatorId }

        val teamId = if (teamIdParam != null) "&bid=$teamIdParam" else ""

        val url = "/$slug/v$volume/c$number?ui=$userId$teamId"

        chapter.setUrlWithoutDomain(url)

        val nameChapter = chapterItem.jsonObject["chapter_name"]?.jsonPrimitive?.contentOrNull
        val fullNameChapter = "Том $volume. Глава $number"
        chapter.scanlator = if (teams?.size == 1) teams[0].jsonObject["name"]?.jsonPrimitive?.content else if (isScanlatorId.orEmpty().isNotEmpty()) isScanlatorId!![0].jsonObject["name"]?.jsonPrimitive?.content else branches?.let { getScanlatorTeamName(it, chapterItem) } ?: if ((preferences.getBoolean(isScan_USER, false)) || (chaptersList?.distinctBy { it.jsonObject["username"]!!.jsonPrimitive.content }?.size == 1)) chapterItem.jsonObject["username"]!!.jsonPrimitive.content else null
        chapter.name = if (nameChapter.isNullOrBlank()) fullNameChapter else "$fullNameChapter - $nameChapter"
        chapter.date_upload = simpleDateFormat.parse(chapterItem.jsonObject["chapter_created_at"]!!.jsonPrimitive.content.substringBefore(" "))?.time ?: 0L
        chapter.chapter_number = number.toFloatOrNull() ?: -1f

        return chapter
    }

    private fun getScanlatorTeamName(branches: List<JsonElement>, chapterItem: JsonElement): String? {
        var scanlatorData: String? = null
        for (currentBranch in branches.withIndex()) {
            val branch = branches[currentBranch.index].jsonObject
            val teams = branch["teams"]!!.jsonArray
            if (chapterItem.jsonObject["branch_id"]!!.jsonPrimitive.int == branch["id"]!!.jsonPrimitive.int && teams.isNotEmpty()) {
                for (currentTeam in teams.withIndex()) {
                    val team = teams[currentTeam.index].jsonObject
                    val scanlatorId = chapterItem.jsonObject["chapter_scanlator_id"]!!.jsonPrimitive.int
                    if ((scanlatorId == team.jsonObject["id"]!!.jsonPrimitive.int) ||
                        (scanlatorId == 0 && team["is_active"]!!.jsonPrimitive.int == 1)
                    ) {
                        return team["name"]!!.jsonPrimitive.content
                    } else {
                        scanlatorData = branch["teams"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content
                    }
                }
            }
        }
        return scanlatorData
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        // redirect Регистрация 18+
        val redirect = document.html()
        if (!redirect.contains("window.__info")) {
            if (redirect.contains("auth-layout")) {
                throw Exception("Для просмотра 18+ контента необходима авторизация через WebView\uD83C\uDF0E︎")
            }
        }

        val chapInfo = document
            .select("script:containsData(window.__info)")
            .first()!!
            .html()
            .split("window.__info = ")
            .last()
            .trim()
            .split(";")
            .first()

        val chapInfoJson = json.decodeFromString<JsonObject>(chapInfo)
        val servers = chapInfoJson["servers"]!!.jsonObject.toMap()
        val imgUrl: String = chapInfoJson["img"]!!.jsonObject["url"]!!.jsonPrimitive.content

        val serverToUse = listOf(isServer, "secondary", "fourth", "main", "compress").distinct()

        // Get pages
        val pagesArr = document
            .select("script:containsData(window.__pg)")
            .first()!!
            .html()
            .trim()
            .removePrefix("window.__pg = ")
            .removeSuffix(";")

        val pagesJson = json.decodeFromString<JsonArray>(pagesArr)
        val pages = mutableListOf<Page>()

        pagesJson.forEach { page ->
            val keys = servers.keys.filter { serverToUse.indexOf(it) >= 0 }.sortedBy { serverToUse.indexOf(it) }
            val serversUrls = keys.map {
                servers[it]?.jsonPrimitive?.contentOrNull + imgUrl + page.jsonObject["u"]!!.jsonPrimitive.content
            }.distinct().joinToString(separator = ",,") { it }
            pages.add(Page(page.jsonObject["p"]!!.jsonPrimitive.int, serversUrls))
        }

        return pages
    }

    private fun checkImage(url: String): Boolean {
        val response = client.newCall(GET(url, imgHeader())).execute()
        return response.isSuccessful && (response.header("content-length", "0")?.toInt()!! > 600)
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        if (page.imageUrl != null) {
            return Observable.just(page.imageUrl)
        }

        val urls = page.url.split(",,")

        return Observable.from(urls).filter { checkImage(it) }.first()
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, imgHeader())
    }

    // Workaround to allow "Open in browser" use the
    private fun searchMangaByIdRequest(id: String): Request {
        return GET("$baseUrl/$id", headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_SLUG_SEARCH)
            client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = "/$realQuery"
                    MangasPage(listOf(details), false)
                }
        } else {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (csrfToken.isEmpty()) {
            val tokenResponse = client.newCall(popularMangaRequest(page)).execute()
            val resBody = tokenResponse.body.string()
            csrfToken = "_token\" content=\"(.*)\"".toRegex().find(resBody)!!.groups[1]!!.value
        }
        val url = "$baseUrl/filterlist?page=$page&chapters[min]=1".toHttpUrlOrNull()!!.newBuilder()
        if (query.isNotEmpty()) {
            url.addQueryParameter("name", query)
        }
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is CategoryList -> filter.state.forEach { category ->
                    if (category.state) {
                        url.addQueryParameter("types[]", category.id)
                    }
                }
                is FormatList -> filter.state.forEach { forma ->
                    if (forma.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (forma.isIncluded()) "format[include][]" else "format[exclude][]", forma.id)
                    }
                }
                is StatusList -> filter.state.forEach { status ->
                    if (status.state) {
                        url.addQueryParameter("status[]", status.id)
                    }
                }
                is StatusTitleList -> filter.state.forEach { title ->
                    if (title.state) {
                        url.addQueryParameter("manga_status[]", title.id)
                    }
                }
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (genre.isIncluded()) "genres[include][]" else "genres[exclude][]", genre.id)
                    }
                }
                is OrderBy -> {
                    url.addQueryParameter("dir", if (filter.state!!.ascending) "asc" else "desc")
                    url.addQueryParameter("sort", arrayOf("rate", "name", "views", "created_at", "last_chapter_at", "chap_count")[filter.state!!.index])
                }
                is MyList -> filter.state.forEach { favorite ->
                    if (favorite.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (favorite.isIncluded()) "bookmarks[include][]" else "bookmarks[exclude][]", favorite.id)
                    }
                }
                is RequireChapters -> {
                    if (filter.state == 1) {
                        url.setQueryParameter("chapters[min]", "0")
                    }
                }
                else -> {}
            }
        }
        return POST(url.toString(), catalogHeaders())
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Filters
    private class SearchFilter(name: String, val id: String) : Filter.TriState(name)
    private class CheckFilter(name: String, val id: String) : Filter.CheckBox(name)

    private class CategoryList(categories: List<CheckFilter>) : Filter.Group<CheckFilter>("Тип", categories)
    private class FormatList(formas: List<SearchFilter>) : Filter.Group<SearchFilter>("Формат выпуска", formas)
    private class StatusList(statuses: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус перевода", statuses)
    private class StatusTitleList(titles: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус тайтла", titles)
    private class GenreList(genres: List<SearchFilter>) : Filter.Group<SearchFilter>("Жанры", genres)
    private class MyList(favorites: List<SearchFilter>) : Filter.Group<SearchFilter>("Мои списки", favorites)

    override fun getFilterList() = FilterList(
        OrderBy(),
        CategoryList(getCategoryList()),
        FormatList(getFormatList()),
        GenreList(getGenreList()),
        StatusList(getStatusList()),
        StatusTitleList(getStatusTitleList()),
        MyList(getMyList()),
        RequireChapters(),
    )

    private class OrderBy : Filter.Sort(
        "Сортировка",
        arrayOf("Рейтинг", "Имя", "Просмотры", "Дате добавления", "Дате обновления", "Кол-во глав"),
        Selection(2, false),
    )

    private fun getCategoryList() = listOf(
        CheckFilter("Манга", "1"),
        CheckFilter("OEL-манга", "4"),
        CheckFilter("Манхва", "5"),
        CheckFilter("Маньхуа", "6"),
        CheckFilter("Руманга", "8"),
        CheckFilter("Комикс западный", "9"),
    )

    private fun getFormatList() = listOf(
        SearchFilter("4-кома (Ёнкома)", "1"),
        SearchFilter("Сборник", "2"),
        SearchFilter("Додзинси", "3"),
        SearchFilter("Сингл", "4"),
        SearchFilter("В цвете", "5"),
        SearchFilter("Веб", "6"),
    )

    private fun getStatusList() = listOf(
        CheckFilter("Продолжается", "1"),
        CheckFilter("Завершен", "2"),
        CheckFilter("Заморожен", "3"),
        CheckFilter("Заброшен", "4"),
    )

    private fun getStatusTitleList() = listOf(
        CheckFilter("Онгоинг", "1"),
        CheckFilter("Завершён", "2"),
        CheckFilter("Анонс", "3"),
        CheckFilter("Приостановлен", "4"),
        CheckFilter("Выпуск прекращён", "5"),
    )

    private fun getGenreList() = listOf(
        SearchFilter("арт", "32"),
        SearchFilter("боевик", "34"),
        SearchFilter("боевые искусства", "35"),
        SearchFilter("вампиры", "36"),
        SearchFilter("гарем", "37"),
        SearchFilter("гендерная интрига", "38"),
        SearchFilter("героическое фэнтези", "39"),
        SearchFilter("детектив", "40"),
        SearchFilter("дзёсэй", "41"),
        SearchFilter("драма", "43"),
        SearchFilter("игра", "44"),
        SearchFilter("исекай", "79"),
        SearchFilter("история", "45"),
        SearchFilter("киберпанк", "46"),
        SearchFilter("кодомо", "76"),
        SearchFilter("комедия", "47"),
        SearchFilter("махо-сёдзё", "48"),
        SearchFilter("меха", "49"),
        SearchFilter("мистика", "50"),
        SearchFilter("научная фантастика", "51"),
        SearchFilter("омегаверс", "77"),
        SearchFilter("повседневность", "52"),
        SearchFilter("постапокалиптика", "53"),
        SearchFilter("приключения", "54"),
        SearchFilter("психология", "55"),
        SearchFilter("романтика", "56"),
        SearchFilter("самурайский боевик", "57"),
        SearchFilter("сверхъестественное", "58"),
        SearchFilter("сёдзё", "59"),
        SearchFilter("сёдзё-ай", "60"),
        SearchFilter("сёнэн", "61"),
        SearchFilter("сёнэн-ай", "62"),
        SearchFilter("спорт", "63"),
        SearchFilter("сэйнэн", "64"),
        SearchFilter("трагедия", "65"),
        SearchFilter("триллер", "66"),
        SearchFilter("ужасы", "67"),
        SearchFilter("фантастика", "68"),
        SearchFilter("фэнтези", "69"),
        SearchFilter("школа", "70"),
        SearchFilter("эротика", "71"),
        SearchFilter("этти", "72"),
        SearchFilter("юри", "73"),
        SearchFilter("яой", "74"),
    )

    private fun getMyList() = listOf(
        SearchFilter("Читаю", "1"),
        SearchFilter("В планах", "2"),
        SearchFilter("Брошено", "3"),
        SearchFilter("Прочитано", "4"),
        SearchFilter("Любимые", "5"),
    )

    private class RequireChapters : Filter.Select<String>(
        "Только проекты с главами",
        arrayOf("Да", "Все"),
    )

    companion object {
        const val PREFIX_SLUG_SEARCH = "slug:"
        private const val SERVER_PREF = "MangaLibImageServer"

        private const val SORTING_PREF = "MangaLibSorting"
        private const val SORTING_PREF_Title = "Способ выбора переводчиков"

        private const val isScan_USER = "ScanlatorUsername"
        private const val isScan_USER_Title = "Альтернативный переводчик"

        private const val TRANSLATORS_TITLE = "Чёрный список переводчиков\n(для красоты через «/» или с новой строки)"
        private const val TRANSLATORS_DEFAULT = ""

        private const val LANGUAGE_PREF = "MangaLibTitleLanguage"
        private const val LANGUAGE_PREF_Title = "Выбор языка на обложке"

        private val simpleDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    }

    private var isServer: String? = preferences.getString(SERVER_PREF, "fourth")
    private var isEng: String? = preferences.getString(LANGUAGE_PREF, "eng")
    private var groupTranslates: String = preferences.getString(TRANSLATORS_TITLE, TRANSLATORS_DEFAULT)!!
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val serverPref = ListPreference(screen.context).apply {
            key = SERVER_PREF
            title = "Сервер изображений"
            entries = arrayOf("Первый", "Второй", "Сжатия")
            entryValues = arrayOf("secondary", "fourth", "compress")
            summary = "%s \n\nВыбор приоритетного сервера изображений. \n" +
                "По умолчанию «Второй». \n\n" +
                "ⓘВыбор другого помогает при долгой автоматической смене/загрузке изображений текущего."
            setDefaultValue("fourth")
            setOnPreferenceChangeListener { _, newValue ->
                isServer = newValue.toString()
                true
            }
        }

        val sortingPref = ListPreference(screen.context).apply {
            key = SORTING_PREF
            title = SORTING_PREF_Title
            entries = arrayOf(
                "Полный список (без повторных переводов)",
                "Все переводы (друг за другом)",
            )
            entryValues = arrayOf("ms_mixing", "ms_combining")
            summary = "%s"
            setDefaultValue("ms_mixing")
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                preferences.edit().putString(SORTING_PREF, selected).commit()
            }
        }
        val scanlatorUsername = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = isScan_USER
            title = isScan_USER_Title
            summary = "Отображает Ник переводчика если Группа не указана явно."
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(key, checkValue).commit()
            }
        }
        val titleLanguagePref = ListPreference(screen.context).apply {
            key = LANGUAGE_PREF
            title = LANGUAGE_PREF_Title
            entries = arrayOf("Английский", "Русский")
            entryValues = arrayOf("eng", "rus")
            summary = "%s"
            setDefaultValue("eng")
            setOnPreferenceChangeListener { _, newValue ->
                val titleLanguage = preferences.edit().putString(LANGUAGE_PREF, newValue as String).commit()
                val warning = "Если язык обложки не изменился очистите базу данных в приложении (Настройки -> Дополнительно -> Очистить базу данных)"
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                titleLanguage
            }
        }
        screen.addPreference(serverPref)
        screen.addPreference(sortingPref)
        screen.addPreference(screen.editTextPreference(TRANSLATORS_TITLE, TRANSLATORS_DEFAULT, groupTranslates))
        screen.addPreference(scanlatorUsername)
        screen.addPreference(titleLanguagePref)
    }
    private fun PreferenceScreen.editTextPreference(title: String, default: String, value: String): androidx.preference.EditTextPreference {
        return androidx.preference.EditTextPreference(context).apply {
            key = title
            this.title = title
            summary = value.replace("/", "\n")
            this.setDefaultValue(default)
            dialogTitle = title
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(title, newValue as String).commit()
                    Toast.makeText(context, "Для обновления списка необходимо перезапустить приложение с полной остановкой.", Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }
    }
}
