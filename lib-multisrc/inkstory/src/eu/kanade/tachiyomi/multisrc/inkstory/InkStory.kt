package eu.kanade.tachiyomi.multisrc.inkstory

import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.Fetch
import keiyoushi.network.XorInterceptor
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

abstract class InkStory :
    KeiSource(),
    ConfigurableSource {

    abstract val serviceName: String

    private val preferences by getPreferencesLazy {
        val mode = getString(PREF_LEGACY_BRANCH_MODE, null)
        val query = getString(PREF_LEGACY_BRANCH_QUERY, null)
        val legacyKeys = listOf(PREF_LEGACY_BRANCH_MODE, PREF_LEGACY_BRANCH_QUERY, "inkstory_image_quality", "inkstory_image_type", "inkstory_image_width")
        if (legacyKeys.any(::contains)) {
            edit().apply {
                if (mode == "latest" || mode == "preferred") putBoolean(PREF_DEDUPE, true)
                if (mode == "preferred" && !query.isNullOrBlank()) putString(PREF_PREFERRED_GROUPS, query)
                legacyKeys.forEach(::remove)
            }.apply()
        }
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        // User Agent required by source. Don't change
        set("User-Agent", "Tachiyomi (+https://github.com/keiyoushi/extensions-source)")
    }

    // Fetch adds no fetch metadata for the UA above, and server functions refuse requests without it
    private val serverFnHeaders get() = headers.newBuilder()
        .set("Accept", "application/json")
        .set("x-tsr-serverFn", "true")
        .set("Sec-Fetch-Site", "same-origin")
        .build()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(2) { it.host == baseUrl.toHttpUrl().host }
        addInterceptor(XorInterceptor(IMAGE_KEY) { it.url.isXorImage() })
    }

    private suspend inline fun <reified T> serverFnOrNull(id: String, data: JsonElement): T? {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("_serverFn")
            .addPathSegment(id)
            .addQueryParameter("payload", serovalPayload(data))
            .build()
        return client.get(url, serverFnHeaders, Fetch.XHR).parseAs<JsonObject>().serverFnResult()?.parseAs<T>()
    }

    private suspend inline fun <reified T> serverFn(id: String, data: JsonElement): T = serverFnOrNull<T>(id, data) ?: throw Exception("Not found")

    private suspend fun getBook(slug: String): BookDto? = serverFnOrNull(FN_BOOK, BookInput(slug, createView = false).toJsonElement())

    private suspend fun catalogue(input: CatalogueInput, page: Int): MangasPage {
        val catalogue = serverFn<CatalogueDto>(FN_CATALOGUE, input.toJsonElement())
        val titleLanguage = prefTitleLanguage
        return MangasPage(catalogue.books.map { it.toSManga(titleLanguage) }, page * PAGE_SIZE < catalogue.total)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = catalogue(CatalogueInput(PAGE_SIZE, page - 1, sort = "viewsCount", orderBy = "desc", readingRestriction = READING_RESTRICTIONS), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = catalogue(CatalogueInput(PAGE_SIZE, page - 1, sort = "latestChapterAt", orderBy = "desc", readingRestriction = READING_RESTRICTIONS), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()
        val genres = filters.firstInstanceOrNull<GenreFilter>()
        val tags = filters.firstInstanceOrNull<TagFilter>()
        val rating = filters.firstInstanceOrNull<RatingFilter>()
        val year = filters.firstInstanceOrNull<YearFilter>()
        val chapters = filters.firstInstanceOrNull<ChaptersFilter>()
        val input = CatalogueInput(
            size = PAGE_SIZE,
            page = page - 1,
            sort = sort?.selected ?: "viewsCount",
            orderBy = sort?.order ?: "desc",
            readingRestriction = READING_RESTRICTIONS,
            search = query.trim().ifEmpty { null },
            labelsInclude = (genres?.included.orEmpty() + tags?.included.orEmpty()).ifEmpty { null },
            labelsExclude = (genres?.excluded.orEmpty() + tags?.excluded.orEmpty()).ifEmpty { null },
            strictLabelEqual = filters.firstInstanceOrNull<StrictLabelEqualFilter>()?.state?.takeIf { it },
            status = filters.firstInstanceOrNull<StatusFilter>()?.selected,
            country = filters.firstInstanceOrNull<CountryFilter>()?.selected,
            contentStatus = filters.firstInstanceOrNull<ContentStatusFilter>()?.selected,
            formats = filters.firstInstanceOrNull<FormatFilter>()?.selected,
            averageRatingMin = rating?.min,
            averageRatingMax = rating?.max,
            yearMin = year?.min,
            yearMax = year?.max,
            chaptersCountMin = chapters?.min,
            chaptersCountMax = chapters?.max,
        )
        return catalogue(input, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "content") return null
        val slug = url.pathSegments.getOrNull(1)?.ifEmpty { null } ?: return null
        return getBook(slug)?.toSMangaDetails(prefTitleLanguage)?.apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/content/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val bookId = manga.memo["id"]?.string
        val details = async {
            if (fetchDetails || bookId == null) (getBook(manga.url) ?: throw Exception("Not found")).toSMangaDetails(prefTitleLanguage) else null
        }
        val chapterList = async {
            if (fetchChapters) getChapters(manga.url, bookId ?: details.await()!!.memo["id"]!!.string) else chapters
        }
        SMangaUpdate(details.await() ?: manga, chapterList.await())
    }

    private suspend fun getChapters(slug: String, bookId: String): List<SChapter> {
        val blocked = prefBlockedGroups
        val chapterList = serverFn<ChapterListDto>(FN_CHAPTERS, bookId.toJsonElement())
        val chapters = chapterList.chapters.filter { chapter -> chapter.groups().none { it in blocked } }
        val shown = if (prefDedupe) {
            val preferred = prefPreferredGroups
            val branchOrder = chapterList.branches.withIndex().associate { it.value.id to it.index }
            chapters.groupBy { it.key }.values.map { uploads ->
                preferred.firstNotNullOfOrNull { group -> uploads.firstOrNull { group in it.groups() } }
                    ?: uploads.minBy { branchOrder[it.branchId] ?: Int.MAX_VALUE }
            }
        } else {
            chapters
        }
        val list = shown.map { it.toSChapter(slug) }
        if (!prefDelay) return list
        val hideAfter = Clock.System.now().minus(4.days).toEpochMilliseconds()
        return list.filter { it.date_upload <= hideAfter }
    }

    private fun ChapterDto.groups() = publisherNames.orEmpty().split(',').map { it.trim().lowercase() }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/content/${chapter.memo["slug"]!!.string}/${chapter.url}"

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val bookId = manga.memo["id"]?.string ?: getBook(manga.url)!!.id
        val input = CatalogueInput(RELATED_SIZE, 0, relatedBookId = bookId)
        val titleLanguage = prefTitleLanguage
        return serverFn<CatalogueDto>(FN_CATALOGUE, input.toJsonElement()).books.map { it.toSManga(titleLanguage) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = serverFn<ChapterPagesDto>(FN_CHAPTER, ChapterInput(chapter.url, createView = false).toJsonElement())
        .pages
        .sortedBy { it.index }
        .mapIndexed { index, page -> Page(index, imageUrl = page.image) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$API_URL/labels".toHttpUrl().newBuilder()
            .addQueryParameter("serviceName", serviceName)
            .build()
        val labels = client.get(url, Fetch.XHR).parseAs<List<LabelListItemDto>>()
        return FilterData(
            genres = labels.filter { it.kind == "GENRE" }.map { it.name to it.id },
            tags = labels.filter { it.kind == "TAG" }.map { it.name to it.id },
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val labels = data?.parseAs<FilterData>()
        return FilterList(
            listOfNotNull<Filter<*>>(
                SortFilter(),
                labels?.genres?.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
                labels?.tags?.takeIf { it.isNotEmpty() }?.let(::TagFilter),
                labels?.let { StrictLabelEqualFilter() },
                StatusFilter(),
                FormatFilter(),
                CountryFilter(),
                ContentStatusFilter(),
                RatingFilter(),
                YearFilter(),
                ChaptersFilter(),
            ),
        )
    }

    private val prefDedupe get() = preferences.getBoolean(PREF_DEDUPE, false)
    private val prefPreferredGroups get() = preferences.getString(PREF_PREFERRED_GROUPS, "")!!.groupList()
    private val prefBlockedGroups get() = preferences.getString(PREF_BLOCKED_GROUPS, "")!!.groupList().toSet()
    private val prefDelay get() = preferences.getBoolean(PREF_DELAY, false)
    private val prefTitleLanguage get() = preferences.getString(PREF_TITLE_LANGUAGE, "ru")!!

    private fun String.groupList() = split(',').map { it.trim().lowercase() }.filter(String::isNotEmpty)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_TITLE_LANGUAGE
            title = "Язык названий"
            entries = arrayOf("Русский", "Английский", "Оригинальный")
            entryValues = arrayOf("ru", "en", "original")
            summary = "%s"
            setDefaultValue("ru")
        }.let(screen::addPreference)

        val preferredGroups = EditTextPreference(screen.context).apply {
            key = PREF_PREFERRED_GROUPS
            title = "Предпочитаемые команды"
            dialogTitle = title
            summary = "Через запятую, по порядку. Если ни одной нет, остаётся ветка, которую сайт показывает первой"
            setDefaultValue("")
        }
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_DEDUPE
            title = "Одна версия каждой главы"
            summaryOff = "Главы всех команд"
            summaryOn = "Одна глава на номер, по предпочитаемым командам"
            setDefaultValue(false)
            setOnPreferenceChangeListener { _, value ->
                preferredGroups.setEnabled(value as Boolean)
                true
            }
        }.let(screen::addPreference)
        preferredGroups.setEnabled(prefDedupe)
        screen.addPreference(preferredGroups)

        EditTextPreference(screen.context).apply {
            key = PREF_BLOCKED_GROUPS
            title = "Скрытые команды"
            dialogTitle = title
            summary = "Через запятую. Главы этих команд не показываются"
            setDefaultValue("")
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_DELAY
            title = "Скрывать главы"
            summaryOn = "Главы новее 4 дней скрыты"
            summaryOff = "Показываются все главы"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    // The site marks XOR'd pages with an `x` in the uuid file name's version slot
    private fun HttpUrl.isXorImage(): Boolean {
        val name = pathSegments.last().substringBeforeLast('.')
        return host == IMAGE_HOST && name.length == 36 && name[14] == 'x'
    }

    companion object {
        private const val API_URL = "https://api.inuko.me/v2"
        private const val IMAGE_HOST = "static.inuko.me"
        private const val PAGE_SIZE = 40
        private const val RELATED_SIZE = 20

        // Server function ids: SHA-256 of the function's place in the site's code, the same for every inuko.me site
        private const val FN_CATALOGUE = "5e920c6f2538a89f74b8cc297001d862340f690c4684e8ab6c14522960998b2e"
        private const val FN_BOOK = "f1eb190aebb53f98ebd70df7ef5ec57105819b52503dba8d8943811d2bced2f2"
        private const val FN_CHAPTERS = "1c9d8e028fe5eab8c867ee5fc39b0a75acc20270c6cecf479cfd582404e947bc"
        private const val FN_CHAPTER = "c342d30c43b5931eec428542eb9d2bf68431a41a046ae931929f8f018d7f5505"

        private val READING_RESTRICTIONS = listOf("NONE", "AUTHORIZED_ONLY", "LIBRARY_ONLY")
        private val IMAGE_KEY = "UySkp0BzPhwlvP2V".toByteArray()

        private const val PREF_DEDUPE = "inkstory_one_per_chapter"
        private const val PREF_PREFERRED_GROUPS = "inkstory_preferred_groups"
        private const val PREF_BLOCKED_GROUPS = "inkstory_blocked_groups"
        private const val PREF_DELAY = "delay_chapters"
        private const val PREF_TITLE_LANGUAGE = "inkstory_title_language"
        private const val PREF_LEGACY_BRANCH_MODE = "inkstory_chapter_branch_mode"
        private const val PREF_LEGACY_BRANCH_QUERY = "inkstory_preferred_branch_query"
    }
}
