package eu.kanade.tachiyomi.multisrc.libgroup

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
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
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
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

    private val siteId = when (name) {
        "MangaLib" -> "1"
        "YaoiLib" -> "2"
        "HentaiLib" -> "4"
        else -> "-"
    }

    private val baseImage = when (name) {
        "MangaLib" -> "https://img33.imgslib.link"
        "YaoiLib" -> "https://img2.mixlib.me"
        "HentaiLib" -> "https://img2.hentaicdn.org"
        else -> "-"
    }

    private val baseApi = "https://api.lib.social/api/manga?site_id%5B%5D=$siteId"

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

    private fun access_token(): String? {
        val cookies = client.cookieJar.loadForRequest("https://auth.lib.social".toHttpUrl()) +
            client.cookieJar.loadForRequest("https://lib.social".toHttpUrl())
        return cookies
            .firstOrNull { cookie -> cookie.name.startsWith("remember_web") || cookie.name == "XSRF-TOKEN" }
            ?.let { cookie -> URLDecoder.decode(cookie.value, "UTF-8") }
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .connectTimeout(5, TimeUnit.MINUTES)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addNetworkInterceptor { imageContentTypeIntercept(it) }
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 419) {
                throw IOException("HTTP error ${response.code}. Проверьте сайт. Для завершения авторизации необходимо перезапустить приложение с полной остановкой.")
            }
            if (response.code == 404) {
                throw IOException("HTTP error ${response.code}. Проверьте сайт. Попробуйте авторизоваться через WebView\uD83C\uDF0E︎ и обновите список глав.")
            }
            response
        }
        .build()

    private val userAgentMobile = "Mozilla/5.0 (Linux; Android 10; SM-G980F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Mobile Safari/537.36"

    private val userAgentRandomizer = "${Random.nextInt().absoluteValue}"

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

    protected fun apiHeaders() = Headers.Builder()
        .apply {
            add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36 Edg/100.0.$userAgentRandomizer")
            add("Accept", "application/json, text/plain, */*")
            add("Content-Type", "application/json")
            add("Site-Id", siteId)
            add("Referer", baseUrl)
            if (!access_token().isNullOrEmpty()) {
                add("Authorization", "Bearer ${access_token()}")
            }
        }
        .build()

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseApi&sort_type=desc&sort_by=last_chapter_at&page=$page&chap_count_min=1", apiHeaders())

    override fun latestUpdatesParse(response: Response) =
        popularMangaParse(response)

    // Popular
    override fun popularMangaRequest(page: Int) = GET("$baseApi&sort_type=desc&sort_by=views&page=$page&chap_count_min=1", apiHeaders())

    override fun popularMangaParse(response: Response): MangasPage {
        val page = json.decodeFromString<PageWrapperDto<MangaDetDto>>(response.body.string())
        val mangas = page.data.map {
            it.toSManga()
        }

        return MangasPage(mangas, true)
    }

    // Popular cross Latest
    private fun MangaDetDto.toSManga(): SManga {
        return SManga.create().apply {
            title = when {
                isEng.equals("rus") && rus_name!!.isNotEmpty() -> rus_name
                isEng.equals("eng") && eng_name!!.isNotEmpty() -> eng_name
                else -> name
            }
            thumbnail_url = cover.default
            if (!thumbnail_url!!.contains("://")) {
                thumbnail_url = baseUrl + thumbnail_url
            }
            url = "/$slug"
            description = summary
            genre = type?.label + ", " + genres?.joinToString { it.name } + ", " + tags?.joinToString { it.name }
            author = authors?.joinToString { it.name }
            artist = artists?.joinToString { it.name }
            val statusTranslate = scanlateStatus?.label?.lowercase() ?: ""
            val statusTitle = this@toSManga.status?.label?.lowercase() ?: ""
            status = when {
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
    }

    // Details
    override fun mangaDetailsRequest(manga: SManga) = GET(baseApi.substringBefore("?") + manga.url + "?fields[]=background&fields[]=eng_name&fields[]=otherNames&fields[]=summary&fields[]=releaseDate&fields[]=type_id&fields[]=caution&fields[]=views&fields[]=close_view&fields[]=rate_avg&fields[]=rate&fields[]=genres&fields[]=tags&fields[]=teams&fields[]=franchise&fields[]=authors&fields[]=publisher&fields[]=userRating&fields[]=moderated&fields[]=metadata&fields[]=metadata.count&fields[]=metadata.close_comments&fields[]=manga_status_id&fields[]=chap_count&fields[]=status_id&fields[]=artists&fields[]=format", apiHeaders())

    override fun mangaDetailsParse(response: Response): SManga {
        val series = json.decodeFromString<SeriesWrapperDto<MangaDetDto>>(response.body.string())
        return series.data.toSManga()
    }

    // Chapters
    override fun chapterListRequest(manga: SManga) = GET(baseApi.substringBefore("?") + manga.url + "/chapters", apiHeaders())

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga)
            }
    }
    private fun parseDate(date: String?): Long {
        date ?: return 0L
        return try {
            simpleDateFormat.parse(date)!!.time
        } catch (_: Exception) {
            Date().time
        }
    }
    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()
    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val chapter = json.decodeFromString<PageWrapperDto<BookDto>>(response.body.string())
        return chapter.data.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.number.toFloatOrNull() ?: -2F
                name = "${chapter.volume}. Глава ${chapter.number} " + (chapter.name ?: "")
                url = "${manga.url}/chapter?branch_id=${chapter.branches.firstOrNull()?.branch_id ?: -2L}&number=${chapter.number}&volume=${chapter.volume}"
                date_upload = parseDate(chapter.branches.firstOrNull()?.created_at)
                scanlator = chapter.branches.firstOrNull()?.teams?.firstOrNull()?.name
            }
        }.distinct()
    }

    private fun sortChaptersByTranslator(sortingList: String?, chaptersList: JsonArray?, slug: String, userId: String, branches: List<JsonElement>): List<SChapter>? {
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

    private fun chapterFromElement(chapterItem: JsonElement, sortingList: String?, slug: String, userId: String, teamIdParam: Int? = null, branches: List<JsonElement>? = null, teams: List<JsonElement>? = null, chaptersList: JsonArray? = null): SChapter {
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
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseApi.substringBefore("?") + chapter.url, apiHeaders())
    }
    override fun imageRequest(page: Page) = GET(page.url, imgHeader())
    override fun pageListParse(response: Response): List<Page> {
        val imageList = json.decodeFromString<SeriesWrapperDto<ChapterDto>>(response.body.string())
        return imageList.data.pages.mapIndexed { index, page ->
            Page(index, baseImage + page.url)
        }
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.url)
    }

    override fun imageUrlParse(response: Response): String = ""

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
        val url = "$baseApi&page=$page&chap_count_min=1".toHttpUrl().newBuilder()
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
                    url.addQueryParameter("sort_type", if (filter.state!!.ascending) "asc" else "desc")
                    url.addQueryParameter("sort_by", arrayOf("rate", "name", "views", "created_at", "last_chapter_at", "chap_count")[filter.state!!.index])
                }
                is MyList -> filter.state.forEach { favorite ->
                    if (favorite.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (favorite.isIncluded()) "bookmarks[include][]" else "bookmarks[exclude][]", favorite.id)
                    }
                }
                is RequireChapters -> {
                    if (filter.state == 1) {
                        url.setQueryParameter("chap_count_min", "0")
                    }
                }
                else -> {}
            }
        }
        return GET(url.toString(), apiHeaders())
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
