package eu.kanade.tachiyomi.extension.ru.desu

import android.widget.Toast
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

@Source
abstract class Desu :
    HttpSource(),
    ConfigurableSource {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    private val preferences by getPreferencesLazy()

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi")
        add("Referer", baseUrl)
    }

    override val client: OkHttpClient =
        network.client.newBuilder()
            .rateLimit(3) { it.host == baseUrlHost }
            .build()

    private fun MangaDetDto.toSManga(genresStr: String? = "", authorsStr: String? = null): SManga {
        val ratingValue = score?.value ?: 0.0f
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

        val rawAgeStop = when (content_rating) {
            "no" -> ""
            else -> content_rating?.replace("_plus", "+") ?: ""
        }

        val category = when (kind) {
            "manga" -> "Манга"
            "manhwa" -> "Манхва"
            "manhua" -> "Маньхуа"
            "comics" -> "Комикс"
            "one_shot" -> "Ваншот"
            else -> "Манга"
        }

        var altName = ""

        if (!synonyms.isNullOrEmpty()) {
            altName = "Альтернативные названия:\n" +
                synonyms.joinToString(separator = " / ") +
                "\n\n"
        }

        return SManga.create().also { manga ->
            manga.title = if (isEng.equals("rus")) {
                russian
            } else {
                name
            }
            manga.url = "/$id"
            manga.thumbnail_url = cover.preview
            val totalVotes = score?.votes ?: 0L
            manga.description = if (isEng.equals("rus")) {
                name
            } else {
                russian
            } + "\n" + ratingStar + " " + ratingValue + " (голосов: " +
                totalVotes + ")\n" + altName + description
            manga.genre = ("$category, $rawAgeStop, $genresStr").split(", ").filter { it.isNotEmpty() }.joinToString { it.trim() }
            manga.status = when (trans_status) {
                "continued" -> SManga.ONGOING

                "completed" -> SManga.COMPLETED

                else -> when (status) {
                    "ongoing" -> SManga.ONGOING

                    "released" -> SManga.COMPLETED

                    //  "copyright" -> SManga.LICENSED  Hides available chapters!
                    else -> SManga.UNKNOWN
                }
            }
            manga.author = authorsStr
        }
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/?order_by=popular&page=$page", headers)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga/?order_by=updated&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
"""        val url = "$baseUrl$API_URL/".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())"""
        val url = "$baseUrl/manga/?page=$page".toHttpUrl().newBuilder()
        val types = mutableListOf<Type>()
        val genres = mutableListOf<Genre>()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> url.addQueryParameter("order", arrayOf("popular", "updated", "id", "name")[filter.state])
                is TypeList -> filter.state.forEach { type -> if (type.state) types.add(type) }
                is GenreList -> filter.state.forEach { genre -> if (genre.state) genres.add(genre) }
                else -> {}
            }
        }

        if (types.isNotEmpty()) {
            url.addQueryParameter("kinds", types.joinToString(",") { it.id })
        }
        if (genres.isNotEmpty()) {
            url.addQueryParameter("genres", genres.joinToString(",") { it.id })
        }
        if (query.isNotEmpty()) {
            url.addQueryParameter("search", query)
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
"""        val page = json.decodeFromString<PageWrapperDto<MangaDetDto>>(response.body.string())
        val mangas = page.response.map {
            it.toSManga()
        }

        return MangasPage(mangas, page.pageNavParams.count > page.pageNavParams.page * page.pageNavParams.limit)"""
        val document = response.asJsoup()
        val mangas = document.select(".animeList .memberListItem").map { element ->
            SManga.create().apply {
                element.selectFirst("a.avatar")?.let { cover ->
                    val titleFullId = cover.attr("href")
                    val titleId = titleFullId.substringAfterLast(".").substringBeforeLast("/")
                    setUrlWithoutDomain("/$titleId")
                    cover.selectFirst(".img")?.attr("style")?.let { style ->
                        if (style.contains("url('")) {
                            thumbnail_url = style.substringAfter("url('").substringBefore("')")
                        }
                    }
                }
                val titleSelector = if (isEng == "rus") ".dimmed.oTitle" else ".animeTitle.oTitle"
                title = element.selectFirst(titleSelector)?.text()
                    ?: element.selectFirst(".animeTitle.oTitle")?.text() ?: ""
            }
        }
        val hasNextPage = document.selectFirst("a:contains(Вперёд)") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun titleDetailsRequest(manga: SManga): Request = GET(baseUrl + API_URL + manga.url + "/", headers)

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = client.newCall(titleDetailsRequest(manga))
        .asObservableSuccess()
        .map { response ->
            mangaDetailsParse(response).apply { initialized = true }
        }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + "/manga" + manga.url, headers)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val responseString = response.body.string()
        val series = json.decodeFromString<InfoWrapperDto<MangaDetDto>>(responseString)
        val genresStr = json.decodeFromString<InfoWrapperDto<MangaDetGenresDto>>(responseString).manga.genres?.joinToString { it.name } ?: ""
        val authorsStr = if (responseString.contains("author_id")) {
            json.decodeFromString<InfoWrapperDto<MangaDetAuthorsDto>>(responseString).manga.authors?.joinToString { it.name } ?: ""
        } else {
            null
        }
        return series.manga.toSManga(genresStr, authorsStr)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseString = response.body.string()
        val objChapter = json.decodeFromString<SeriesWrapperDto<List<ChaptersDto>>>(responseString).chapters

        val chaptersList = objChapter.map { chapter ->
            val fullNumStr = "${chapter.volume}. Глава ${chapter.number}"
            SChapter.create().apply {
                name = chapter.title?.let { "$fullNumStr $it" } ?: fullNumStr
                // #apiChapter - JSON API url to automatically delete when chapter is opened in browser
                url = chapter.view_url + "#apiChapter/${chapter.manga_id}/chapters/${chapter.id}"
                chapter_number = chapter.number.toFloatOrNull() ?: -1f
                date_upload = chapter.publish_date.times(1000L)
            }
        }
        return chaptersList
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + API_URL + manga.url + "/chapters", headers)

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + API_URL + chapter.url.substringAfterLast("#apiChapter"), headers)

    override fun getChapterUrl(chapter: SChapter): String = chapter.url.substringBeforeLast("#apiChapter")

    override fun pageListParse(response: Response): List<Page> {
        val responseString = response.body.string()

        val result = json.decodeFromString<ChapterWrapperDto<ChapterDataDto>>(responseString)

        // Теперь компилятор точно знает, что result.chapter — это ChapterDataDto
        val objPages = result.chapter.pages

        return objPages.mapIndexed { index, page ->
            Page(index, imageUrl = page.url)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun searchMangaByIdRequest(id: String): Request = GET("$baseUrl$API_URL/$id", headers)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            val titleFullId = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() }
                ?: throw Exception("Unsupported url")
            val titleId = titleFullId.substringAfterLast(".").substringBeforeLast("/")
            return fetchSearchManga(page, "$PREFIX_SLUG_SEARCH$titleId", filters)
        }
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

    private class OrderBy :
        Filter.Select<String>(
            "Сортировка",
            arrayOf("По популярности", "По обновлению", "По добавлению", "По алфавиту"),
        )

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Жанр", genres)

    private class TypeList(types: List<Type>) : Filter.Group<Type>("Тип", types)

    private class Type(name: String, val id: String) : Filter.CheckBox(name)

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)

    override fun getFilterList() = FilterList(
        OrderBy(),
        TypeList(getTypeList()),
        GenreList(getGenreList()),
    )

    private fun getTypeList() = listOf(
        Type("Манга", "manga"),
        Type("Манхва", "manhwa"),
        Type("Маньхуа", "manhua"),
        Type("Ваншот", "one_shot"),
        Type("Комикс", "comics"),
    )

    private fun getGenreList() = listOf(
        Genre("Безумие", "Dementia"),
        Genre("Боевые искусства", "Martial Arts"),
        Genre("Вампиры", "Vampire"),
        Genre("Военное", "Military"),
        Genre("Гарем", "Harem"),
        Genre("Демоны", "Demons"),
        Genre("Детектив", "Mystery"),
        Genre("Детское", "Kids"),
        Genre("Дзёсей", "Josei"),
        Genre("Додзинси", "Doujinshi"),
        Genre("Драма", "Drama"),
        Genre("Игры", "Game"),
        Genre("Исторический", "Historical"),
        Genre("Комедия", "Comedy"),
        Genre("Космос", "Space"),
        Genre("Магия", "Magic"),
        Genre("Машины", "Cars"),
        Genre("Меха", "Mecha"),
        Genre("Музыка", "Music"),
        Genre("Пародия", "Parody"),
        Genre("Повседневность", "Slice of Life"),
        Genre("Полиция", "Police"),
        Genre("Приключения", "Adventure"),
        Genre("Психологическое", "Psychological"),
        Genre("Романтика", "Romance"),
        Genre("Самураи", "Samurai"),
        Genre("Сверхъестественное", "Supernatural"),
        Genre("Сёдзе", "Shoujo"),
        Genre("Сёдзе Ай", "Shoujo Ai"),
        Genre("Сейнен", "Seinen"),
        Genre("Сёнен", "Shounen"),
        Genre("Сёнен Ай", "Shounen Ai"),
        Genre("Смена пола", "Gender Bender"),
        Genre("Спорт", "Sports"),
        Genre("Супер сила", "Super Power"),
        Genre("Триллер", "Thriller"),
        Genre("Ужасы", "Horror"),
        Genre("Фантастика", "Sci-Fi"),
        Genre("Фэнтези", "Fantasy"),
        Genre("Хентай", "Hentai"),
        Genre("Школа", "School"),
        Genre("Экшен", "Action"),
        Genre("Этти", "Ecchi"),
        Genre("Юри", "Yuri"),
        Genre("Яой", "Yaoi"),
    )

    private var isEng: String? = preferences.getString(LANGUAGE_PREF, "eng")

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        ListPreference(screen.context).apply {
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
        }.let(screen::addPreference)
    }

    companion object {
        const val PREFIX_SLUG_SEARCH = "slug:"
        private const val LANGUAGE_PREF = "DesuTitleLanguage"
        private const val API_URL = "/api/manga/"
    }
}
