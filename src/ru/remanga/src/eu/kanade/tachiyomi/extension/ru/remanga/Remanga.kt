package eu.kanade.tachiyomi.extension.ru.remanga

import android.annotation.TargetApi
import android.app.Application
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.extension.ru.remanga.dto.BookDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.BranchesDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.ChunksPageDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.ExBookDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.ExLibraryDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.ExWrapperDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.LibraryDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.MangaDetDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.MyLibraryDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.PageDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.PageWrapperDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.PagesDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.SeriesWrapperDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.TagsDto
import eu.kanade.tachiyomi.extension.ru.remanga.dto.UserDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit.MINUTES
import kotlin.math.absoluteValue
import kotlin.random.Random

class Remanga : ConfigurableSource, HttpSource() {

    override val name = "Remanga"

    override val id: Long = 8983242087533137528

    override val lang = "ru"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun PUT(
        url: String,
        headers: Headers = Headers.Builder().build(),
        body: RequestBody = FormBody.Builder().build(),
        cache: CacheControl = CacheControl.Builder().maxAge(10, MINUTES).build(),
    ): Request {
        return Request.Builder()
            .url(url)
            .put(body)
            .headers(headers)
            .cacheControl(cache)
            .build()
    }

    private val baseOrig: String = "https://api.remanga.org"
    private val baseMirr: String = "https://api.xn--80aaig9ahr.xn--c1avg" // https://реманга.орг
    private val domain: String? = preferences.getString(DOMAIN_PREF, baseOrig)

    private val baseRuss: String = "https://exmanga.ru"
    private val baseUkr: String = "https://ex.euromc.com.ua"
    private val exManga: String = preferences.getString(exDOMAIN_PREF, baseRuss) ?: baseRuss

    override val baseUrl = domain.toString()

    override val supportsLatest = true

    private val userAgentRandomizer = "${Random.nextInt().absoluteValue}"

