package eu.kanade.tachiyomi.extension.uk.zenko

import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.Calendar

class Zenko :
    HttpSource(),
    ConfigurableSource {
    override val name = "Zenko"
    override val baseUrl = "https://zenko.online"
    override val lang = "uk"
    override val supportsLatest = true
    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", "$baseUrl/")
        .add("Referer", "$baseUrl/")

    override val client = network.client.newBuilder()
        .rateLimitHost(API_URL.toHttpUrl(), 10)
        .build()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = makeCatalogRequest(page, "viewsCount")

    override fun popularMangaParse(response: Response) = parseAsCatalogResponse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = makeCatalogRequest(page, "lastChapterCreatedAt")

    override fun latestUpdatesParse(response: Response) = parseAsCatalogResponse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty() && query.length < 2) {
            throw UnsupportedOperationException("Запит має містити щонайменше 2 символи / The query must contain at least 2 characters")
        }
        return makeCatalogRequest(page, "viewsCount", query, filters)
    }

    override fun searchMangaParse(response: Response) = parseAsCatalogResponse(response)

    // =============================== Latest/Popular/Search Utilities ===============================
    private fun makeCatalogRequest(page: Int, sortBy: String, query: String? = null, filters: FilterList? = null): Request {
        val offset = offsetCounter(page)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val url = "$API_URL/titles".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "15")
            .addQueryParameter("offset", offset)

        filters?.forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    url.addQueryParameter("sortBy", filter.selected)
                    url.addQueryParameter("order", filter.order)
                }
                is CategoryFilter -> filter.checked?.let { url.addQueryParameter("categories", it.joinToString(",")) }
                is TranslationStatusFilter -> filter.checked?.let { url.addQueryParameter("translationStatus", it.joinToString(",")) }
                is GenresFilter -> filter.checked?.let { url.addQueryParameter("genres", it.joinToString(",")) }
                is TagsFilter -> filter.checked?.let { url.addQueryParameter("tags", it.joinToString(",")) }
                is AgeFilter -> filter.checked?.let { url.addQueryParameter("ageLimit", it.joinToString(",")) }
                is YearRangeFilter -> {
                    filter.minValue?.let { url.addQueryParameter("releaseYearFrom", checkMinRange(it, 1980, currentYear)) }
                    filter.maxValue?.let { url.addQueryParameter("releaseYearTo", checkMaxRange(it, 1980, currentYear)) }
                }
                else -> {}
            }
        }

        if (filters == null) {
            val hiddenCat = hiddenCategories()
            val ageCat = ageLimit()
            url.addQueryParameter("sortBy", sortBy)
            url.addQueryParameter("order", "DESC")

            if (hiddenCat.isNotEmpty()) {
                val categoriesSetting = CategoryFilter.categories.filter { !hiddenCat.contains(it.second) }.joinToString(",") { it.second }
                url.addQueryParameter("categories", categoriesSetting)
            }
            if (ageCat.isNotEmpty()) {
                val ageLimitSetting = ageCat.joinToString(",")
                url.addQueryParameter("ageLimit", ageLimitSetting)
            }
        }

        if (!query.isNullOrEmpty()) {
            url.addQueryParameter("name", query)
        }
        return GET(url.build(), headers)
    }

    private fun parseAsCatalogResponse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        val lang = isEng()
        val mangas = result.data.map { dto ->
            SManga.create().apply {
                setUrlWithoutDomain("/titles/${dto.id}")
                title = getSelectedLanguage(lang, dto.engName, dto.name)
                thumbnail_url = buildImageUrl(dto.coverImg)
            }
        }
        return MangasPage(mangas, result.meta.hasNextPage)
    }

    // =========================== Manga Details ============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfter("titles/")
        val url = "$API_URL/titles/$mangaId"
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val dto = response.parseAs<MangaDetailsResponse>()
        val lang = isEng()
        setUrlWithoutDomain("/titles/${dto.id}")
        title = getSelectedLanguage(lang, dto.engName, dto.name)
        thumbnail_url = buildImageUrl(dto.coverImg)
        description = buildString {
            append(dto.description)
            append("\n")
            if (lang == "ua") {
                append("\nАльтернативні назви:")
                dto.engName?.let { append(" $it,") }
                dto.originalName?.let { append(" $it") }
            }
            dto.likesCount?.let { append("\nВподобайок: $it ") }
            dto.viewsCount?.let { append("\nПереглядів: $it ") }
            dto.bookmarksCount?.let { append("\nВ закладинках у: $it ") }
        }
        genre = buildList {
            dto.genres?.map { it.name }?.let { addAll(it) }
            dto.tags?.map { it.name }?.let { addAll(it) }
        }.joinToString()
        author = dto.writers?.joinToString { it.name }
        artist = dto.painters?.joinToString { it.name }
        status = dto.status.toStatus()
    }

    // ============================== Chapters ==============================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfter("titles/")
        val url = "$API_URL/titles/$mangaId/chapters"
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<List<ChapterResponseItem>>()
        return result.sortedByDescending { item ->
            val id = StringProcessor.generateId(item.name)
            if (id > 0) id else item.id.toDouble()
        }.map { chapterResponseItem ->
            SChapter.create().apply {
                setUrlWithoutDomain("/titles/${chapterResponseItem.titleId}/${chapterResponseItem.id}")
                name = StringProcessor.format(chapterResponseItem.name)
                date_upload = chapterResponseItem.createdAt!!.secToMs()
                scanlator = chapterResponseItem.publisher!!.name
                chapter_number = StringProcessor.number(chapterResponseItem.name)
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request {
        val (titleId, chapterId) = chapter.url.substringAfter("titles/").split("/", limit = 2)
        val url = "$API_URL/chapters/$chapterId"
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ChapterResponseItem>()
        return data.pages!!.map { page ->
            Page(page.id, imageUrl = "$IMAGE_STORAGE_URL/${page.content}")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Filters ==============================
    override fun getFilterList() = FilterList(
        OrderBy(),
        Filter.Separator(),
        CategoryFilter(hiddenCategories()),
        Filter.Separator(),
        TranslationStatusFilter(),
        Filter.Separator(),
        AgeFilter(ageLimit()),
        Filter.Separator(),
        GenresFilter(),
        Filter.Separator(),
        TagsFilter(),
        Filter.Separator(),
        YearRangeFilter(),
    )

    // ============================ Preferences =============================
    private fun isEng(): String = preferences.getString(LANGUAGE_PREF, "ua")!!
    private fun ageLimit(): Set<String> = preferences.getStringSet(SITE_AGE_PREF, emptySet<String>())!!
    private fun hiddenCategories(): Set<String> = preferences.getStringSet(SITE_CATEGORIES_PREF, setOf(SITE_CATEGORIES_DEFAULT))!!

    private fun getSelectedLanguage(isEng: String, engName: String?, name: String): String = when {
        isEng == "eng" && !engName.isNullOrEmpty() -> engName
        else -> name
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = LANGUAGE_PREF
            title = LANGUAGE_PREF_TITLE
            entries = arrayOf("Українська", "Англійська")
            entryValues = arrayOf("ua", "eng")
            summary = "%s"
            setDefaultValue("ua")
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, SITE_TOAST_WARNING, Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = SITE_AGE_PREF
            title = SITE_AGE_PREF_TITLE
            val tags = AgeFilter.age
            entries = tags.map { it.first }.toTypedArray()
            entryValues = tags.map { it.second }.toTypedArray()
            summary = tags.filter { it.second in ageLimit() }
                .joinToString { it.first }
                .ifEmpty { SITE_AGE_PREF_ALL }
            dialogTitle = SITE_AGE_PREF_DIALOG
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                @Suppress("UNCHECKED_CAST")
                val selected = values as Set<String>
                this.summary = tags.filter { it.second in selected }
                    .joinToString { it.first }
                    .ifEmpty { SITE_AGE_PREF_ALL }
                true
            }
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = SITE_CATEGORIES_PREF
            title = SITE_CATEGORIES_PREF_TITLE
            val tags = CategoryFilter.categories
            entries = tags.map { it.first }.toTypedArray()
            entryValues = tags.map { it.second }.toTypedArray()
            summary = tags.filter { it.second in hiddenCategories() }
                .joinToString { it.first }
                .ifEmpty { SITE_AGE_PREF_ALL } + SITE_PREF_EXPLANATION
            dialogTitle = SITE_CATEGORIES_PREF_DIALOG
            setDefaultValue(setOf(SITE_CATEGORIES_DEFAULT))

            setOnPreferenceChangeListener { _, values ->
                @Suppress("UNCHECKED_CAST")
                val selected = values as Set<String>
                this.summary = tags.filter { it.second in selected }
                    .joinToString { it.first }
                    .ifEmpty { SITE_AGE_PREF_ALL } + SITE_PREF_EXPLANATION
                true
            }
        }.let(screen::addPreference)
    }

    // ============================= Utilities ==============================
    private fun offsetCounter(page: Int) = ((page - 1) * 15).toString()

    private fun String.toStatus(): Int {
        val status = this.lowercase()
        return when (status) {
            "ongoing" -> SManga.ONGOING
            "finished" -> SManga.COMPLETED
            "paused" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun Long.secToMs(): Long = this * 1000

    private fun buildImageUrl(imageId: String): String = "$IMAGE_STORAGE_URL/$imageId?optimizer=image&width=560&quality=70&height=auto"

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

    companion object {
        private const val API_URL = "https://api.zenko.online"
        private const val IMAGE_STORAGE_URL = "https://storage.zenko.online"
        private const val LANGUAGE_PREF = "TitleLanguagePref"
        private const val LANGUAGE_PREF_TITLE = "Вибір мови на обкладинці"
        private const val SITE_AGE_PREF = "site_age_categories"
        private const val SITE_AGE_PREF_TITLE = "Вікові обмеження"
        private const val SITE_AGE_PREF_DIALOG = "Контент з обраними віковими обмеженнями буде відображатися"
        private const val SITE_AGE_PREF_ALL = "Всі категорії (без обмежень)"
        private const val SITE_TOAST_WARNING = "Якщо мова обкладинки не змінилася, очистіть базу даних у програмі (Налаштування -> Додатково -> Очистити базу даних)"
        private const val SITE_CATEGORIES_PREF = "site_hidden_categories"
        private const val SITE_CATEGORIES_PREF_TITLE = "Приховані категорії"
        private const val SITE_CATEGORIES_PREF_DIALOG = "Оберіть категорії які не будуть відображатися"
        private const val SITE_CATEGORIES_DEFAULT = "RANOBE"
        private const val SITE_PREF_EXPLANATION = "\n\nⓘ Усі налаштування приховування та відображення контенту застосовуються до пошуку за назвою, \"Популярне\" та \"Новинки\".\nПошук без обмежень можливий у \"Фільтри\""
    }
}
