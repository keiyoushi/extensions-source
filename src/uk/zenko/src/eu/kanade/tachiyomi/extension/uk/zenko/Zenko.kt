package eu.kanade.tachiyomi.extension.uk.zenko

import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Calendar

@Source
abstract class Zenko :
    KeiSource(),
    ConfigurableSource {
    private val domain by lazy { baseUrl.toHttpUrl().host }
    private val apiUrl by lazy { "https://api.$domain" }
    private val imgUrl by lazy { "https://storage.$domain" }
    private val apiUrlHost by lazy { "api.$domain" }

    override val supportsLatest = true
    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(10) { it.host == apiUrlHost }
    }

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest(page, "viewsCount")

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest(page, "lastChapterCreatedAt")

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty() && query.length < 2) {
            throw Exception("Запит має містити щонайменше 2 символи / The query must contain at least 2 characters")
        }
        return makeCatalogRequest(page, "viewsCount", query, filters)
    }

    // =============================== Latest/Popular/Search Utilities ===============================
    private suspend fun makeCatalogRequest(page: Int, sortBy: String, query: String? = null, filters: FilterList? = null): MangasPage {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val url = "$apiUrl/titles".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", "15")
            addQueryParameter("offset", ((page - 1) * 15).toString())

            filters?.forEach { filter ->
                when (filter) {
                    is OrderBy -> {
                        addQueryParameter("sortBy", filter.selected)
                        addQueryParameter("order", filter.order)
                    }
                    is CategoryFilter -> filter.checked?.let { addQueryParameter("categories", it.joinToString(",")) }
                    is StatusFilter -> filter.checked?.let { addQueryParameter("status", it.joinToString(",")) }
                    is TranslationStatusFilter -> filter.checked?.let { addQueryParameter("translationStatus", it.joinToString(",")) }
                    is GenresFilter -> {
                        filter.included?.let { addQueryParameter("genres", it.joinToString(",")) }
                        filter.excluded?.let { addQueryParameter("excludeGenres", it.joinToString(",")) }
                    }
                    is TagsFilter -> {
                        filter.included?.let { addQueryParameter("tags", it.joinToString(",")) }
                        filter.excluded?.let { addQueryParameter("excludeTags", it.joinToString(",")) }
                    }
                    is AgeFilter -> filter.checked?.let { addQueryParameter("ageLimit", it.joinToString(",")) }
                    is YearRangeFilter -> {
                        filter.minValue?.let { addQueryParameter("releaseYearFrom", checkMinRange(it, max = currentYear)) }
                        filter.maxValue?.let { addQueryParameter("releaseYearTo", checkMaxRange(it, max = currentYear)) }
                    }
                    else -> {}
                }
            }

            if (filters == null) {
                val hiddenCat = hiddenCategories()
                val ageCat = ageLimit()
                val hiddenGenres = blockGenres()
                addQueryParameter("sortBy", sortBy)
                addQueryParameter("order", "DESC")

                if (hiddenCat.isNotEmpty()) {
                    val categoriesSetting = CategoryFilter.categories.filter { !hiddenCat.contains(it.second) }.joinToString(",") { it.second }
                    addQueryParameter("categories", categoriesSetting)
                }
                if (ageCat.isNotEmpty()) {
                    val ageLimitSetting = ageCat.joinToString(",")
                    addQueryParameter("ageLimit", ageLimitSetting)
                }
                if (hiddenGenres.isNotEmpty()) {
                    val hiddenGenresSetting = hiddenGenres.joinToString(",")
                    addQueryParameter("excludeGenres", hiddenGenresSetting)
                }
            }

            if (!query.isNullOrEmpty()) {
                addQueryParameter("name", query)
            }
        }.build()

        client.get(url).use { response ->
            val result = response.parseAs<SearchResponse>()
            val lang = isEng()
            val mangas = result.data.map { it.toSManga(lang, imgUrl) }
            return MangasPage(mangas, result.meta.hasNextPage)
        }
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "titles") {
            val mangaId = url.pathSegments[1]

            val tmpManga = SManga.create().apply {
                this.url = "/titles/$mangaId"
            }

            return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }

        return null
    }

    // =========================== Manga Details ============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = manga.url.substringAfter("titles/")

        val mangaAsync = async {
            if (fetchDetails) {
                val lang = isEng()
                val url = "$apiUrl/titles/$mangaId"
                val data = client.get(url).use { it.parseAs<MangaDetailsResponse>() }
                data.toSManga(lang, imgUrl)
            } else {
                manga
            }
        }

        val chaptersAsync = async {
            if (fetchChapters) {
                val chaptersUrl = "$apiUrl/titles/$mangaId/chapters"
                val data = client.get(chaptersUrl).use { it.parseAs<List<ChapterResponseItem>>() }
                data.sortedByDescending { item ->
                    val id = StringProcessor.generateId(item.name)
                    if (id > 0) id else item.id.toDouble()
                }.map { it.toSChapter() }
            } else {
                chapters
            }
        }

        SMangaUpdate(mangaAsync.await(), chaptersAsync.await())
    }

    // =========================== Related Manga (Komikku) ============================
    override val supportsRelatedMangas: Boolean = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val mangaId = manga.url.substringAfter("titles/")
        val url = "$apiUrl/titles/$mangaId/similar"
        val data = client.get(url).use { it.parseAs<List<SearchDetailsResponse>>() }
        val hiddenCat = hiddenCategories()
        return data.filter { !hiddenCat.contains(it.category) }.map { it.toSManga(lang, imgUrl) }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (_, chapterId) = chapter.url.substringAfter("titles/").split("/", limit = 2)
        val url = "$apiUrl/chapters/$chapterId"
        val data = client.get(url).use { it.parseAs<ChapterResponseItem>() }
        return data.pages.orEmpty().map { page ->
            Page(page.order, imageUrl = "$imgUrl/${page.content}")
        }
    }

    // ============================= Filters ==============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        OrderBy(),
        CategoryFilter(hiddenCategories()),
        StatusFilter(),
        TranslationStatusFilter(),
        AgeFilter(ageLimit()),
        GenresFilter(blockGenres()),
        TagsFilter(),
        YearRangeFilter(),
    )

    // ============================ Preferences =============================
    private fun isEng(): String = preferences.getString(LANGUAGE_PREF, "ua") ?: "ua"
    private fun ageLimit(): Set<String> = preferences.getStringSet(SITE_AGE_PREF, emptySet()) ?: emptySet()
    private fun hiddenCategories(): Set<String> = preferences.getStringSet(SITE_CATEGORIES_PREF, setOf(SITE_CATEGORIES_DEFAULT)) ?: setOf(SITE_CATEGORIES_DEFAULT)
    private fun blockGenres(): Set<String> = preferences.getStringSet(GENRES_PREF, emptySet()) ?: emptySet()

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
            val tags = GenresFilter.genres
            key = GENRES_PREF
            title = GENRES_PREF_TITLE
            entries = tags.map { it.first }.toTypedArray()
            entryValues = tags.map { it.second }.toTypedArray()
            summary = blockGenres().joinToString()
            dialogTitle = GENRES_PREF_DIALOG
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                val selected = values as Set<*>
                this.summary = selected.joinToString()
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

    private fun checkMinRange(input: String?, min: Int = 1980, max: Int): String {
        val value = input?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull() ?: return min.toString()
        if (value !in min..max) return min.toString()
        return value.toString()
    }
    private fun checkMaxRange(input: String?, min: Int = 1980, max: Int): String {
        val value = input?.trim()?.takeIf(String::isNotEmpty)?.toIntOrNull() ?: return max.toString()
        if (value !in min..max) return max.toString()
        return value.toString()
    }

    companion object {
        private const val LANGUAGE_PREF = "TitleLanguagePref"
        private const val LANGUAGE_PREF_TITLE = "Вибір мови на обкладинці"
        private const val GENRES_PREF = "pref_genres_exclude"
        private const val GENRES_PREF_TITLE = "Приховані жанри"
        private const val GENRES_PREF_DIALOG = "Виберіть жанри які потрібно сховати"
        private const val SITE_AGE_PREF = "site_age_categories"
        private const val SITE_AGE_PREF_TITLE = "Вікові обмеження"
        private const val SITE_AGE_PREF_DIALOG = "Контент з обраними віковими обмеженнями буде відображатися"
        private const val SITE_AGE_PREF_ALL = "Всі категорії (без обмежень)"
        private const val SITE_TOAST_WARNING = "Якщо мова обкладинки не змінилася, очистить базу даних у програмі (Налаштування -> Додатково -> Очистити базу даних)"
        private const val SITE_CATEGORIES_PREF = "site_hidden_categories"
        private const val SITE_CATEGORIES_PREF_TITLE = "Приховані категорії"
        private const val SITE_CATEGORIES_PREF_DIALOG = "Оберіть категорії які не будуть відображатися"
        private const val SITE_CATEGORIES_DEFAULT = "RANOBE"
        private const val SITE_PREF_EXPLANATION = "\n\nⓘ Усі налаштування приховування та відображення контенту застосовуються до пошуку за назвою, \"Популярне\" та \"Новинки\".\nПошук без обмежень можливий у \"Фільтри\""
    }
}
