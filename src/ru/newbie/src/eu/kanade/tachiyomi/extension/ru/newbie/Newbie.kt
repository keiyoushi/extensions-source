package eu.kanade.tachiyomi.extension.ru.newbie

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Application
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.extension.ru.newbie.dto.BookDto
import eu.kanade.tachiyomi.extension.ru.newbie.dto.BranchesDto
import eu.kanade.tachiyomi.extension.ru.newbie.dto.LibraryDto
import eu.kanade.tachiyomi.extension.ru.newbie.dto.MangaDetDto
import eu.kanade.tachiyomi.extension.ru.newbie.dto.PageDto
import eu.kanade.tachiyomi.extension.ru.newbie.dto.PageWrapperDto
import eu.kanade.tachiyomi.extension.ru.newbie.dto.SearchLibraryDto
import eu.kanade.tachiyomi.extension.ru.newbie.dto.SearchWrapperDto
import eu.kanade.tachiyomi.extension.ru.newbie.dto.SeriesWrapperDto
import eu.kanade.tachiyomi.extension.ru.newbie.dto.SubSearchDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.random.Random

class Newbie : ConfigurableSource, HttpSource() {
    override val name = "NewManga(Newbie)"

    override val id: Long = 8033757373676218584

    override val baseUrl = "https://newmanga.org"

    override val lang = "ru"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val supportsLatest = true

    private var branches = mutableMapOf<String, List<BranchesDto>>()

    private val userAgentRandomizer = "${Random.nextInt().absoluteValue}"

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36 Edg/100.0.$userAgentRandomizer")
        .add("Referer", baseUrl)

