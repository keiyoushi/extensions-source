package eu.kanade.tachiyomi.extension.uk.faust

import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.time.Year
import java.util.Locale
import kotlin.collections.joinToString
import kotlin.collections.orEmpty
import kotlin.text.ifEmpty

class Faust :
    HttpSource(),
    ConfigurableSource {

    override val name = "Faust"
    override val baseUrl = "https://faust-web.com"
    private val apiUrl = "https://faust-web.com/api"
    override val lang = "uk"
    override val supportsLatest = true
    private var genresFetched: Boolean = false
    private var tagsFetched: Boolean = false
    private val preferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Content-Type", "application/json")

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = makeSearchRequest(null, page, query, filters)

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResponseDto>()

        val mangas = data.titles.map { dto ->
            SManga.create().apply {
                url = dto.slug
                title = dto.name
                thumbnail_url = dto.coverImageUrl
            }
        }

        return MangasPage(mangas, (data.totalPages > data.page))
    }

    // ============================== Search/Utility ===============================
    private fun makeSearchRequest(sort: String? = null, page: Int, query: String? = "", filters: FilterList = getFilterList()): Request {
        val url = "$apiUrl/titles/search/library"

        // Prepare the data for serialization
        val requestBodyData = SearchRequestBody(
            searchQuery = query,
            page = page,
            pageSize = 30,
        )

        // Apply filters to the requestBodyData
        filters.forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    val asc = if (filter.state?.ascending == true) "-" else "+"
                    requestBodyData.sortBy = sort ?: "$asc${filter.selected}"
                }
                is CategoriesFilter -> filter.selected?.let { requestBodyData.mangaType = it }
                is TranslationStatusFilter -> filter.selected?.let { requestBodyData.translationStatus = it }
                is PublicationStatusFilter -> filter.selected?.let { requestBodyData.publicationStatus = it }
                is AgeStatusFilter -> filter.selected?.let { requestBodyData.ageBracket = it }
                is YearRangeFilter -> {
                    filter.minValue?.let { requestBodyData.yearFrom = checkMinRange(it, 1970, Year.now().value + 1) }
                    filter.maxValue?.let { requestBodyData.yearTo = checkMaxRange(it, 1970, Year.now().value + 1) }
                }
                is ChaptersRangeFilter -> {
                    filter.minValue?.let { requestBodyData.minChapters = checkMinRange(it, 0, 3000) }
                    filter.maxValue?.let { requestBodyData.maxChapters = checkMaxRange(it, 0, 3000) }
                }
                is GenresFilter -> {
                    filter.included?.let {
                        requestBodyData.genreIds = JsonArray(it.map { genreId -> JsonPrimitive(genreId) })
                    }
                    filter.excluded?.let {
                        val allExcluded = it.plus(ignoreGenres()).distinct()
                        requestBodyData.excludeGenreIds = JsonArray(allExcluded.map { genreId -> JsonPrimitive(genreId) })
                    } ?: run {
                        if (ignoreGenres().isNotEmpty()) {
                            requestBodyData.excludeGenreIds = JsonArray(ignoreGenres().map { genreId -> JsonPrimitive(genreId) })
                        }
                    }
                }
                is TagsFilter -> {
                    filter.included?.let {
                        requestBodyData.tagIds = JsonArray(it.map { tagId -> JsonPrimitive(tagId) })
                    }
                    filter.excluded?.let {
                        requestBodyData.excludeTagIds = JsonArray(it.map { tagId -> JsonPrimitive(tagId) })
                    }
                }
                else -> {}
            }
        }

        // Convert the data class to JSON request body
        val body = requestBodyData.toJsonRequestBody() // Assuming toJsonRequestBody() handles serialization

        return POST(url, headers, body)
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = makeSearchRequest(sort = "-rating", page = page)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = makeSearchRequest(sort = "+updated", page = page)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ============================== Manga / Manga ===============================
    // WebView url
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    // API request
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/titles/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val dto = response.parseAs<SMangaDto>()
        title = dto.name
        thumbnail_url = dto.coverImageUrl
        description = buildString {
            append(dto.description)
            append("\n\nАльтернативні назви: ${dto.englishName}")
            append("\nРейтинг: ${"%.2f".format(dto.averageRating)}/5 (${dto.votesCount}), В закладках: ${dto.bookmarksCount}")
        }
        artist = dto.artists?.joinToString { "${it.firstName} ${it.lastName}".trim() }?.takeIf { it.isNotBlank() }
        author = dto.authors?.joinToString { "${it.firstName} ${it.lastName}".trim() }?.takeIf { it.isNotBlank() }
        genre = buildList {
            add(mangaType(dto.mangaType))
            addAll(dto.genres?.map { it.name }.orEmpty())
            addAll(dto.tags?.map { it.name }.orEmpty())
        }.joinToString()
        status = when (dto.translationStatus) {
            "Inactive" -> SManga.CANCELLED
            "Translated" -> SManga.COMPLETED
            "Active" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Manga / Chapters ===============================
    // WebView url
    override fun getChapterUrl(chapter: SChapter): String {
        val (chapterSlug, seriesSlug) = chapter.url.split("/", limit = 2)
        val pieces = chapterSlug.split("-")
        return "$baseUrl/manga/$seriesSlug/${pieces[0]}-${pieces[1]}/${pieces[2]}-${pieces[3]}"
    }

    // API request
    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/titles/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<ChapterResponseDto>()

        return dto.volumes.flatMap { volume ->
            volume.chapters.map { chapter ->
                SChapter.create().apply {
                    val vol = chapter.volumeOrder.toString().removeSuffix(".0")
                    val chp = chapter.number.toString().removeSuffix(".0")
                    name = when {
                        chapter.name.contains("Розділ") -> "Том $vol ${chapter.name}"
                        else -> "Том $vol Розділ $chp ${chapter.name}"
                    }
                    url = "${chapter.slug}/${dto.slug}"
                    date_upload = parseDate(chapter.updatedDate)
                    chapter_number = chapter.number
                    scanlator = chapter.translationTeams?.joinToString { it.name }
                }
            }
        }.reversed()
    }

    // ============================== Images ===============================
    // API request
    override fun pageListRequest(chapter: SChapter): Request {
        val (chapterSlug, seriesSlug) = chapter.url.split("/", limit = 2)
        return GET("$apiUrl/chapters/$chapterSlug?titleSlug=$seriesSlug", headers)
    }
    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ChapterResponseList>()
        return dto.pages.map { page ->
            Page(page.pageNumber, imageUrl = page.blobName)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Utilities/Filter ===============================
    private val scope = CoroutineScope(Dispatchers.IO)
    override fun getFilterList(): FilterList {
        scope.launch {
            val genresDeferred = if (!genresFetched) async { fetchGenres() } else null
            val tagsDeferred = if (!tagsFetched) async { fetchTags() } else null

            genresDeferred?.await()
            tagsDeferred?.await()
        }
        val filters = mutableListOf<Filter<*>>(
            OrderBy(),
            Filter.Separator(),
            GenresFilter(),
            Filter.Separator(),
            TagsFilter(),
            Filter.Separator(),
            CategoriesFilter(),
            TranslationStatusFilter(),
            PublicationStatusFilter(),
            AgeStatusFilter(),
            Filter.Separator(),
            ChaptersRangeFilter(),
            Filter.Separator(),
            YearRangeFilter(),
            Filter.Header("Натисніть 'Скинути', щоб завантажити Жанри та Теги"),
        )
        return FilterList(filters)
    }
    private fun fetchGenres() {
        var currentAttempts = 0
        while (!genresFetched && currentAttempts < 3) {
            try {
                client.newCall(GET("$apiUrl/genres/paged?page=1&pageSize=100", headers)).execute().use { response ->
                    val list = response.parseAs<GenreListPageDto>().items
                    GenresFilter.options = list.map { it.name to it.id }
                    genresFetched = true
                }
            } catch (_: Exception) {
                // Log exception if needed, but don't rethrow to allow retry
            } finally {
                currentAttempts++
            }
        }
    }
    private fun fetchTags() {
        var currentAttempts = 0
        while (!tagsFetched && currentAttempts < 3) {
            try {
                client.newCall(GET("$apiUrl/tags/paged?page=1&pageSize=150", headers)).execute().use { response ->
                    val list = response.parseAs<GenreListPageDto>().items
                    TagsFilter.options = list.map { it.name to it.id }
                    tagsFetched = true
                }
            } catch (_: Exception) {
                // Log exception if needed, but don't rethrow to allow retry
            } finally {
                currentAttempts++
            }
        }
    }

    // ============================== Utilities ===============================
    private fun checkMinRange(input: String?, min: Int, max: Int): String {
        val value = input?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull() ?: return min.toString()
        if (value !in min..max) return min.toString()
        return value.toString()
    }
    private fun checkMaxRange(input: String?, min: Int, max: Int): String {
        val value = input?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull() ?: return max.toString()
        if (value !in min..max) return max.toString()
        return value.toString()
    }
    private fun mangaType(type: String?): String = when (type) {
        "Manga" -> "Манґа"
        "Manhwa" -> "Манхва"
        "Manhua" -> "Маньхва"
        "Oneshot" -> "Ваншот"
        "Webcomic" -> "Вебкомікс"
        "Doujinshi" -> "Доджінші"
        "Extra" -> "Екстра"
        "Comics" -> "Комікс"
        "Malyopys" -> "Мальопис"
        else -> "ЧЗХ"
    }

    private fun ignoreGenres(): Set<String> = preferences.getStringSet(SITE_GENRES_PREF, emptySet<String>())!!
    private fun ignoreGenresTitles(): String? = preferences.getString(SITE_GENRES_PREF_TITLES, null)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        MultiSelectListPreference(screen.context).apply {
            key = SITE_GENRES_PREF
            title = SITE_GENRES_PREF_TITLE
            val tags = GenresFilter.options
            entries = tags.map { it.first }.toTypedArray()
            entryValues = tags.map { it.second }.toTypedArray()
            summary = (ignoreGenresTitles() ?: "Не вибрано") + SITE_GENRES_PREF_SUM
            dialogTitle = "Виберіть категорії які потрібно сховати"
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                val selected = values as Set<*>
                preferences.edit().putString(
                    SITE_GENRES_PREF_TITLES,
                    tags.filter { it.second in selected }
                        .joinToString { it.first }
                        .ifEmpty { "Не вибрано" },
                ).apply()
                this.summary = tags.filter { it.second in selected }
                    .joinToString { it.first }
                    .ifEmpty { "Не вибрано" } + SITE_GENRES_PREF_SUM
                true
            }
        }.let(screen::addPreference)
    }

    private fun parseDate(dateStr: String?): Long = dateFormatSite.tryParse(dateStr)

    companion object {
        private val dateFormatSite = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSX", Locale.ROOT)

        private const val SITE_GENRES_PREF = "site_hidden_genres"
        private const val SITE_GENRES_PREF_TITLES = "site_hidden_genres_titles"
        private const val SITE_GENRES_PREF_TITLE = "Приховані категорії"
        private const val SITE_GENRES_PREF_SUM = "\n\nⓘЦі категорії завжди будуть приховані в 'Популярне', 'Новинки' та 'Фільтр'.\n\nⓘЯкщо список категорій порожній, зайдіть 'Огляд' -> 'Джерела' -> Faust -> 'Фільтр' та натисніть 'Скинути'"
    }
}
