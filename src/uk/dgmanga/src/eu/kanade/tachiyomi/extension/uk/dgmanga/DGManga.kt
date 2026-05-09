package eu.kanade.tachiyomi.extension.uk.dgmanga

import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.collections.joinToString
import kotlin.getValue
import kotlin.text.ifEmpty

class DGManga :
    HttpSource(),
    ConfigurableSource {

    override val name = "DGManga"
    override val baseUrl = "https://dgmanga.app"
    private val apiUrl = "https://dgmanga.app/api"
    override val lang = "uk"
    override val supportsLatest = true
    override val versionId = 2
    private val preferences by getPreferencesLazy()
    override val client = network.cloudflareClient.newBuilder()
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET(
        "$apiUrl/titles".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "28")
            addQueryParameter("sort", "popular")
            if (hideLicensedInSearch) addQueryParameter("isLicensed", "false")
            addQueryParameter("skipContentPrefs", "true")
        }.build(),
        headers,
    )

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET(
        "$apiUrl/titles".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "28")
            addQueryParameter("sort", "updated")
            if (hideLicensedInSearch) addQueryParameter("isLicensed", "false")
            addQueryParameter("skipContentPrefs", "true")
        }.build(),
        headers,
    )

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Search by title
        if (query.isNotEmpty() && query.length < 2) {
            throw Exception("Запит має містити щонайменше 2 символи / The query must contain at least 2 characters")
        }

        val url = "$apiUrl/titles".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "28")
            addQueryParameter("q", query)
            addQueryParameter("skipContentPrefs", "true")

            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is OrderBy -> filter.selected?.let { addQueryParameter("sort", it) }
                    is TypeFilter -> filter.selected?.let { addQueryParameter("type", it) }
                    is StatusFilter -> filter.selected?.let { addQueryParameter("status", it) }
                    is TranslationStatusFilter -> filter.selected?.let { addQueryParameter("translation_status", it) }
                    is GenresFilter -> filter.active?.let { addQueryParameter("genres", filter.selectedValues.joinToString(",")) }
                    is TagsFilter -> filter.active?.let { addQueryParameter("tags", filter.selectedValues.joinToString(",")) }
                    is LicensedFilter -> filter.selected?.let { addQueryParameter("isLicensed", if (hideLicensedInSearch) "false" else it) }
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<CatalogResponseDto>()
        val ignoredGenres = ignoreGenres()
        val mangas = data.titles.mapNotNull { dto ->
            SManga.create().apply {
                // Hide manga by genres in Settings
                if (ignoredGenres.isNotEmpty()) {
                    if (dto.genres.any { it in ignoredGenres }) {
                        return@mapNotNull null
                    }
                }
                // hide novels
                if (dto.type == "novel") {
                    return@mapNotNull null
                }
                url = dto.id
                title = dto.title
                thumbnail_url = dto.cover
            }
        }

        return MangasPage(mangas, (data.totalPages > data.page))
    }

    // ============================== Manga/Main Page ===============================
    // WebView url
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    // API request
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/titles/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val dto = response.parseAs<SMangaDto>()
        title = dto.title
        thumbnail_url = dto.cover
        description = buildString {
            append(dto.description)
            append("\n\nАльтернативні назви: ${dto.alternativeTitles?.joinToString(",")}")
        }
        artist = dto.authorRef.joinToString { it.name.toString() }.takeIf { it.isNotBlank() }
        author = dto.illustratorRef.joinToString { it.name.toString() }.takeIf { it.isNotBlank() }
        genre = buildList {
            add(mangaType(dto.type))
            addAll(dto.genres)
            addAll(dto.tags)
        }.joinToString()
        status = when (dto.translationStatus) {
            "Покинуто" -> SManga.CANCELLED
            "Завершено" -> SManga.COMPLETED
            "Перекладається" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Manga/Chapters ===============================
    // WebView url
    override fun getChapterUrl(chapter: SChapter): String {
        val (chapterId, chapterNumber, chapterTitle) = chapter.url.split("/", limit = 3)
        return "$baseUrl/read/$chapterTitle/$chapterNumber?chapterId=$chapterId"
    }

    // API request
    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/chapters/title/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<List<ChapterResponseDto>>()
        return dto.map { dto ->
            SChapter.create().apply {
                val vol = dto.volumeNumber.toString().removeSuffix(".0")
                val num = dto.chapterNumber.toString().removeSuffix(".0")
                name = "Том $vol Розділ $num ${dto.chapterName ?: ""}"
                url = "${dto.id}/$num/${dto.title}"
                date_upload = parseDate(dto.createdAt)
                chapter_number = dto.chapterNumber
                scanlator = dto.teams.firstOrNull()?.name
            }
        }
    }

    // ============================== Images ===============================
    // request URL
    override fun pageListRequest(chapter: SChapter): Request {
        val (chapterId, chapterNumber, chapterTitle) = chapter.url.split("/", limit = 3)
        return GET("$baseUrl/read/$chapterTitle/$chapterNumber?chapterId=$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        // They either load images via JS on the page. Or load it in the API one by one with $apiUrl/image-proxy/chapter/{chapterId}/{imageIndex}
        // With no information about number (index) of images to load anywhere. Page body also loaded by JS.
        val string = response.asJsoup().selectFirst("script:containsData(pages)")?.toString() ?: throw Exception("No information found about images")

        val beginIndex = string.indexOf("pages")
        val endIndex = string.indexOf("],", beginIndex)
        val trimmedString = string.substring(beginIndex, endIndex)

        val pages = mutableListOf<Page>()

        imageQueryRegex.findAll(trimmedString).forEachIndexed { index, result ->
            pages.add(Page(index = index, imageUrl = result.groupValues[1]))
        }
        if (pages.isEmpty()) {
            throw Exception("No images found in the chapter content.")
        }
        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList() = FilterList(
        OrderBy(),
        TypeFilter(),
        StatusFilter(),
        TranslationStatusFilter(),
        Filter.Separator(),
        GenresFilter(),
        Filter.Separator(),
        TagsFilter(),
        Filter.Separator(),
        LicensedFilter(),
    )

    // ============================== Preference ===============================
    private fun ignoreGenres(): Set<String> = preferences.getStringSet(SITE_GENRES_PREF, emptySet<String>())!!
    private val hideLicensedInSearch = preferences.getBoolean(SITE_LICENSED_SEARCH, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        MultiSelectListPreference(screen.context).apply {
            key = SITE_GENRES_PREF
            title = SITE_GENRES_PREF_TITLE
            val tags = GenresFilter.values
            entries = tags.map { it.first }.toTypedArray()
            entryValues = tags.map { it.second }.toTypedArray()
            summary = tags.filter { it.second in ignoreGenres() }
                .joinToString { it.first }
                .ifEmpty { "Не вибрано" } + SITE_GENRES_PREF_SUM
            dialogTitle = "Виберіть категорії які потрібно сховати"
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                @Suppress("UNCHECKED_CAST")
                val selected = values as Set<String>
                this.summary = tags.filter { it.second in selected }
                    .joinToString { it.first }
                    .ifEmpty { "Не вибрано" } + SITE_GENRES_PREF_SUM
                true
            }
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SITE_LICENSED_SEARCH
            title = SITE_LICENSED_SEARCH_TITLE
            summary = SITE_LICENSED_SEARCH_SUM
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    // ============================== Utilities ===============================
    private fun mangaType(type: String?): String = when (type) {
        "manga" -> "Манґа"
        "manhwa" -> "Манхва"
        "manhua" -> "Маньхва"
        "western" -> "Вестерн"
        "Мальописи" -> "Мальопис"
        "novel" -> "Новела"
        else -> "ЧЗХ"
    }

    private fun parseDate(dateStr: String?): Long = dateFormatSite.tryParse(dateStr)

    companion object {
        private val imageQueryRegex = Regex("\"(http.*?)\\\\\"")
        private const val SITE_GENRES_PREF = "site_hidden_genres"
        private const val SITE_GENRES_PREF_TITLE = "Приховані категорії"
        private const val SITE_GENRES_PREF_SUM = "\nⓘЦі категорії завжди будуть приховані в 'Популярне', 'Новинки' та 'Фільтр'."
        private const val SITE_LICENSED_SEARCH = "site_hide_licensed"
        private const val SITE_LICENSED_SEARCH_TITLE = "Скривати ліцензовані твори"
        private const val SITE_LICENSED_SEARCH_SUM = "\nⓘ При зміні цього параметра необхідно перезапустити програму з повною зупинкою."
        private val dateFormatSite = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
    }
}
