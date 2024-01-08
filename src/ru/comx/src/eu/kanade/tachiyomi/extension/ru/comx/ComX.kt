package eu.kanade.tachiyomi.extension.ru.comx

import android.webkit.CookieManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class ComX : ParsedHttpSource() {

    private val json: Json by injectLazy()

    override val name = "Com-X"

    override val baseUrl = "https://com-x.life"

    override val lang = "ru"

    override val supportsLatest = true
    private val cookieManager by lazy { CookieManager.getInstance() }
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(3)
        .cookieJar(
            object : CookieJar {
                // Syncs okhttp with WebView cookies, allowing logged-in users do logged-in stuff
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    for (cookie in cookies) {
                        cookieManager.setCookie(url.toString(), cookie.toString())
                    }
                }

                override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {
                    val cookiesString = cookieManager.getCookie(url.toString())

                    if (cookiesString != null && cookiesString.isNotEmpty()) {
                        val cookieHeaders = cookiesString.split("; ").toList()
                        val cookies = mutableListOf<Cookie>()
                        for (header in cookieHeaders) {
                            cookies.add(Cookie.parse(url, header)!!)
                        }
                        // Adds age verification cookies to access mature comics
                        return if (url.toString().contains("/reader/")) {
                            cookies.apply {
                                add(
                                    Cookie.Builder()
                                        .domain(baseUrl.substringAfter("//"))
                                        .path("/")
                                        .name("adult")
                                        .value(
                                            url.toString().substringAfter("/reader/")
                                                .substringBefore("/"),
                                        )
                                        .build(),
                                )
                            }
                        } else {
                            cookies
                        }
                    } else {
                        return mutableListOf()
                    }
                }
            },
        )
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val response = chain.proceed(originalRequest)
            if (response.code == 404 && response.asJsoup().toString().contains("Protected by Batman")) {
                throw IOException("Antibot, попробуйте пройти капчу в WebView")
            }
            response
        }
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Tachiyomi")
        .add("Referer", baseUrl)

    // Popular
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", getFilterList())

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun popularMangaSelector() = "div.short"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = baseUrl + element.select("img").first()!!.attr("data-src")
        element.select(".readed__title a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            //  Russian's titles prevails. +Site bad search English titles.
            manga.title = it.text().replace(" / ", " | ").split(" | ").last().trim()
        }
        return manga
    }

    override fun popularMangaNextPageSelector(): Nothing? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    override fun latestUpdatesSelector() = "ul#content-load li.latest"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = baseUrl + element.select("img").first()!!.attr("src").replace("mini/mini", "mini/mid")
        element.select("a.latest__title").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            //  Russian's titles prevails. +Site bad search English titles.
            manga.title = it.text().replace(" / ", " | ").split(" | ").last().trim()
        }
        return manga
    }
    override fun latestUpdatesNextPageSelector(): Nothing? = null

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return POST(
                "$baseUrl/index.php?do=search",
                body = FormBody.Builder()
                    .add("do", "search")
                    .add("subaction", "search")
                    .add("story", query)
                    .add("search_start", page.toString())
                    .build(),
                headers = headers,
            )
        }
        val mutableGenre = mutableListOf<String>()
        val mutableType = mutableListOf<String>()
        val mutableAge = mutableListOf<String>()
        var orderBy = "rating"
        var ascEnd = "desc"
        val sectionPub = mutableListOf<String>()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    orderBy = arrayOf("date", "rating", "news_read", "comm_num", "title")[filter.state!!.index]
                    ascEnd = if (filter.state!!.ascending) "asc" else "desc"
                }
                is AgeList -> filter.state.forEach { age ->
                    if (age.state) {
                        mutableAge += age.id
                    }
                }
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state) {
                        mutableGenre += genre.id
                    }
                }
                is TypeList -> filter.state.forEach { type ->
                    if (type.state) {
                        mutableType += type.id
                    }
                }
                is PubList -> filter.state.forEach { publisher ->
                    if (publisher.state) {
                        sectionPub += publisher.id
                    }
                }
                else -> {}
            }
        }
        val pageParameter = if (page > 1) "page/$page/" else ""
        return POST(
            "$baseUrl/ComicList/p.cat=${sectionPub.joinToString(",")}/g=${mutableGenre.joinToString(",")}/t=${mutableType.joinToString(",")}/adult=${mutableAge.joinToString(",")}/$pageParameter",
            body = FormBody.Builder()
                .add("dlenewssortby", orderBy)
                .add("dledirection", ascEnd)
                .add("set_new_sort", "dle_sort_xfilter")
                .add("set_direction_sort", "dle_direction_xfilter")
                .build(),
            headers = headers,
        )
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaParse(response: Response) = popularMangaParse(response)
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): Nothing? = null

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.page__grid").first()!!

        val ratingValue = infoElement.select(".page__activity-votes").textNodes().first().text().trim().toFloat() * 2
        val ratingVotes = infoElement.select(".page__activity-votes span > span").first()!!.text().trim()
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
        val rawCategory = document.select(".speedbar a").last()!!.text().trim()
        val category = when (rawCategory.lowercase()) {
            "manga" -> "Манга"
            "manhwa" -> "Манхва"
            "manhua" -> "Маньхуа"
            else -> "Комикс"
        }
        val rawAgeStop = if (document.toString().contains("ВНИМАНИЕ! 18+")) "18+" else ""
        val manga = SManga.create()
        manga.title = infoElement.select(".page__header h1").text().trim()
        manga.author = infoElement.select(".page__list li:contains(Издатель)").text()
        manga.genre = category + ", " + rawAgeStop + ", " + infoElement.select(".page__tags a").joinToString { it.text() }
        manga.status = parseStatus(infoElement.select(".page__list li:contains(Статус)").text())

        manga.description = infoElement.select(".page__title-original").text().trim() + "\n" +
            if (document.select(".page__list li:contains(Тип выпуска)").text().contains("!!! События в комиксах - ХРОНОЛОГИЯ !!!")) { "Cобытие в комиксах - ХРОНОЛОГИЯ\n" } else { "" } +
            ratingStar + " " + ratingValue + " (голосов: " + ratingVotes + ")\n" +
            infoElement.select(".page__text ").first()?.html()?.let { Jsoup.parse(it) }
                ?.select("body:not(:has(p)),p,br")
                ?.prepend("\\n")?.text()?.replace("\\n", "\n")?.replace("\n ", "\n")
                .orEmpty()

        val src = infoElement.select(".img-wide img").let {
            it.attr("data-src").ifEmpty { it.attr("src") }
        }

        if (src.contains("://")) {
            manga.thumbnail_url = src
        } else {
            manga.thumbnail_url = baseUrl + src
        }
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Продолжается") ||
            element.contains(" из ") ||
            element.contains("Онгоинг") -> SManga.ONGOING
        element.contains("Заверш") ||
            element.contains("Лимитка") ||
            element.contains("Ван шот") ||
            element.contains("Графический роман") -> SManga.COMPLETED

        else -> SManga.UNKNOWN
    }

    // Chapters
    override fun chapterListSelector() = throw NotImplementedError("Unused")

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val dataStr = document
            .toString()
            .substringAfter("window.__DATA__ = ")
            .substringBefore("</script>")
            .substringBeforeLast(";")

        val data = json.decodeFromString<JsonObject>(dataStr)
        val chaptersList = data["chapters"]?.jsonArray
        val isEvent = document.select(".page__list li:contains(Тип выпуска)").text()
            .contains("!!! События в комиксах - ХРОНОЛОГИЯ !!!")

        val chapters: List<SChapter>? = chaptersList?.map {
            val chapter = SChapter.create()
            // Usually "title" is main chapter name info, "title_en" is additional chapter name info.
            // I decided to keep them both because who knows where they decided to put useful info today.
            // Except when they are the same.
            chapter.name = if (it.jsonObject["title"]!!.jsonPrimitive.content == it.jsonObject["title_en"]!!.jsonPrimitive.content) {
                it.jsonObject["title"]!!.jsonPrimitive.content
            } else {
                (it.jsonObject["title"]!!.jsonPrimitive.content + " " + it.jsonObject["title_en"]!!.jsonPrimitive.content).trim()
            }
            chapter.date_upload = simpleDateFormat.parse(it.jsonObject["date"]!!.jsonPrimitive.content)?.time ?: 0L
            chapter.chapter_number = it.jsonObject["posi"]!!.jsonPrimitive.float
            // when it is Event add reading order numbers as prefix
            if (isEvent) {
                chapter.name = chapter.chapter_number.toInt().toString() + " " + chapter.name
            }
            chapter.setUrlWithoutDomain("/readcomix/" + data["news_id"] + "/" + it.jsonObject["id"]!!.jsonPrimitive.content + ".html")
            chapter
        }
        return chapters ?: emptyList()
    }

    override fun chapterFromElement(element: Element): SChapter =
        throw NotImplementedError("Unused")

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()

        // Comics 18+
        if (html.contains("adult__header")) {
            throw Exception("Комикс 18+ (что-то сломалось)")
        }

        val baseImgUrl = "https://img.com-x.life/comix/"

        val beginTag = "\"images\":["
        val beginIndex = html.indexOf(beginTag)
        val endIndex = html.indexOf("]", beginIndex)

        val urls: List<String> = html.substring(beginIndex + beginTag.length, endIndex)
            .split(',').map {
                val img = it.replace("\\", "").replace("\"", "")
                baseImgUrl + img
            }

        val pages = mutableListOf<Page>()
        for (i in urls.indices) {
            pages.add(Page(i, "", urls[i]))
        }

        return pages
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservable().doOnNext { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404 && response.asJsoup().toString().contains("Выпуск был удален по требованию правообладателя")) {
                        throw Exception("Лицензировано. Возможно может помочь авторизация через WebView")
                    } else {
                        throw Exception("HTTP error ${response.code}")
                    }
                }
            }
            .map { response ->
                pageListParse(response)
            }
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }

    // Filters
    private class OrderBy : Filter.Sort(
        "Сортировать по",
        arrayOf("Дате", "Популярности", "Посещаемости", "Комментариям", "Алфавиту"),
        Selection(1, false),
    )

    private class CheckFilter(name: String, val id: String) : Filter.CheckBox(name)

    private class PubList(publishers: List<CheckFilter>) : Filter.Group<CheckFilter>("Разделы", publishers)
    private class GenreList(genres: List<CheckFilter>) : Filter.Group<CheckFilter>("Жанры", genres)
    private class TypeList(types: List<CheckFilter>) : Filter.Group<CheckFilter>("Тип выпуска", types)
    private class AgeList(ages: List<CheckFilter>) : Filter.Group<CheckFilter>("Возрастное ограничение", ages)

    override fun getFilterList() = FilterList(
        OrderBy(),
        PubList(getPubList()),
        GenreList(getGenreList()),
        TypeList(getTypeList()),
        AgeList(getAgeList()),
    )

    private fun getAgeList() = listOf(
        CheckFilter("Для всех", "1"),
        CheckFilter("18+", "2"),
    )

    private fun getTypeList() = listOf(
        CheckFilter("События в комиксах", "1"),
        CheckFilter("Аннуалы", "3"),
        CheckFilter("Артбук", "4"),
        CheckFilter("Ван-шот", "7"),
        CheckFilter("Гайд", "15"),
        CheckFilter("Графический Роман", "17"),
        CheckFilter("Комикс Стрип", "19"),
        CheckFilter("Лимитка", "21"),
        CheckFilter("Макси-серия", "25"),
        CheckFilter("Мини-серия", "27"),
        CheckFilter("Онгоинг", "28"),
        CheckFilter("Рассказ", "29"),
        CheckFilter("Роман", "30"),
        CheckFilter("Сборник", "31"),
        CheckFilter("Серия", "32"),
        CheckFilter("Спешл", "35"),
        CheckFilter("Энциклопедия", "36"),
    )

    private fun getPubList() = listOf(
        CheckFilter("Манга", "3"),
        CheckFilter("Маньхуа", "45"),
        CheckFilter("Манхва", "44"),
        CheckFilter("Разные комиксы", "18"),
        CheckFilter("Aftershock", "50"),
        CheckFilter("Avatar Press", "11"),
        CheckFilter("Boom! Studios", "12"),
        CheckFilter("Dark Horse", "7"),
        CheckFilter("DC Comics", "14"),
        CheckFilter("Dynamite Entertainment", "10"),
        CheckFilter("Icon Comics", "16"),
        CheckFilter("IDW Publishing", "6"),
        CheckFilter("Image", "4"),
        CheckFilter("Marvel", "2"),
        CheckFilter("Oni Press", "13"),
        CheckFilter("Top Cow", "9"),
        CheckFilter("Valiant", "15"),
        CheckFilter("Vertigo", "8"),
        CheckFilter("Wildstorm", "5"),
        CheckFilter("Zenescope", "51"),
    )

    private fun getGenreList() = listOf(
        CheckFilter("Автобиографическая новелла", "9"),
        CheckFilter("Альтернативная история", "10"),
        CheckFilter("Антиутопия", "11"),
        CheckFilter("Апокалипсис", "12"),
        CheckFilter("Артбук", "14"),
        CheckFilter("Афрофутуризм", "15"),
        CheckFilter("Биография", "16"),
        CheckFilter("Боевик", "17"),
        CheckFilter("Боевые искусства", "18"),
        CheckFilter("Вампиры", "19"),
        CheckFilter("Вестерн", "20"),
        CheckFilter("Военный", "21"),
        CheckFilter("Гарем", "22"),
        CheckFilter("Гендерная интрига", "23"),
        CheckFilter("Героическое фэнтези", "24"),
        CheckFilter("Детектив", "25"),
        CheckFilter("Детский", "26"),
        CheckFilter("Дзёсэй", "27"),
        CheckFilter("Документальный", "28"),
        CheckFilter("Драма", "29"),
        CheckFilter("Единоборства", "30"),
        CheckFilter("Жизнь", "31"),
        CheckFilter("Зомби", "32"),
        CheckFilter("Игра", "33"),
        CheckFilter("Игры", "124"),
        CheckFilter("Исекай", "35"),
        CheckFilter("Исэкай", "38"),
        CheckFilter("Исторический", "36"),
        CheckFilter("История", "37"),
        CheckFilter("Квест", "39"),
        CheckFilter("Киберпанк", "40"),
        CheckFilter("Кодомо", "41"),
        CheckFilter("Комедия", "42"),
        CheckFilter("Комелия", "43"),
        CheckFilter("Космоопера", "44"),
        CheckFilter("Космос", "45"),
        CheckFilter("Криминал", "46"),
        CheckFilter("Криптоистория", "47"),
        CheckFilter("ЛГБТ", "48"),
        CheckFilter("Магия", "49"),
        CheckFilter("Мелодрама", "50"),
        CheckFilter("Меха", "51"),
        CheckFilter("Мистика", "52"),
        CheckFilter("Музыка", "54"),
        CheckFilter("Научная фантастика", "55"),
        CheckFilter("Неотвратимость", "56"),
        CheckFilter("Нуар", "57"),
        CheckFilter("Омегаверс", "58"),
        CheckFilter("Паника", "59"),
        CheckFilter("Пародия", "60"),
        CheckFilter("Пираты", "61"),
        CheckFilter("Повседневность", "62"),
        CheckFilter("Политика", "63"),
        CheckFilter("Постапокалиптика", "64"),
        CheckFilter("Предатель среди нас", "65"),
        CheckFilter("Приключения", "67"),
        CheckFilter("Приступления", "69"),
        CheckFilter("Психические отклонения", "70"),
        CheckFilter("Психоделика", "71"),
        CheckFilter("Психология", "73"),
        CheckFilter("Путешествия во времени", "74"),
        CheckFilter("Религия", "75"),
        CheckFilter("Романтика", "76"),
        CheckFilter("Самурайский боевик", "77"),
        CheckFilter("Сверхъестественное", "78"),
        CheckFilter("Сейнен", "79"),
        CheckFilter("Симбиоты", "80"),
        CheckFilter("Сказка", "81"),
        CheckFilter("Слэшер", "82"),
        CheckFilter("Смерть", "83"),
        CheckFilter("Спорт", "84"),
        CheckFilter("Стимпанк", "85"),
        CheckFilter("Супергероика", "87"),
        CheckFilter("Сэйнэн", "90"),
        CheckFilter("Сянься", "91"),
        CheckFilter("Сёдзё", "93"),
        CheckFilter("Сёдзё-ай", "94"),
        CheckFilter("Сёнэн", "96"),
        CheckFilter("Сёнэн-ай", "97"),
        CheckFilter("Трагедия", "98"),
        CheckFilter("Тревога", "99"),
        CheckFilter("Триллер", "100"),
        CheckFilter("Тюремная драма", "101"),
        CheckFilter("Ужасы", "102"),
        CheckFilter("Фантасмагория", "103"),
        CheckFilter("Фантасмогория", "104"),
        CheckFilter("Фантастика", "105"),
        CheckFilter("Фэнтези", "106"),
        CheckFilter("Хоррор", "109"),
        CheckFilter("Черный юмор", "110"),
        CheckFilter("Школа", "111"),
        CheckFilter("Школьная жизнь", "123"),
        CheckFilter("Шпионский", "113"),
        CheckFilter("Экшен", "122"),
        CheckFilter("Экшн", "115"),
        CheckFilter("Эротика", "116"),
        CheckFilter("Этти", "117"),
        CheckFilter("Юмор", "118"),
        CheckFilter("Юри", "119"),
        CheckFilter("Яой", "120"),
        CheckFilter("Ёнкома", "121"),
    )

    companion object {
        private val simpleDateFormat by lazy { SimpleDateFormat("dd.MM.yyyy", Locale.US) }
    }
}
