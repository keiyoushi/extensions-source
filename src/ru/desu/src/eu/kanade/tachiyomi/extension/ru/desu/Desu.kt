package eu.kanade.tachiyomi.extension.ru.desu

import android.widget.Toast
import androidx.preference.ListPreference
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Desu :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", "Mihon (+https://github.com/keiyoushi/extensions-source)")
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(1, 2.seconds) { it.host == baseUrl.toHttpUrl().host }
    }

    // ============================== Manga Details ===============================
    private fun MangaDetDto.toSManga(
        genresStr: String? = genres?.joinToString { it.name } ?: "",
        authorsStr: String? = authors?.joinToString { it.name },
    ): SManga {
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

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl$API_URL/catalog/?page=$page".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "20")
            .addQueryParameter("page", page.toString())
        val types = mutableListOf<Type>()
        val statuses = mutableListOf<Status>()
        val genres = mutableListOf<Genre>()
        filters.forEach { filter ->
            when (filter) {
                is OrderBy -> url.addQueryParameter("order_by", arrayOf("popular", "updated", "id", "name")[filter.state])
                is TypeList -> filter.state.forEach { type -> if (type.state) types.add(type) }
                is StatusList -> filter.state.forEach { status -> if (status.state) statuses.add(status) }
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
        if (statuses.isNotEmpty()) {
            url.addQueryParameter("status", statuses.joinToString(",") { it.id })
        }
        if (query.isNotEmpty()) {
            url.addQueryParameter("search", query)
        }

        val response = client.get(url.build())

        val page = response.parseAs<PageWrapperDto<MangaDetDto>>()

        val mangas = page.mangas.map { it.toSManga() }

        return MangasPage(mangas, page.pagination.last_page > page.pagination.current_page)
    }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", getFilterList(null))

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", getFilterList(null))

    // ============================== Manga/Chapters ===============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = if (fetchDetails) {
            async {
                val responseManga = client.get("$baseUrl$API_URL${manga.url}/")
                responseManga.parseAs<InfoWrapperDto<MangaDetDto>>().manga.toSManga()
            }
        } else {
            null
        }

        val chaptersDeferred = if (fetchChapters) {
            async {
                val responseChapter = client.get("$baseUrl$API_URL${manga.url}/chapters")
                val objChapter = responseChapter.parseAs<SeriesWrapperDto<List<ChaptersDto>>>().chapters
                objChapter.map { chapter ->
                    val fullNumStr = "${chapter.volume}. Глава ${chapter.number}"
                    SChapter.create().apply {
                        name = chapter.title?.let { "$fullNumStr $it" } ?: fullNumStr
                        url = chapter.id.toString()
                        memo = buildJsonObject {
                            put("mangaUrl", manga.url)
                            put("viewUrl", chapter.view_url)
                        }
                        chapter_number = chapter.number.toFloatOrNull() ?: -1f
                        date_upload = chapter.publish_date.times(1000L)
                    }
                }
            }
        } else {
            null
        }

        val finalManga = mangaDeferred?.await() ?: manga
        val finalChapters = chaptersDeferred?.await() ?: chapters

        SMangaUpdate(finalManga, finalChapters)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga${manga.url}"

    // ============================== Chapters Images ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val titleId = chapter.memo["mangaUrl"]!!.string.removePrefix("/")
        val chapterId = chapter.url
        val url = "$baseUrl$API_URL$titleId/chapters/$chapterId"

        val response = client.get(url.toHttpUrl())

        val result = response.parseAs<ChapterWrapperDto<ChapterDataDto>>()

        return result.chapter.pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.url)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val viewUrl = chapter.memo["viewUrl"]?.string
            ?: throw Exception("Обновите список глав!")
        return viewUrl
    }

    // =========================== Deeplink (Manga from Browser) ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "manga" && url.pathSegments[1].length > 1) {
            val tmpManga = SManga.create().apply {
                this.url = "/${url.pathSegments[1].substringAfterLast(".")}"
            }
            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }
        return null
    }

    // ============================== Filters ===============================
    private class OrderBy :
        Filter.Select<String>(
            "Сортировка",
            arrayOf("По популярности", "По обновлению", "По добавлению", "По алфавиту"),
        )

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Жанр", genres)

    private class TypeList(types: List<Type>) : Filter.Group<Type>("Тип", types)

    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Статус", statuses)

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private class Type(name: String, val id: String) : Filter.CheckBox(name)
    private class Status(name: String, val id: String) : Filter.CheckBox(name)

//    override fun getFilterList() = FilterList(
//        OrderBy(),
//        TypeList(getTypeList()),
//        GenreList(getGenreList()),
//        StatusList(getStatusList()),
//    )

    private fun getTypeList() = listOf(
        Type("Манга", "manga"),
        Type("Манхва", "manhwa"),
        Type("Маньхуа", "manhua"),
        Type("Ваншот", "one_shot"),
        Type("Комикс", "comics"),
    )

    private fun getStatusList() = listOf(
        Status("Выходит", "ongoing"),
        Status("Издано", "released"),
        Status("Переводится", "continued"),
        Status("Завершено", "completed"),
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

    // ============================== Preference ===============================
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
        private const val LANGUAGE_PREF = "DesuTitleLanguage"
        private const val API_URL = "/api/manga/"
    }
}