    private fun imageContentTypeIntercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.queryParameter("slice").isNullOrEmpty()) {
            return chain.proceed(chain.request())
        }

        val response = chain.proceed(chain.request())
        val image = response.body.byteString().toResponseBody("image/*".toMediaType())
        return response.newBuilder().body(image).build()
    }

    override val client: OkHttpClient =
        network.cloudflareClient.newBuilder()
            .rateLimitHost(API_URL.toHttpUrl(), 2)
            .addInterceptor { imageContentTypeIntercept(it) }
            .build()

    private val count = 30

    override fun popularMangaRequest(page: Int) = GET("$API_URL/projects/popular?scale=month&size=$count&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val page = json.decodeFromString<PageWrapperDto<LibraryDto>>(response.body.string())
        val mangas = page.items.map {
            it.toSManga()
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    private fun LibraryDto.toSManga(): SManga {
        val o = this
        return SManga.create().apply {
            // Do not change the title name to ensure work with a multilingual catalog!
            title = if (isEng.equals("rus")) o.title.ru else o.title.en
            url = "$id"
            thumbnail_url = "$IMAGE_URL/${image.name}"
        }
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$API_URL/projects/updates?only_bookmarks=false&size=$count&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaParse(response: Response): MangasPage {
        val page = json.decodeFromString<SearchWrapperDto<SubSearchDto<SearchLibraryDto>>>(response.body.string())
        val mangas = page.result.hits.map {
            it.toSearchManga()
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    private fun SearchLibraryDto.toSearchManga(): SManga {
        return SManga.create().apply {
            // Do not change the title name to ensure work with a multilingual catalog!
            title = if (isEng.equals("rus")) document.title_ru else document.title_en
            url = document.id
            thumbnail_url = if (document.image_large.isNotEmpty()) {
                "$IMAGE_URL/${document.image_large}"
            } else {
                "$IMAGE_URL/${document.image_small}"
            }
        }
    }

    private val simpleDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US) }

    private fun parseDate(date: String?): Long {
        date ?: return 0L
        return try {
            simpleDateFormat.parse(date)!!.time
        } catch (_: Exception) {
            Date().time
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val mutableGenre = mutableListOf<String>()
        val mutableExGenre = mutableListOf<String>()
        val mutableTag = mutableListOf<String>()
        val mutableExTag = mutableListOf<String>()
        val mutableType = mutableListOf<String>()
        val mutableStatus = mutableListOf<String>()
        val mutableTitleStatus = mutableListOf<String>()
        val mutableAge = mutableListOf<String>()
        var orderBy = "MATCH"
        var ascEnd = "DESC"
        var requireChapters = true
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    if (query.isEmpty()) {
                        orderBy = arrayOf("RATING", "VIEWS", "HEARTS", "COUNT_CHAPTERS", "CREATED_AT", "UPDATED_AT")[filter.state!!.index]
                        ascEnd = if (filter.state!!.ascending) "ASC" else "DESC"
                    }
                }
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        if (genre.isIncluded()) mutableGenre += '"' + genre.name + '"' else mutableExGenre += '"' + genre.name + '"'
                    }
                }
                is TagsList -> filter.state.forEach { tag ->
                    if (tag.state != Filter.TriState.STATE_IGNORE) {
                        if (tag.isIncluded()) mutableTag += '"' + tag.name + '"' else mutableExTag += '"' + tag.name + '"'
                    }
                }
                is TypeList -> filter.state.forEach { type ->
                    if (type.state) {
                        mutableType += '"' + type.id + '"'
                    }
                }
                is StatusList -> filter.state.forEach { status ->
                    if (status.state) {
                        mutableStatus += '"' + status.id + '"'
                    }
                }
                is StatusTitleList -> filter.state.forEach { status ->
                    if (status.state) {
                        mutableTitleStatus += '"' + status.id + '"'
                    }
                }
                is AgeList -> filter.state.forEach { age ->
                    if (age.state) {
                        mutableAge += '"' + age.id + '"'
                    }
                }
                is RequireChapters -> {
                    if (filter.state == 1) {
                        requireChapters = false
                    }
                }
                else -> {}
            }
        }

        return POST(
            "https://neo.newmanga.org/catalogue",
            body = """{"query":"$query","sort":{"kind":"$orderBy","dir":"$ascEnd"},"filter":{"hidden_projects":[],"genres":{"excluded":$mutableExGenre,"included":$mutableGenre},"tags":{"excluded":$mutableExTag,"included":$mutableTag},"type":{"allowed":$mutableType},"translation_status":{"allowed":$mutableStatus},"released_year":{"min":null,"max":null},"require_chapters":$requireChapters,"original_status":{"allowed":$mutableTitleStatus},"adult":{"allowed":$mutableAge}},"pagination":{"page":$page,"size":$count}}""".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
            headers = headers,
        )
    }

    private fun parseStatus(status: String): Int {
        return when (status) {
            "completed" -> SManga.COMPLETED
            "on_going" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private fun parseType(type: String): String {
        return when (type) {
            "manga" -> "Манга"
            "manhwa" -> "Манхва"
            "manhya" -> "Маньхуа"
            "single" -> "Сингл"
            "comics" -> "Комикс"
            "russian" -> "Руманга"
            else -> type
        }
    }
    private fun parseAge(adult: String): String {
        return when (adult) {
            "" -> "0+"
            else -> "$adult+"
        }
    }

    private fun MangaDetDto.toSManga(): SManga {
        val ratingValue = DecimalFormat("#,###.##").format(rating * 2).replace(",", ".").toFloat()
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
            title = if (isEng.equals("rus")) o.title.ru else o.title.en
            url = "$id"
            thumbnail_url = "$IMAGE_URL/${image.name}"
            author = o.author?.name
            artist = o.artist?.name
            val mediaNameLanguage = if (isEng.equals("rus")) o.title.en else o.title.ru
            description = mediaNameLanguage + "\n" + ratingStar + " " + ratingValue + " [♡" + hearts + "]\n" + Jsoup.parse(o.description).text()
            genre = parseType(type) + ", " + adult?.let { parseAge(it) } + ", " + genres.joinToString { it.title.ru.capitalize() }
            status = parseStatus(o.status)
        }
    }

    private fun titleDetailsRequest(manga: SManga): Request {
        return GET(API_URL + "/projects/" + manga.url, headers)
    }

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(titleDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(baseUrl + "/p/" + manga.url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val series = json.decodeFromString<MangaDetDto>(response.body.string())
        branches[series.id.toString()] = series.branches
        return series.toSManga()
    }

    @SuppressLint("DefaultLocale")
    private fun chapterName(book: BookDto): String {
        var chapterName = "${book.tom}. Глава ${DecimalFormat("#,###.##").format(book.number).replace(",", ".")}"
        if (!book.is_available) {
            chapterName += " \uD83D\uDCB2 "
        }
        if (book.name?.isNotBlank() == true) {
            chapterName += " ${book.name.capitalize()}"
        }
        return chapterName
    }

    private fun mangaBranches(manga: SManga): List<BranchesDto> {
        val response = client.newCall(titleDetailsRequest(manga)).execute()
        val series = json.decodeFromString<MangaDetDto>(response.body.string())
        branches[series.id.toString()] = series.branches
        return series.branches
    }

    private fun selector(b: BranchesDto): Boolean = b.is_default
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val branch = branches.getOrElse(manga.url) { mangaBranches(manga) }
        return when {
            branch.isEmpty() -> {
                return Observable.just(listOf())
            }
            manga.status == SManga.LICENSED -> {
                Observable.error(Exception("Лицензировано - Нет глав"))
            }
            else -> {
                val branchId = branch.first { selector(it) }.id
                client.newCall(chapterListRequest(branchId))
                    .asObservableSuccess()
                    .map { response ->
                        chapterListParse(response, manga, branchId)
                    }
            }
        }
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException("chapterListParse(response: Response, manga: SManga)")

    private fun chapterListParse(response: Response, manga: SManga, branch: Long): List<SChapter> {
        var chapters = json.decodeFromString<SeriesWrapperDto<List<BookDto>>>(response.body.string()).items
        if (!preferences.getBoolean(PAID_PREF, false)) {
            chapters = chapters.filter { it.is_available }
        }
        return chapters.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.number
                name = chapterName(chapter)
                url = "/p/${manga.url}/$branch/r/${chapter.id}"
                date_upload = parseDate(chapter.created_at)
                scanlator = chapter.translator
            }
        }
    }
    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException("chapterListRequest(branch: Long)")
    private fun chapterListRequest(branch: Long): Request {
        return GET(
            "$API_URL/branches/$branch/chapters?reverse=true&size=1000000",
            headers,
        )
    }

    @TargetApi(Build.VERSION_CODES.N)
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(API_URL + "/chapters/${chapter.url.substringAfterLast("/")}/pages", headers)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    private fun pageListParse(response: Response, urlRequest: String): List<Page> {
        val pages = json.decodeFromString<List<PageDto>>(response.body.string())
        val result = mutableListOf<Page>()
        pages.forEach { page ->
            (1..page.slices!!).map { i ->
                result.add(Page(result.size, urlRequest + "/${page.id}?slice=$i"))
            }
        }
        return result
    }

    override fun pageListParse(response: Response): List<Page> = throw Exception("Not used")
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                pageListParse(response, pageListRequest(chapter).url.toString())
            }
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.url)
    }

    override fun imageUrlRequest(page: Page): Request = throw NotImplementedError("Unused")

    override fun imageUrlParse(response: Response): String = throw NotImplementedError("Unused")

    override fun imageRequest(page: Page): Request {
        val refererHeaders = headersBuilder().build()
        return GET(page.imageUrl!!, refererHeaders)
    }

    private class CheckFilter(name: String, val id: String) : Filter.CheckBox(name)
    private class SearchFilter(name: String) : Filter.TriState(name)

    private class TypeList(types: List<CheckFilter>) : Filter.Group<CheckFilter>("Типы", types)
    private class StatusList(statuses: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус перевода", statuses)
    private class StatusTitleList(titles: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус оригинала", titles)
    private class GenreList(genres: List<SearchFilter>) : Filter.Group<SearchFilter>("Жанры", genres)
    private class TagsList(tags: List<SearchFilter>) : Filter.Group<SearchFilter>("Теги", tags)
    private class AgeList(ages: List<CheckFilter>) : Filter.Group<CheckFilter>("Возрастное ограничение", ages)

    override fun getFilterList() = FilterList(
        OrderBy(),
        GenreList(getGenreList()),
        TagsList(getTagsList()),
        TypeList(getTypeList()),
        StatusList(getStatusList()),
        StatusTitleList(getStatusTitleList()),
        AgeList(getAgeList()),
        RequireChapters(),
    )

    private class OrderBy : Filter.Sort(
        "Сортировка",
        arrayOf("По рейтингу", "По просмотрам", "По лайкам", "По кол-ву глав", "По дате создания", "По дате обновления"),
        Selection(0, false),
    )

    private class RequireChapters : Filter.Select<String>(
        "Только проекты с главами",
        arrayOf("Да", "Все"),
    )

    private fun getTypeList() = listOf(
        CheckFilter("Манга", "MANGA"),
        CheckFilter("Манхва", "MANHWA"),
        CheckFilter("Маньхуа", "MANHYA"),
        CheckFilter("Сингл", "SINGLE"),
        CheckFilter("OEL-манга", "OEL"),
        CheckFilter("Комикс", "COMICS"),
        CheckFilter("Руманга", "RUSSIAN"),
    )

    private fun getStatusList() = listOf(
        CheckFilter("Выпускается", "ON_GOING"),
        CheckFilter("Заброшен", "ABANDONED"),
        CheckFilter("Завершён", "COMPLETED"),
    )

    private fun getStatusTitleList() = listOf(
        CheckFilter("Выпускается", "ON_GOING"),
        CheckFilter("Приостановлен", "SUSPENDED"),
        CheckFilter("Завершён", "COMPLETED"),
        CheckFilter("Анонс", "ANNOUNCEMENT"),
    )

    private fun getGenreList() = listOf(
        SearchFilter("cёнэн-ай"),
        SearchFilter("боевик"),
        SearchFilter("боевые искусства"),
        SearchFilter("гарем"),
        SearchFilter("гендерная интрига"),
        SearchFilter("героическое фэнтези"),
        SearchFilter("детектив"),
        SearchFilter("дзёсэй"),
        SearchFilter("додзинси"),
        SearchFilter("драма"),
        SearchFilter("ёнкома"),
        SearchFilter("игра"),
        SearchFilter("драма"),
        SearchFilter("ёнкома"),
        SearchFilter("игра"),
        SearchFilter("исекай"),
        SearchFilter("история"),
        SearchFilter("киберпанк"),
        SearchFilter("кодомо"),
        SearchFilter("комедия"),
        SearchFilter("махо-сёдзё"),
        SearchFilter("меха"),
        SearchFilter("мистика"),
        SearchFilter("научная фантастика"),
        SearchFilter("омегаверс"),
        SearchFilter("повседневность"),
        SearchFilter("постапокалиптика"),
        SearchFilter("приключения"),
        SearchFilter("психология"),
        SearchFilter("романтика"),
        SearchFilter("самурайский боевик"),
        SearchFilter("сверхъестественное"),
        SearchFilter("сёдзё"),
        SearchFilter("сёдзё-ай"),
        SearchFilter("сёнэн"),
        SearchFilter("спорт"),
        SearchFilter("сэйнэн"),
        SearchFilter("трагедия"),
        SearchFilter("триллер"),
        SearchFilter("ужасы"),
        SearchFilter("фантастика"),
        SearchFilter("фэнтези"),
        SearchFilter("школа"),
        SearchFilter("элементы юмора"),
        SearchFilter("эротика"),
        SearchFilter("этти"),
        SearchFilter("юри"),
        SearchFilter("яой"),
    )

    private fun getTagsList() = listOf(
        SearchFilter("веб"),
        SearchFilter("в цвете"),
        SearchFilter("сборник"),
        SearchFilter("хентай"),
        SearchFilter("азартные игры"),
        SearchFilter("алхимия"),
        SearchFilter("амнезия"),
        SearchFilter("ангелы"),
        SearchFilter("антигерой"),
        SearchFilter("антиутопия"),
        SearchFilter("апокалипсис"),
        SearchFilter("аристократия"),
        SearchFilter("армия"),
        SearchFilter("артефакты"),
        SearchFilter("боги"),
        SearchFilter("бои на мечах"),
        SearchFilter("борьба за власть"),
        SearchFilter("брат и сестра"),
        SearchFilter("будущее"),
        SearchFilter("вампиры"),
        SearchFilter("ведьма"),
        SearchFilter("вестерн"),
        SearchFilter("видеоигры"),
        SearchFilter("виртуальная реальность"),
        SearchFilter("военные"),
        SearchFilter("война"),
        SearchFilter("волшебники"),
        SearchFilter("волшебные существа"),
        SearchFilter("воспоминания из другого мира"),
        SearchFilter("врачи / доктора"),
        SearchFilter("выживание"),
        SearchFilter("гг женщина"),
        SearchFilter("гг имба"),
        SearchFilter("гг мужчина"),
        SearchFilter("гг не человек"),
        SearchFilter("геймеры"),
        SearchFilter("гильдии"),
        SearchFilter("глупый гг"),
        SearchFilter("гоблины"),
        SearchFilter("горничные"),
        SearchFilter("грузовик-сан"),
        SearchFilter("гяру"),
        SearchFilter("демоны"),
        SearchFilter("драконы"),
        SearchFilter("дружба"),
        SearchFilter("ёнкома"),
        SearchFilter("жестокий мир"),
        SearchFilter("животные компаньоны"),
        SearchFilter("завоевание мира"),
        SearchFilter("зверолюди"),
        SearchFilter("злые духи"),
        SearchFilter("зомби"),
        SearchFilter("игровые элементы"),
        SearchFilter("империи"),
        SearchFilter("исекай"),
        SearchFilter("квесты"),
        SearchFilter("космос"),
        SearchFilter("кулинария"),
        SearchFilter("культивация"),
        SearchFilter("лгбт"),
        SearchFilter("легендарное оружие"),
        SearchFilter("лоли"),
        SearchFilter("магическая академия"),
        SearchFilter("магия"),
        SearchFilter("мафия"),
        SearchFilter("медицина"),
        SearchFilter("месть"),
        SearchFilter("монстродевушки"),
        SearchFilter("монстры"),
        SearchFilter("музыка"),
        SearchFilter("навыки / способности"),
        SearchFilter("наёмники"),
        SearchFilter("насилие / жестокость"),
        SearchFilter("нежить"),
        SearchFilter("ниндзя"),
        SearchFilter("обмен телами"),
        SearchFilter("оборотни"),
        SearchFilter("обратный гарем"),
        SearchFilter("огнестрельное оружие"),
        SearchFilter("офисные работники"),
        SearchFilter("пародия"),
        SearchFilter("пираты"),
        SearchFilter("подземелье"),
        SearchFilter("политика"),
        SearchFilter("полиция"),
        SearchFilter("преступники / криминал"),
        SearchFilter("призраки / духи"),
        SearchFilter("прокачка"),
        SearchFilter("психодел"),
        SearchFilter("путешествия во времени"),
        SearchFilter("рабы"),
        SearchFilter("разумные расы"),
        SearchFilter("ранги силы"),
        SearchFilter("реинкарнация"),
        SearchFilter("роботы"),
        SearchFilter("рыцари"),
        SearchFilter("самураи"),
        SearchFilter("система"),
        SearchFilter("скрытие личности"),
        SearchFilter("спасение мира"),
        SearchFilter("спортивное тело"),
        SearchFilter("средневековье"),
        SearchFilter("стимпанк"),
        SearchFilter("супергерои"),
        SearchFilter("традиционные игры"),
        SearchFilter("умный гг"),
        SearchFilter("управление территорией"),
        SearchFilter("учитель / ученик"),
        SearchFilter("философия"),
        SearchFilter("хикикомори"),
        SearchFilter("холодное оружие"),
        SearchFilter("шантаж"),
        SearchFilter("эльфы"),
        SearchFilter("якудза"),
        SearchFilter("япония"),
    )

    private fun getAgeList() = listOf(
        CheckFilter("13+", "ADULT_13"),
        CheckFilter("16+", "ADULT_16"),
        CheckFilter("18+", "ADULT_18"),
    )

    private var isEng: String? = preferences.getString(LANGUAGE_PREF, "eng")
    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
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
        val paidChapterShow = androidx.preference.CheckBoxPreference(screen.context).apply {
            key = PAID_PREF
            title = PAID_PREF_Title
            summary = "Показывает не купленные\uD83D\uDCB2 главы(может вызвать ошибки при обновлении/автозагрузке)"
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean(key, checkValue).commit()
            }
        }
        screen.addPreference(titleLanguagePref)
        screen.addPreference(paidChapterShow)
    }

    companion object {
        private const val API_URL = "https://api.newmanga.org/v2"
        private const val IMAGE_URL = "https://storage.newmanga.org"

        private const val LANGUAGE_PREF = "NewMangaTitleLanguage"
        private const val LANGUAGE_PREF_Title = "Выбор языка на обложке"

        private const val PAID_PREF = "PaidChapter"
        private const val PAID_PREF_Title = "Показывать платные главы"
    }

    private val json: Json by injectLazy()
}
