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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
                setUrlWithoutDomain("/api/titles/${dto.slug}")
                title = dto.name
                thumbnail_url = dto.coverImageUrl
            }
        }

        return MangasPage(mangas, (data.totalPages > data.page))
    }

    // ============================== Search/Utility ===============================
    private fun makeSearchRequest(sort: String? = null, page: Int, query: String? = "", filters: FilterList = getFilterList()): Request {
        val url = "$apiUrl/titles/search/library"
        val body = buildJsonObject {
            put("searchQuery", query)
            put("page", page)
            put("pageSize", 30)
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is OrderBy -> {
                        val state = filter.state
                        val orderBy = state?.let { arrayOf("rating", "popularity", "alphabet", "updated", "newest")[it.index] } ?: "rating"
                        val asc = if (state?.ascending == true) "+" else "-"
                        put("sortBy", sort ?: "$asc$orderBy")
                    }
                    is CategoriesFilter -> filter.selected?.let { put("mangaType", it) }
                    is TranslationStatusFilter -> filter.selected?.let { put("translationStatus", it) }
                    is PublicationStatusFilter -> filter.selected?.let { put("publicationStatus", it) }
                    is AgeStatusFilter -> filter.selected?.let { put("ageBracket", it) }
                    is YearRangeFilter -> {
                        filter.minValue?.let { put("yearFrom", checkMinRange(it, 1970, Year.now().value + 1)) }
                        filter.maxValue?.let { put("yearTo", checkMaxRange(it, 1970, Year.now().value + 1)) }
                    }
                    is ChaptersRangeFilter -> {
                        filter.minValue?.let { put("minChapters", checkMinRange(it, 0, 3000)) }
                        filter.maxValue?.let { put("maxChapters", checkMaxRange(it, 0, 3000)) }
                    }
                    is GenresFilter -> {
                        filter.included?.let {
                            putJsonArray("genreIds") {
                                it.forEach { add(it) }
                            }
                        }
                        filter.excluded?.let {
                            val allExcluded = it.plus(ignoreGenres()).distinct()
                            putJsonArray("excludeGenreIds") {
                                allExcluded.forEach { add(it) }
                            }
                        } ?: run {
                            if (ignoreGenres().isNotEmpty()) {
                                putJsonArray("excludeGenreIds") {
                                    ignoreGenres().forEach { add(it) }
                                }
                            }
                        }
                    }
                    is TagsFilter -> {
                        filter.included?.let {
                            putJsonArray("tagIds") {
                                it.forEach { add(it) }
                            }
                        }
                        filter.excluded?.let {
                            putJsonArray("excludeTagIds") {
                                it.forEach { add(it) }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }.toJsonRequestBody()

        return POST(url, headers, body)
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = makeSearchRequest(sort = "-rating", page = page)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = makeSearchRequest(sort = "+updated", page = page)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ============================== Manga / Manga ===============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url.substringAfter("titles/")}"

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val dto = response.parseAs<SMangaDto>()
        title = dto.name
        thumbnail_url = dto.coverImageUrl
        setUrlWithoutDomain("/api/titles/${dto.slug}")
        description = "${dto.description}" +
            "\n\nАльтернативні назви: ${dto.englishName}" +
            "\nРейтинг: ${"%.2f".format(dto.averageRating)}/5 (${dto.votesCount}), " +
            "В закладках: ${dto.bookmarksCount}"
        artist = dto.artists?.joinToString { "${it.firstName} ${it.lastName}".trim() }?.takeIf { it.isNotBlank() }
        author = dto.authors?.joinToString { "${it.firstName} ${it.lastName}".trim() }?.takeIf { it.isNotBlank() }
        genre = buildList {
            add(mangaType(dto.mangaType))
            addAll(dto.tags?.map { it.name }.orEmpty())
            addAll(dto.genres?.map { it.name }.orEmpty())
        }.joinToString()
        status = when (dto.translationStatus) {
            "Inactive" -> SManga.CANCELLED
            "Translated" -> SManga.COMPLETED
            "Active" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Manga / Chapters ===============================
    override fun getChapterUrl(chapter: SChapter): String {
        val pieces = chapter.url.substringAfter("chapters/").substringBefore("?titleSlug").split("-")
        return "$baseUrl/manga/${chapter.url.substringAfter("titleSlug=")}/${pieces[0]}-${pieces[1]}/${pieces[2]}-${pieces[3]}"
    }

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
                    setUrlWithoutDomain("/api/chapters/${chapter.slug}?titleSlug=${dto.slug}")
                    date_upload = parseDate(chapter.updatedDate)
                    chapter_number = chapter.number
                    scanlator = chapter.translationTeams?.joinToString { it.name }
                }
            }
        }.reversed()
    }

    // ============================== Images ===============================
    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ChapterResponseList>()
        return dto.pages.map { page ->
            Page(page.pageNumber, imageUrl = page.blobName)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Utilities ===============================
    override fun getFilterList(): FilterList {
        if (!genresFetched) {
            fetchGenres()
        }
        if (!tagsFetched) {
            fetchTags()
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
        var fetchGenresAttempts = 0
        if (!genresFetched && fetchGenresAttempts < 3) {
            try {
                client.newCall(GET("$apiUrl/genres/paged?page=1&pageSize=100", headers)).execute().use { response ->
                    val list = response.parseAs<GenreListPageDto>().items
                    GenresFilter.options = list.map { it.name to it.id }
                    genresFetched = true
                }
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }
    private fun fetchTags() {
        var fetchTagsAttempts = 0
        if (!tagsFetched && fetchTagsAttempts < 3) {
            try {
                client.newCall(GET("$apiUrl/tags/paged?page=1&pageSize=150", headers)).execute().use { response ->
                    val list = response.parseAs<GenreListPageDto>().items
                    TagsFilter.options = list.map { it.name to it.id }
                    tagsFetched = true
                }
            } catch (_: Exception) {
            } finally {
                fetchTagsAttempts++
            }
        }
    }
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
                try {
                    preferences.edit().putString(
                        SITE_GENRES_PREF_TITLES,
                        tags.filter { it.second in selected }
                            .joinToString { it.first }
                            .ifEmpty { "Не вибрано" },
                    ).apply()
                } catch (_: Exception) {
                }
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
        private const val SITE_GENRES_PREF_SUM = "\n\nⓘЦі категорії завжди будуть приховані в 'Популярне', 'Новинки' та 'Фільтр'." +
            "\n\nⓘЯкщо список категорій порожній, зайдіть 'Огляд' -> 'Джерела' -> Faust -> 'Фільтр' та натисніть 'Скинути'"
    }
}