    override fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        // Magic User-Agent, no change/update, does not cause 403
        if (!preferences.getBoolean(userAgent_PREF, false)) { add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36 Edg/100.0.$userAgentRandomizer") }
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/jxl,image/webp,*/*;q=0.8")
        add("Referer", baseUrl.replace("api.", ""))
    }

    private fun exHeaders() = Headers.Builder()
        .set("User-Agent", "Tachiyomi")
        .set("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .set("Referer", baseUrl.replace("api.", ""))
        .build()
    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // authorization breaks exManga
        if (request.url.toString().contains(exManga)) {
            return chain.proceed(request)
        }

        val cookies = client.cookieJar.loadForRequest(baseUrl.replace("api.", "").toHttpUrl())
        val authCookie = cookies
            .firstOrNull { cookie -> cookie.name == "user" }
            ?.let { cookie -> URLDecoder.decode(cookie.value, "UTF-8") }
            ?.let { jsonString -> json.decodeFromString<UserDto>(jsonString) }
            ?: return chain.proceed(request)

        val access_token = cookies
            .firstOrNull { cookie -> cookie.name == "token" }
            ?.let { cookie -> URLDecoder.decode(cookie.value, "UTF-8") }
            ?: return chain.proceed(request)

        USER_ID = authCookie.id.toString()

        val authRequest = request.newBuilder()
            .addHeader("Authorization", "bearer $access_token")
            .build()
        return chain.proceed(authRequest)
    }
    private fun imageContentTypeIntercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)
        val urlRequest = originalRequest.url.toString()
        val possibleType = urlRequest.substringAfterLast("/").substringBefore("?").split(".")
        return if (urlRequest.contains("/images/") and (possibleType.size == 2)) {
            val realType = possibleType[1]
            val image = response.body.byteString().toResponseBody("image/$realType".toMediaType())
            response.newBuilder().body(image).build()
        } else {
            response
        }
    }

    private val loadLimit = if (!preferences.getBoolean(bLoad_PREF, false)) 1 else 3

    override val client: OkHttpClient =
        network.cloudflareClient.newBuilder()
            .rateLimitHost("https://img3.reimg.org".toHttpUrl(), loadLimit, 2)
            .rateLimitHost("https://img5.reimg.org".toHttpUrl(), loadLimit, 2)
            .rateLimitHost(exManga.toHttpUrl(), 3)
            .addInterceptor { imageContentTypeIntercept(it) }
            .addInterceptor { authIntercept(it) }
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val response = chain.proceed(originalRequest)
                if (originalRequest.url.toString().contains(exManga) and !response.isSuccessful) {
                    throw IOException("HTTP error ${response.code}. Домен ${exManga.substringAfter("//")} сервиса ExManga недоступен, выберите другой в настройках ⚙️ расширения")
                }
                response
            }
            .addNetworkInterceptor { chain ->
                val originalRequest = chain.request()
                val response = chain.proceed(originalRequest)
                if (originalRequest.url.toString().contains(baseUrl) and ((response.code == 403) or (response.code == 500))) {
                    val indicateUAgant = if (headers["User-Agent"].orEmpty().contains(userAgentRandomizer)) "☒" else "☑"
                    throw IOException("HTTP error ${response.code}. Попробуйте сменить Домен ${baseUrl.replace(baseMirr.substringAfter("api."), "реманга.орг").substringAfter("//")} и/или User-Agent$indicateUAgant в настройках ⚙️ расширения.")
                }
                response
            }
            .build()

    private val count = 30

    private var branches = mutableMapOf<String, List<BranchesDto>>()

    private var mangaIDs = mutableMapOf<String, Long>()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/search/catalog/?ordering=-rating&count=$count&page=$page&count_chapters_gte=1".toHttpUrl().newBuilder()
        if (preferences.getBoolean(isLib_PREF, false)) {
            url.addQueryParameter("exclude_bookmarks", "1")
        }
        return GET(url.toString(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/search/catalog/?ordering=-chapter_date&count=$count&page=$page&count_chapters_gte=1".toHttpUrl().newBuilder()
        if (preferences.getBoolean(isLib_PREF, false)) {
            url.addQueryParameter("exclude_bookmarks", "1")
        }
        return GET(url.toString(), headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.toString().contains(exManga)) {
            val page = json.decodeFromString<ExWrapperDto<List<ExLibraryDto>>>(response.body.string())
            val mangas = page.data.map {
                it.toSManga()
            }

            return MangasPage(mangas, true)
        } else if (response.request.url.toString().contains("/bookmarks/")) {
            val page = json.decodeFromString<PageWrapperDto<MyLibraryDto>>(response.body.string())
            val mangas = page.content.map {
                it.title.toSManga()
            }

            return MangasPage(mangas, true)
        } else {
            val page = json.decodeFromString<PageWrapperDto<LibraryDto>>(response.body.string())

            val mangas = page.content.map {
                it.toSManga()
            }

            return MangasPage(mangas, page.props.page < page.props.total_pages!!)
        }
    }
    private fun ExLibraryDto.toSManga(): SManga =
        SManga.create().apply {
            // Do not change the title name to ensure work with a multilingual catalog!
            title = name
            url = "/api/titles/$dir/"
            thumbnail_url = baseUrl + img
        }

    private fun LibraryDto.toSManga(): SManga =
        SManga.create().apply {
            // Do not change the title name to ensure work with a multilingual catalog!
            title = if (isEng.equals("rus")) rus_name else en_name
            url = "/api/titles/$dir/"
            thumbnail_url = baseUrl + img.mid
        }

    private val simpleDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US) }

    private fun parseDate(date: String?): Long {
        date ?: return Date().time
        return try {
            simpleDateFormat.parse(date)!!.time
        } catch (_: Exception) {
            Date().time
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/api/search/catalog/?page=$page&count_chapters_gte=1".toHttpUrl().newBuilder()
        if (query.isNotEmpty()) {
            url = "$baseUrl/api/search/?page=$page".toHttpUrl().newBuilder()
            url.addQueryParameter("query", query)
        }
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    val ord = arrayOf("id", "chapter_date", "rating", "votes", "views", "count_chapters", "random")[filter.state!!.index]
                    url.addQueryParameter("ordering", if (filter.state!!.ascending) ord else "-$ord")
                }
                is CategoryList -> filter.state.forEach { category ->
                    if (category.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (category.isIncluded()) "categories" else "exclude_categories", category.id)
                    }
                }
                is TypeList -> filter.state.forEach { type ->
                    if (type.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (type.isIncluded()) "types" else "exclude_types", type.id)
                    }
                }
                is StatusList -> filter.state.forEach { status ->
                    if (status.state) {
                        url.addQueryParameter("status", status.id)
                    }
                }
                is AgeList -> filter.state.forEach { age ->
                    if (age.state) {
                        url.addQueryParameter("age_limit", age.id)
                    }
                }
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (genre.isIncluded()) "genres" else "exclude_genres", genre.id)
                    }
                }
                is MyList -> {
                    if (filter.state > 0) {
                        if (USER_ID == "") {
                            throw Exception("Пользователь не найден, необходима авторизация через WebView\uD83C\uDF0E")
                        }
                        val TypeQ = getMyList()[filter.state].id
                        val UserProfileUrl = "$baseUrl/api/users/$USER_ID/bookmarks/?type=$TypeQ&page=$page".toHttpUrl().newBuilder()
                        return GET(UserProfileUrl.toString(), headers)
                    }
                }
                is RequireChapters -> {
                    if (filter.state == 1) {
                        url.setQueryParameter("count_chapters_gte", "0")
                    }
                }
                is RequireEX -> {
                    if (filter.state == 1) {
                        return GET("$exManga/manga?take=20&skip=${10 * (page - 1)}&name=$query", exHeaders())
                    }
                }
                else -> {}
            }
        }

        if (preferences.getBoolean(isLib_PREF, false)) {
            url.addQueryParameter("exclude_bookmarks", "1")
        }

        return GET(url.toString(), headers)
    }

    private fun parseStatus(status: Int): Int {
        return when (status) {
            0 -> SManga.COMPLETED // Закончен
            1 -> SManga.ONGOING // Продолжается
            2 -> SManga.ON_HIATUS // Заморожен
            3 -> SManga.ON_HIATUS // Нет переводчика
            4 -> SManga.ONGOING // Анонс
            5 -> SManga.LICENSED // Лицензировано
            else -> SManga.UNKNOWN
        }
    }

    private fun parseType(type: TagsDto): String {
        return when (type.name) {
            "Западный комикс" -> "Комикс"
            else -> type.name
        }
    }
    private fun parseAge(age_limit: Int): String {
        return when (age_limit) {
            2 -> "18+"
            1 -> "16+"
            else -> ""
        }
    }

    private fun MangaDetDto.toSManga(): SManga {
        val ratingValue = avg_rating.toFloat()
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
        val o = this
        return SManga.create().apply {
            // Do not change the title name to ensure work with a multilingual catalog!
            title = if (isEng.equals("rus")) rus_name else en_name
            url = "/api/titles/$dir/"
            thumbnail_url = baseUrl + img.high
            var altName = ""
            if (another_name.isNotEmpty()) {
                altName = "Альтернативные названия:\n" + another_name + "\n"
            }
            val mediaNameLanguage = if (isEng.equals("rus")) en_name else rus_name
            this.description = "$mediaNameLanguage\n$ratingStar $ratingValue (голосов: $count_rating)\n$altName" +
                o.description?.let { Jsoup.parse(it) }
                    ?.select("body:not(:has(p)),p,br")
                    ?.prepend("\\n")?.text()?.replace("\\n", "\n")?.replace("\n ", "\n")
                    .orEmpty()
            genre = (parseType(type) + ", " + parseAge(age_limit) + ", " + (genres + categories).joinToString { it.name }).split(", ").filter { it.isNotEmpty() }.joinToString { it.trim() }
            status = parseStatus(o.status.id)
        }
    }
    private fun titleDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        var warnLogin = false
        return client.newCall(titleDetailsRequest(manga))
            .asObservable().doOnNext { response ->
                if (!response.isSuccessful) {
                    response.close()
                    if (USER_ID == "") warnLogin = true else throw Exception("HTTP error ${response.code}")
                }
            }
            .map { response ->
                (if (warnLogin) manga.apply { description = "Для просмотра контента необходима авторизация через WebView\uD83C\uDF0E︎" } else mangaDetailsParse(response))
                    .apply {
                        initialized = true
                    }
            }
    }
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl.replace("api.", "") + "/manga/" + manga.url.substringAfter("/api/titles/", "/"), headers)
    }
    override fun mangaDetailsParse(response: Response): SManga {
        val series = json.decodeFromString<SeriesWrapperDto<MangaDetDto>>(response.body.string())
        branches[series.content.dir] = series.content.branches
        mangaIDs[series.content.dir] = series.content.id
        return series.content.toSManga()
    }

    private fun mangaBranches(manga: SManga): List<BranchesDto> {
        val requestString = client.newCall(GET(baseUrl + manga.url, headers)).execute()
        if (!requestString.isSuccessful) {
            if (USER_ID == "") {
                throw Exception("HTTP error ${requestString.code}. Для просмотра контента необходима авторизация через WebView\uD83C\uDF0E")
            }
            throw Exception("HTTP error ${requestString.code}")
        }
        val responseString = requestString.body.string()
        // manga requiring login return "content" as a JsonArray instead of the JsonObject we expect
        // callback request for update outside the library
        val content = json.decodeFromString<JsonObject>(responseString)["content"]
        return if (content is JsonObject) {
            val series = json.decodeFromJsonElement<MangaDetDto>(content)
            branches[series.dir] = series.branches
            mangaIDs[series.dir] = series.id
            if (parseStatus(series.status.id) == SManga.LICENSED && series.branches.maxByOrNull { selector(it) }!!.count_chapters == 0) {
                throw Exception("Лицензировано - Нет глав")
            }
            series.branches
        } else {
            emptyList()
        }
    }

    private fun filterPaid(tempChaptersList: MutableList<SChapter>): MutableList<SChapter> {
        return if (!preferences.getBoolean(PAID_PREF, false)) {
            val lastEx = tempChaptersList.find { !it.name.contains("\uD83D\uDCB2") }
            tempChaptersList.filterNot {
                it.name.contains("\uD83D\uDCB2") && if (lastEx != null) {
                    val volCor = it.name.substringBefore(
                        ". Глава",
                    ).toIntOrNull()!!
                    val volLast = lastEx.name.substringBefore(". Глава").toIntOrNull()!!
                    (volCor > volLast) ||
                        ((volCor == volLast) && (it.chapter_number > lastEx.chapter_number))
                } else {
                    false
                }
            } as MutableList<SChapter>
        } else {
            tempChaptersList
        }
    }

    private fun selector(b: BranchesDto): Int = b.count_chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val branch = branches.getOrElse(manga.url.substringAfter("/api/titles/").substringBefore("/").substringBefore("?")) { mangaBranches(manga) }
        return when {
            manga.status == SManga.LICENSED && branch.maxByOrNull { selector(it) }!!.count_chapters == 0 -> {
                Observable.error(Exception("Лицензировано - Нет глав"))
            }
            branch.isEmpty() -> {
                if (USER_ID == "") {
                    Observable.error(Exception("Для просмотра контента необходима авторизация через WebView\uD83C\uDF0E"))
                } else {
                    return Observable.just(listOf())
                }
            }
            else -> {
                val mangaID = mangaIDs[manga.url.substringAfter("/api/titles/").substringBefore("/").substringBefore("?")]
                val exChapters = if (preferences.getBoolean(exPAID_PREF, true)) {
                    json.decodeFromString<ExWrapperDto<List<ExBookDto>>>(client.newCall(GET("$exManga/chapter/history/$mangaID", exHeaders())).execute().body.string()).data
                } else {
                    emptyList()
                }
                val selectedBranch = branch.maxByOrNull { selector(it) }!!
                val tempChaptersList = mutableListOf<SChapter>()
                (1..(selectedBranch.count_chapters / 300 + 1)).map {
                    val response = chapterListRequest(selectedBranch.id, it)
                    chapterListParse(response, manga, exChapters)
                }.let { tempChaptersList.addAll(it.flatten()) }
                if (branch.size > 1) {
                    val selectedBranch2 =
                        branch.filter { it.id != selectedBranch.id }.maxByOrNull { selector(it) }!!
                    if (selectedBranch2.count_chapters > 0) {
                        if (selectedBranch.count_chapters < (
                            json.decodeFromString<SeriesWrapperDto<List<BookDto>>>(
                                    chapterListRequest(selectedBranch2.id, 1).body.string(),
                                ).content.firstOrNull()?.chapter?.toFloatOrNull() ?: -2F
                            )
                        ) {
                            (1..(selectedBranch2.count_chapters / 300 + 1)).map {
                                val response = chapterListRequest(selectedBranch2.id, it)
                                chapterListParse(response, manga, exChapters)
                            }.let { tempChaptersList.addAll(0, it.flatten()) }
                            return filterPaid(tempChaptersList).distinctBy { it.name.substringBefore(". Глава") + "--" + it.chapter_number }.sortedWith(compareBy({ it.name.substringBefore(". Глава").toIntOrNull()!! }, { it.chapter_number })).reversed().let { Observable.just(it) }
                        }
                    }
                }

                return filterPaid(tempChaptersList).let { Observable.just(it) }
            }
        }
    }

    private fun chapterListRequest(branch: Long, page: Number): Response =
        client.newCall(
            GET(
                "$baseUrl/api/titles/chapters/?branch_id=$branch&page=$page&count=300",
                headers,
            ),
        ).execute().run {
            if (!isSuccessful) {
                close()
                throw Exception("HTTP error $code")
            }
            this
        }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException("chapterListParse(response: Response, manga: SManga)")

    private fun chapterListParse(response: Response, manga: SManga, exChapters: List<ExBookDto>): List<SChapter> {
        val chapters = json.decodeFromString<SeriesWrapperDto<List<BookDto>>>(response.body.string()).content

        val chaptersList = chapters.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.chapter.split(".").take(2).joinToString(".").toFloat()
                url = "/manga/${manga.url.substringAfterLast("/api/titles/")}ch${chapter.id}"
                date_upload = parseDate(chapter.upload_date)
                scanlator = if (chapter.publishers.isNotEmpty()) {
                    chapter.publishers.joinToString { it.name }
                } else {
                    null
                }

                var exChID = exChapters.find { (it.id == chapter.id) || ((it.tome == chapter.tome) && (it.chapter == chapter.chapter)) }
                if (preferences.getBoolean(exPAID_PREF, true)) {
                    if (chapter.is_paid and (chapter.is_bought != true)) {
                        if (exChID != null) {
                            url = "/chapter?id=${exChID.id}"
                            scanlator = "exmanga"
                        }
                    }

                    if (chapter.is_paid and (chapter.is_bought == true)) {
                        url = "$url#is_bought"
                    }
                } else {
                    exChID = null
                }

                var chapterName = "${chapter.tome}. Глава ${chapter.chapter}"
                if (chapter.is_paid and (chapter.is_bought != true) and (exChID == null)) {
                    chapterName += " \uD83D\uDCB2 "
                }
                if (chapter.name.isNotBlank()) {
                    chapterName += " ${chapter.name.capitalize()}"
                }

                name = chapterName
            }
        }
        return chaptersList
    }

    private fun fixLink(link: String): String {
        if (!link.startsWith("http")) {
            return baseUrl.replace("api.", "") + link
        }
        return link
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun pageListParse(response: Response, chapter: SChapter): List<Page> {
        val body = response.body.string()
        val heightEmptyChunks = 10
        if (chapter.scanlator.equals("exmanga")) {
            try {
                val exPage = json.decodeFromString<ExWrapperDto<List<List<PagesDto>>>>(body)
                val result = mutableListOf<Page>()
                exPage.data.forEach {
                    it.filter { page -> page.height > heightEmptyChunks }.forEach { page ->
                        result.add(Page(result.size, "", page.link))
                    }
                }
                return result
            } catch (e: SerializationException) {
                throw IOException("Главы больше нет на ExManga. Попробуйте обновить список глав (свайп сверху).")
            }
        } else {
            if (chapter.url.contains("#is_bought") and (preferences.getBoolean(exPAID_PREF, true))) {
                val newHeaders = exHeaders().newBuilder()
                    .add("Content-Type", "application/json")
                    .build()
                client.newCall(
                    PUT(
                        "$exManga/chapter",
                        newHeaders,
                        body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
                    ),
                ).execute()
            }
            return try {
                val page = json.decodeFromString<SeriesWrapperDto<PageDto>>(body)
                page.content.pages.filter { it.height > heightEmptyChunks }.map {
                    Page(it.page, "", fixLink(it.link))
                }
            } catch (e: SerializationException) {
                val page = json.decodeFromString<SeriesWrapperDto<ChunksPageDto>>(body)
                val result = mutableListOf<Page>()
                page.content.pages.forEach {
                    it.filter { page -> page.height > heightEmptyChunks }.forEach { page ->
                        result.add(Page(result.size, "", fixLink(page.link)))
                    }
                }
                return result
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("pageListParse(response: Response, urlRequest: String)")

    override fun pageListRequest(chapter: SChapter): Request {
        return if (chapter.scanlator.equals("exmanga")) {
            GET(exManga + chapter.url, exHeaders())
        } else {
            if (chapter.name.contains("\uD83D\uDCB2")) {
                val noEX = if (preferences.getBoolean(exPAID_PREF, true)) {
                    "Расширение отправляет данные на удаленный сервер ExManga только при открытии глав покупаемой манги."
                } else { "Функции ExManga отключены." }
                throw IOException("Глава платная. $noEX")
            }
            GET(baseUrl + "/api/titles/chapters/" + chapter.url.substringAfterLast("/ch").substringBefore("#is_bought") + "/", headers)
        }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                pageListParse(response, chapter)
            }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return if (chapter.scanlator.equals("exmanga")) exManga + chapter.url else baseUrl.replace("api.", "") + chapter.url.substringBefore("#is_bought")
    }

    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    override fun imageUrlRequest(page: Page): Request = throw NotImplementedError("Unused")

    override fun imageUrlParse(response: Response): String = throw NotImplementedError("Unused")

    private fun searchMangaByIdRequest(id: String): Request {
        return GET("$baseUrl/api/titles/$id/", headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_SLUG_SEARCH)
            client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .map { response ->
                    val details = mangaDetailsParse(response)
                    details.url = "/api/titles/$realQuery/"
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

    override fun imageRequest(page: Page): Request {
        val refererHeaders = headersBuilder().build()
        return if (page.imageUrl!!.contains(exManga)) {
            GET(page.imageUrl!!, exHeaders())
        } else {
            GET(page.imageUrl!!, refererHeaders)
        }
    }

    private class SearchFilter(name: String, val id: String) : Filter.TriState(name)
    private class CheckFilter(name: String, val id: String) : Filter.CheckBox(name)

    private class CategoryList(categories: List<SearchFilter>) : Filter.Group<SearchFilter>("Категории", categories)
    private class TypeList(types: List<SearchFilter>) : Filter.Group<SearchFilter>("Типы", types)
    private class StatusList(statuses: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус", statuses)
    private class GenreList(genres: List<SearchFilter>) : Filter.Group<SearchFilter>("Жанры", genres)
    private class AgeList(ages: List<CheckFilter>) : Filter.Group<CheckFilter>("Возрастное ограничение", ages)

    override fun getFilterList() = FilterList(
        RequireEX(),
        OrderBy(),
        GenreList(getGenreList()),
        CategoryList(getCategoryList()),
        TypeList(getTypeList()),
        StatusList(getStatusList()),
        AgeList(getAgeList()),
        MyList(MyStatus),
        RequireChapters(),
    )

    private class OrderBy : Filter.Sort(
        "Сортировка",
        arrayOf("Новизне", "Последним обновлениям", "Популярности", "Лайкам", "Просмотрам", "По кол-ву глав", "Мне повезет"),
        Selection(2, false),
    )

    private fun getAgeList() = listOf(
        CheckFilter("Для всех", "0"),
        CheckFilter("16+", "1"),
        CheckFilter("18+", "2"),
    )

    private fun getTypeList() = listOf(
        SearchFilter("Манга", "0"),
        SearchFilter("Манхва", "1"),
        SearchFilter("Маньхуа", "2"),
        SearchFilter("Западный комикс", "3"),
        SearchFilter("Рукомикс", "4"),
        SearchFilter("Индонезийский комикс", "5"),
        SearchFilter("Другое", "6"),
    )

    private fun getStatusList() = listOf(
        CheckFilter("Закончен", "0"),
        CheckFilter("Продолжается", "1"),
        CheckFilter("Заморожен", "2"),
        CheckFilter("Нет переводчика", "3"),
        CheckFilter("Анонс", "4"),
        CheckFilter("Лицензировано", "5"),
    )

    private fun getCategoryList() = listOf(
        SearchFilter("веб", "5"),
        SearchFilter("в цвете", "6"),
        SearchFilter("ёнкома", "8"),
        SearchFilter("сборник", "10"),
        SearchFilter("сингл", "11"),
        SearchFilter("алхимия", "47"),
        SearchFilter("ангелы", "48"),
        SearchFilter("антигерой", "26"),
        SearchFilter("антиутопия", "49"),
        SearchFilter("апокалипсис", "50"),
        SearchFilter("аристократия", "117"),
        SearchFilter("армия", "51"),
        SearchFilter("артефакты", "52"),
        SearchFilter("амнезия / потеря памяти", "123"),
        SearchFilter("боги", "45"),
        SearchFilter("борьба за власть", "52"),
        SearchFilter("будущее", "55"),
        SearchFilter("бои на мечах", "122"),
        SearchFilter("вампиры", "112"),
        SearchFilter("вестерн", "56"),
        SearchFilter("видеоигры", "35"),
        SearchFilter("виртуальная реальность", "44"),
        SearchFilter("владыка демонов", "57"),
        SearchFilter("военные", "29"),
        SearchFilter("волшебные существа", "59"),
        SearchFilter("воспоминания из другого мира", "60"),
        SearchFilter("врачи / доктора", "116"),
        SearchFilter("выживание", "41"),
        SearchFilter("горничные", "23"),
        SearchFilter("гяру", "28"),
        SearchFilter("гг женщина", "63"),
        SearchFilter("гг мужчина", "64"),
        SearchFilter("умный гг", "111"),
        SearchFilter("тупой гг", "109"),
        SearchFilter("гг имба", "110"),
        SearchFilter("гг не человек", "123"),
        SearchFilter("грузовик-сан", "125"),
        SearchFilter("геймеры", "61"),
        SearchFilter("гильдии", "62"),
        SearchFilter("гоблины", "65"),
        SearchFilter("девушки-монстры", "37"),
        SearchFilter("демоны", "15"),
        SearchFilter("драконы", "66"),
        SearchFilter("дружба", "67"),
        SearchFilter("жестокий мир", "69"),
        SearchFilter("животные компаньоны", "70"),
        SearchFilter("завоевание мира", "71"),
        SearchFilter("зверолюди", "19"),
        SearchFilter("зомби", "14"),
        SearchFilter("игровые элементы", "73"),
        SearchFilter("исекай", "115"),
        SearchFilter("квесты", "75"),
        SearchFilter("космос", "76"),
        SearchFilter("кулинария", "16"),
        SearchFilter("культивация", "18"),
        SearchFilter("лоли", "108"),
        SearchFilter("магическая академия", "78"),
        SearchFilter("магия", "22"),
        SearchFilter("мафия", "24"),
        SearchFilter("медицина", "17"),
        SearchFilter("месть", "79"),
        SearchFilter("монстры", "38"),
        SearchFilter("музыка", "39"),
        SearchFilter("навыки / способности", "80"),
        SearchFilter("наёмники", "81"),
        SearchFilter("насилие / жестокость", "82"),
        SearchFilter("нежить", "83"),
        SearchFilter("ниндзя", "30"),
        SearchFilter("офисные работники", "40"),
        SearchFilter("обратный гарем", "40"),
        SearchFilter("оборотни", "113"),
        SearchFilter("пародия", "85"),
        SearchFilter("подземелья", "86"),
        SearchFilter("политика", "87"),
        SearchFilter("полиция", "32"),
        SearchFilter("преступники / криминал", "36"),
        SearchFilter("призраки / духи", "27"),
        SearchFilter("прокачка", "118"),
        SearchFilter("путешествия во времени", "43"),
        SearchFilter("разумные расы", "88"),
        SearchFilter("ранги силы", "68"),
        SearchFilter("реинкарнация", "13"),
        SearchFilter("роботы", "89"),
        SearchFilter("рыцари", "90"),
        SearchFilter("средневековье", "25"),
        SearchFilter("самураи", "33"),
        SearchFilter("система", "91"),
        SearchFilter("скрытие личности", "93"),
        SearchFilter("спасение мира", "94"),
        SearchFilter("стимпанк", "92"),
        SearchFilter("супергерои", "95"),
        SearchFilter("традиционные игры", "34"),
        SearchFilter("учитель / ученик", "96"),
        SearchFilter("управление территорией", "114"),
        SearchFilter("философия", "97"),
        SearchFilter("хентай", "12"),
        SearchFilter("хикикомори", "21"),
        SearchFilter("шантаж", "99"),
        SearchFilter("эльфы", "46"),
    )

    private fun getGenreList() = listOf(
        SearchFilter("боевые искусства", "3"),
        SearchFilter("гарем", "5"),
        SearchFilter("гендерная интрига", "6"),
        SearchFilter("героическое фэнтези", "7"),
        SearchFilter("детектив", "8"),
        SearchFilter("дзёсэй", "9"),
        SearchFilter("додзинси", "10"),
        SearchFilter("драма", "11"),
        SearchFilter("история", "13"),
        SearchFilter("киберпанк", "14"),
        SearchFilter("кодомо", "15"),
        SearchFilter("комедия", "50"),
        SearchFilter("махо-сёдзё", "17"),
        SearchFilter("меха", "18"),
        SearchFilter("мистика", "19"),
        SearchFilter("мурим", "51"),
        SearchFilter("научная фантастика", "20"),
        SearchFilter("повседневность", "21"),
        SearchFilter("постапокалиптика", "22"),
        SearchFilter("приключения", "23"),
        SearchFilter("психология", "24"),
        SearchFilter("психодел-упоротость-треш", "124"),
        SearchFilter("романтика", "25"),
        SearchFilter("сверхъестественное", "27"),
        SearchFilter("сёдзё", "28"),
        SearchFilter("сёдзё-ай", "29"),
        SearchFilter("сёнэн", "30"),
        SearchFilter("сёнэн-ай", "31"),
        SearchFilter("спорт", "32"),
        SearchFilter("сэйнэн", "33"),
        SearchFilter("трагедия", "34"),
        SearchFilter("триллер", "35"),
        SearchFilter("ужасы", "36"),
        SearchFilter("фантастика", "37"),
        SearchFilter("фэнтези", "38"),
        SearchFilter("школьная жизнь", "39"),
        SearchFilter("экшен", "2"),
        SearchFilter("элементы юмора", "16"),
        SearchFilter("эротика", "42"),
        SearchFilter("этти", "40"),
        SearchFilter("юри", "41"),
    )
    private class MyList(favorites: Array<String>) : Filter.Select<String>("Закладки (только)", favorites)
    private data class MyListUnit(val name: String, val id: String)
    private val MyStatus = getMyList().map {
        it.name
    }.toTypedArray()

    private fun getMyList() = listOf(
        MyListUnit("Каталог", "-"),
        MyListUnit("Читаю", "1"),
        MyListUnit("Буду читать", "2"),
        MyListUnit("Прочитано", "3"),
        MyListUnit("Брошено ", "4"),
        MyListUnit("Отложено", "5"),
        MyListUnit("Не интересно ", "6"),
    )

    private class RequireChapters : Filter.Select<String>(
        "Только проекты с главами",
        arrayOf("Да", "Все"),
    )

    private class RequireEX : Filter.Select<String>(
        "Использовать поиск",
        arrayOf("Remanga", "ExManga(без фильтров)"),
    )
    private var isEng: String? = preferences.getString(LANGUAGE_PREF, "eng")
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val userAgentSystem = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = userAgent_PREF
            title = "User-Agent приложения"
            summary = "Использует User-Agent приложения, прописанный в настройках приложения (Настройки -> Дополнительно)"
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val warning = "Для смены User-Agent(а) необходимо перезапустить приложение с полной остановкой."
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }

        val domainPref = ListPreference(screen.context).apply {
            key = DOMAIN_PREF
            title = "Выбор домена"
            entries = arrayOf("Основной (remanga.org)", "Основной (api.remanga.org)", "Зеркало (реманга.орг)", "Зеркало (api.реманга.орг)")
            entryValues = arrayOf(baseOrig.replace("api.", ""), baseOrig, baseMirr.replace("api.", ""), baseMirr)
            summary = "%s"
            setDefaultValue(baseOrig.replace("api.", ""))
            setOnPreferenceChangeListener { _, newValue ->
                val warning = "Для смены домена необходимо перезапустить приложение с полной остановкой."
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }
        val titleLanguagePref = ListPreference(screen.context).apply {
            key = LANGUAGE_PREF
            title = "Выбор языка на обложке"
            entries = arrayOf("Английский", "Русский")
            entryValues = arrayOf("eng", "rus")
            summary = "%s"
            setDefaultValue("eng")
            setOnPreferenceChangeListener { _, newValue ->
                val warning = "Если язык обложки не изменился очистите базу данных в приложении (Настройки -> Дополнительно -> Очистить базу данных)"
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }
        val paidChapterShow = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = PAID_PREF
            title = "Показывать все платные главы"
            summary = "Показывает не купленные\uD83D\uDCB2 главы(может вызвать ошибки при обновлении/автозагрузке)"
            setDefaultValue(false)
        }
        val exChapterShow = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = exPAID_PREF
            title = "Показывать главы из ExManga"
            summary = "Показывает главы купленные другими людьми и поделившиеся ими через браузерное расширение ExManga. \n\n" +
                "ⓘЧастично отображает не купленные\uD83D\uDCB2 главы для соблюдения порядка глав. \n\n" +
                "ⓘТакже отправляет купленные главы из Tachiyomi в ExManga."
            setDefaultValue(true)
        }
        val domainExPref = ListPreference(screen.context).apply {
            key = exDOMAIN_PREF
            title = "Выбор домена для ExManga"
            entries = arrayOf("Россия (exmanga.ru)", "Украина (ex.euromc.com.ua)")
            entryValues = arrayOf(baseRuss, baseUkr)
            summary = "%s"
            setDefaultValue(baseRuss)
            setOnPreferenceChangeListener { _, newValue ->
                val warning = "Для смены домена необходимо перезапустить приложение с полной остановкой."
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }
        val bookmarksHide = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = isLib_PREF
            title = "Скрыть «Закладки»"
            summary = "Скрывает мангу находящуюся в закладках пользователя на сайте."
            setDefaultValue(false)
        }

        val boostLoad = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = bLoad_PREF
            title = "Ускорить скачивание глав"
            summary = "Увеличивает количество скачиваемых страниц в секунду, но Remanga быстрее ограничит скорость скачивания."
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val warning = "Для применения настройки необходимо перезапустить приложение с полной остановкой."
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }

        screen.addPreference(userAgentSystem)
        screen.addPreference(domainPref)
        screen.addPreference(titleLanguagePref)
        screen.addPreference(paidChapterShow)
        screen.addPreference(exChapterShow)
        screen.addPreference(domainExPref)
        screen.addPreference(bookmarksHide)
        screen.addPreference(boostLoad)
    }

    private val json: Json by injectLazy()

    companion object {

        private var USER_ID = ""

        const val PREFIX_SLUG_SEARCH = "slug:"

        private const val userAgent_PREF = "UAgent"

        private const val bLoad_PREF = "boostLoad_PREF"

        private const val DOMAIN_PREF = "REMangaDomain"

        private const val exDOMAIN_PREF = "EXMangaDomain"

        private const val LANGUAGE_PREF = "ReMangaTitleLanguage"

        private const val PAID_PREF = "PaidChapter"

        private const val exPAID_PREF = "ExChapter"

        private const val isLib_PREF = "LibBookmarks"
    }
}
