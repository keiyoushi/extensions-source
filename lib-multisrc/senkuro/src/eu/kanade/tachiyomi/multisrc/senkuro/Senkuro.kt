package eu.kanade.tachiyomi.multisrc.senkuro

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLPost
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.persistedQueryExtension
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

abstract class Senkuro(
    override val name: String,
    private val _baseUrl: String,
    final override val lang: String,
) : HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("User-Agent", "Tachiyomi (+https://github.com/keiyoushi/extensions-source)")
        .add("Content-Type", "application/json")
        .add("App-Id", if (name == "Senkuro") "4026531840100" else "5033164800100")
        .add("App-Version", "060626")

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val baseUrl: String
        get() = if (apiUrl.contains("api.senkuro.me")) "https://senkuro.me" else _baseUrl

    private val apiUrl: String
        get() = preferences.getString(API_DOMAIN_PREF, API_DOMAIN_DEFAULT)!! + "/graphql"

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(3)
        .build()

    // ============================== Popular ==============================
    private val popularCursors = mutableMapOf<Int, String>()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        if (page == 1) popularCursors.clear()
        return super.fetchPopularManga(page)
    }

    override fun popularMangaRequest(page: Int): Request {
        fetchTachiyomiSearchFilters(page)

        val cursor = if (page == 1) null else popularCursors[page - 1]
        val request = graphQLPost(
            url = apiUrl,
            headers = headers,
            operationName = "fetchMangas",
            variables = FetchMangasVariables(
                after = cursor,
                orderField = "POPULARITY_SCORE",
                orderDirection = "DESC",
            ),
            extensions = persistedQueryExtension(HASH_SEARCH),
        )

        return request.newBuilder().tag(PageTag::class.java, PageTag(page)).build()
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val page = response.request.tag(PageTag::class.java)?.page ?: 1
        val data = response.parseGraphQLAs<MangasResponseDto>()

        data.mangas.pageInfo.endCursor?.let {
            popularCursors[page] = it
        }

        val mangasList = data.mangas.edges.map { it.node.toSManga() }
        return MangasPage(mangasList, data.mangas.pageInfo.hasNextPage)
    }

    // ============================== Latest ===============================
    private val latestCursors = mutableMapOf<Int, String>()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        if (page == 1) latestCursors.clear()
        return super.fetchLatestUpdates(page)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val cursor = if (page == 1) null else latestCursors[page - 1]
        val request = graphQLPost(
            url = apiUrl,
            headers = headers,
            operationName = "fetchMangas",
            variables = FetchMangasVariables(
                after = cursor,
                orderField = "LAST_CHAPTER_AT",
                orderDirection = "DESC",
            ),
            extensions = persistedQueryExtension(HASH_SEARCH),
        )

        return request.newBuilder().tag(PageTag::class.java, PageTag(page)).build()
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val page = response.request.tag(PageTag::class.java)?.page ?: 1
        val data = response.parseGraphQLAs<MangasResponseDto>()

        data.mangas.pageInfo.endCursor?.let {
            latestCursors[page] = it
        }

        val mangasList = data.mangas.edges.map { it.node.toSManga() }
        return MangasPage(mangasList, data.mangas.pageInfo.hasNextPage)
    }

    // ============================== Search ===============================
    private val searchCursors = mutableMapOf<Int, String>()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (page == 1) searchCursors.clear()
        fetchTachiyomiSearchFilters(page)
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val includeGenres = mutableListOf<String>()
        val excludeGenres = mutableListOf<String>()
        val includeTypes = mutableListOf<String>()
        val excludeTypes = mutableListOf<String>()
        val includeFormats = mutableListOf<String>()
        val excludeFormats = mutableListOf<String>()
        val includeStatus = mutableListOf<String>()
        val excludeStatus = mutableListOf<String>()
        val includeTStatus = mutableListOf<String>()
        val excludeTStatus = mutableListOf<String>()
        val includeAges = mutableListOf<String>()
        val excludeAges = mutableListOf<String>()
        var orderField = "POPULARITY_SCORE"
        var orderDirection = "DESC"

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is OrderBy -> {
                    orderField = arrayOf("SCORE", "POPULARITY_SCORE")[filter.state!!.index]
                    orderDirection = if (filter.state!!.ascending) "ASC" else "DESC"
                }
                is GenreList -> filter.state.forEach {
                    if (it.state != Filter.TriState.STATE_IGNORE) {
                        if (it.isIncluded()) includeGenres.add(it.slug) else excludeGenres.add(it.slug)
                    }
                }
                is WorldsList -> filter.state.forEach {
                    if (it.state != Filter.TriState.STATE_IGNORE) {
                        if (it.isIncluded()) includeGenres.add(it.slug) else excludeGenres.add(it.slug)
                    }
                }
                is ElementsList -> filter.state.forEach {
                    if (it.state != Filter.TriState.STATE_IGNORE) {
                        if (it.isIncluded()) includeGenres.add(it.slug) else excludeGenres.add(it.slug)
                    }
                }
                is ChartsList -> filter.state.forEach {
                    if (it.state != Filter.TriState.STATE_IGNORE) {
                        if (it.isIncluded()) includeGenres.add(it.slug) else excludeGenres.add(it.slug)
                    }
                }
                is AgeDemoList -> filter.state.forEach {
                    if (it.state != Filter.TriState.STATE_IGNORE) {
                        if (it.isIncluded()) includeGenres.add(it.slug) else excludeGenres.add(it.slug)
                    }
                }
                is TypeList -> filter.state.forEach { type ->
                    if (type.state != Filter.TriState.STATE_IGNORE) {
                        if (type.isIncluded()) includeTypes.add(type.slug) else excludeTypes.add(type.slug)
                    }
                }
                is FormatList -> filter.state.forEach { format ->
                    if (format.state != Filter.TriState.STATE_IGNORE) {
                        if (format.isIncluded()) includeFormats.add(format.slug) else excludeFormats.add(format.slug)
                    }
                }
                is StatList -> filter.state.forEach { stat ->
                    if (stat.state != Filter.TriState.STATE_IGNORE) {
                        if (stat.isIncluded()) includeStatus.add(stat.slug) else excludeStatus.add(stat.slug)
                    }
                }
                is StatTranslateList -> filter.state.forEach { tstat ->
                    if (tstat.state != Filter.TriState.STATE_IGNORE) {
                        if (tstat.isIncluded()) includeTStatus.add(tstat.slug) else excludeTStatus.add(tstat.slug)
                    }
                }
                is AgeList -> filter.state.forEach { age ->
                    if (age.state != Filter.TriState.STATE_IGNORE) {
                        if (age.isIncluded()) includeAges.add(age.slug) else excludeAges.add(age.slug)
                    }
                }
                else -> {}
            }
        }

        val cursor = if (page == 1) null else searchCursors[page - 1]
        val request = graphQLPost(
            url = apiUrl,
            headers = headers,
            operationName = "fetchMangas",
            variables = FetchMangasVariables(
                after = cursor,
                search = query.takeIf { it.isNotEmpty() },
                orderField = orderField,
                orderDirection = orderDirection,
                label = ExcludeInclude(excludeGenres, includeGenres),
                type = ExcludeInclude(excludeTypes, includeTypes),
                format = ExcludeInclude(excludeFormats, includeFormats),
                status = ExcludeInclude(excludeStatus, includeStatus),
                translitionStatus = ExcludeInclude(excludeTStatus, includeTStatus),
                rating = ExcludeInclude(excludeAges, includeAges),
            ),
            extensions = persistedQueryExtension(HASH_SEARCH),
        )

        return request.newBuilder().tag(PageTag::class.java, PageTag(page)).build()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val page = response.request.tag(PageTag::class.java)?.page ?: 1
        val data = response.parseGraphQLAs<MangasResponseDto>()

        data.mangas.pageInfo.endCursor?.let {
            searchCursors[page] = it
        }

        val mangasList = data.mangas.edges.map { it.node.toSManga() }
        return MangasPage(mangasList, data.mangas.pageInfo.hasNextPage)
    }

    // ============================== Details ==============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.split(",,").last()
        return graphQLPost(
            url = apiUrl,
            headers = headers,
            operationName = "fetchManga",
            variables = FetchMangaVariables(slug = slug),
            extensions = persistedQueryExtension(HASH_DETAILS),
        )
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseGraphQLAs<MangaDetailsResponseDto>().manga.toSManga()

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.split(",,").last()
        return "$baseUrl/manga/$slug"
    }

    // ============================= Chapters ==============================
    private val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(mangaDetailsRequest(manga)).asObservableSuccess().flatMap { response ->
        val detailsDto = response.parseGraphQLAs<MangaDetailsResponseDto>().manga
        val branchId = detailsDto.branches?.firstOrNull { it.primaryBranch }?.id
            ?: detailsDto.branches?.firstOrNull()?.id
            ?: throw Exception("Не удалось найти ветку (Branch ID) для этого тайтла")

        val request = graphQLPost(
            url = apiUrl,
            headers = headers,
            operationName = "fetchMangaChapters",
            variables = FetchMangaChaptersVariables(branchId = branchId),
            extensions = persistedQueryExtension(HASH_CHAPTERS),
        )

        client.newCall(request).asObservableSuccess().map { chaptersResponse ->
            val chaptersDto = chaptersResponse.parseGraphQLAs<ChaptersResponseDto>()
            chaptersDto.mangaChapters.edges.map { edge ->
                val chapter = edge.node
                SChapter.create().apply {
                    chapter_number = chapter.number?.toFloatOrNull() ?: -2f
                    name = "${chapter.volume ?: "1"}. Глава ${chapter.number ?: ""} " + (chapter.name ?: "")
                    url = chapter.slug
                    date_upload = simpleDateFormat.tryParse(chapter.createdAt)
                    scanlator = chapter.creator?.name
                }
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ===============================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterSlug = chapter.url.split(",,").last()
        return graphQLPost(
            url = apiUrl,
            headers = headers,
            operationName = "fetchMangaChapter",
            variables = FetchMangaChapterVariables(slug = chapterSlug),
            extensions = persistedQueryExtension(HASH_PAGES),
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        // Fallback for missing manga slug, usually users open WebView from manga details anyway.
        val chapterSlug = chapter.url.split(",,").last()
        return "$baseUrl/manga/unavailable/chapters/$chapterSlug"
    }

    override fun pageListParse(response: Response): List<Page> {
        val pagesDto = response.parseGraphQLAs<ChapterPagesResponseDto>().mangaChapter.pages ?: emptyList()
        return pagesDto.mapIndexed { index, page ->
            Page(index, imageUrl = page.image?.original?.url ?: page.image?.compress?.url ?: "")
        }
    }

    override fun imageUrlRequest(page: Page): Request = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun fetchImageUrl(page: Page): Observable<String> = Observable.just(page.imageUrl!!)

    // ============================== Filters ==============================
    private fun fetchTachiyomiSearchFilters(pageRequest: Int) {
        if (pageRequest == 1) {
            val response = client.newCall(
                graphQLPost(
                    url = apiUrl,
                    headers = headers,
                    operationName = "fetchMangaFilters",
                    variables = EmptyObject,
                    extensions = persistedQueryExtension(HASH_FILTERS),
                ),
            ).execute()

            val filterDto = response.parseGraphQLAs<FiltersResponseDto>()
            labelsList = filterDto.allLabels.map { label ->
                FilterersTriRoot(
                    label.titles.find { it.lang == "RU" }?.content?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: label.slug,
                    label.slug,
                    label.rootId,
                )
            }.sortedBy { it.name }
        }
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters += listOf(
            OrderBy(),
            TypeList(getTypeList()),
            FormatList(getFormatList()),
            StatList(getStatList()),
            StatTranslateList(getStatTranslateList()),
            AgeList(getAgeList()),
        )
        filters += if (labelsList.isEmpty()) {
            listOf(
                Filter.Separator(),
                Filter.Header("Нажмите «Сбросить», чтобы загрузить все фильтры"),
                Filter.Separator(),
            )
        } else {
            listOf(
                GenreList(labelsList.filter { it.rootId == "TEFCRUw6NQ" }), // Темы
                WorldsList(labelsList.filter { it.rootId == "TEFCRUw6NA" }), // Сеттинг
                ElementsList(labelsList.filter { it.rootId == "TEFCRUw6Ng" }), // Элементы
                ChartsList(labelsList.filter { it.rootId == "TEFCRUw6Mw" }), // Черты
                AgeDemoList(labelsList.filter { it.rootId == "TEFCRUw6Nw" }), // Демография
            )
        }
        return FilterList(filters)
    }

    private class PageTag(val page: Int)
    private class FilterersTriRoot(name: String, val slug: String, val rootId: String) : Filter.TriState(name)
    private class GenreList(labels: List<FilterersTriRoot>) : Filter.Group<FilterersTriRoot>("Темы", labels)
    private class WorldsList(labels: List<FilterersTriRoot>) : Filter.Group<FilterersTriRoot>("Сеттинг", labels)
    private class ElementsList(labels: List<FilterersTriRoot>) : Filter.Group<FilterersTriRoot>("Элементы", labels)
    private class ChartsList(labels: List<FilterersTriRoot>) : Filter.Group<FilterersTriRoot>("Черты", labels)
    private class AgeDemoList(labels: List<FilterersTriRoot>) : Filter.Group<FilterersTriRoot>("Демография", labels)
    private class FilterersTri(name: String, val slug: String) : Filter.TriState(name)
    private class TypeList(types: List<FilterersTri>) : Filter.Group<FilterersTri>("Тип", types)
    private class FormatList(formats: List<FilterersTri>) : Filter.Group<FilterersTri>("Формат", formats)
    private class StatList(status: List<FilterersTri>) : Filter.Group<FilterersTri>("Статус", status)
    private class StatTranslateList(tstatus: List<FilterersTri>) : Filter.Group<FilterersTri>("Статус перевода", tstatus)
    private class AgeList(ages: List<FilterersTri>) : Filter.Group<FilterersTri>("Возрастное ограничение", ages)

    private var labelsList: List<FilterersTriRoot> = emptyList()

    private class OrderBy :
        Filter.Sort(
            "Сортировка",
            arrayOf("По рейтингу", "По популярности"),
            Selection(1, false),
        )

    private fun getTypeList() = listOf(
        FilterersTri("Манга", "MANGA"),
        FilterersTri("Манхва", "MANHWA"),
        FilterersTri("Маньхуа", "MANHUA"),
        FilterersTri("Комикс", "COMICS"),
        FilterersTri("OEL Манга", "OEL_MANGA"),
        FilterersTri("РуМанга", "RU_MANGA"),
    )

    private fun getStatList() = listOf(
        FilterersTri("Анонс", "ANNOUNCE"),
        FilterersTri("Онгоинг", "ONGOING"),
        FilterersTri("Выпущено", "FINISHED"),
        FilterersTri("Приостановлено", "HIATUS"),
        FilterersTri("Отменено", "CANCELLED"),
    )

    private fun getStatTranslateList() = listOf(
        FilterersTri("Переводится", "IN_PROGRESS"),
        FilterersTri("Завершён", "FINISHED"),
        FilterersTri("Заморожен", "FROZEN"),
        FilterersTri("Заброшен", "ABANDONED"),
    )

    private fun getAgeList() = listOf(
        FilterersTri("0+", "GENERAL"),
        FilterersTri("12+", "SENSITIVE"),
        FilterersTri("16+", "QUESTIONABLE"),
        FilterersTri("18+", "EXPLICIT"),
    )

    private fun getFormatList() = listOf(
        FilterersTri("Сборник", "DIGEST"),
        FilterersTri("Додзинси", "DOUJINSHI"),
        FilterersTri("В цвете", "IN_COLOR"),
        FilterersTri("Сингл", "SINGLE"),
        FilterersTri("Веб", "WEB"),
        FilterersTri("Вебтун", "WEBTOON"),
        FilterersTri("Ёнкома", "YONKOMA"),
        FilterersTri("Short", "SHORT"),
    )

    // ============================= Utilities =============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = API_DOMAIN_PREF
            title = API_DOMAIN_TITLE
            entries = arrayOf("Публичный (senkuro.com)", "Россия (senkuro.me)")
            entryValues = arrayOf(API_DOMAIN_DEFAULT, "https://api.senkuro.me")
            summary = "%s"
            setDefaultValue(API_DOMAIN_DEFAULT)
        }.let(screen::addPreference)
    }

    companion object {
        private const val API_DOMAIN_PREF = "MangaApiDomain"
        private const val API_DOMAIN_TITLE = "Домен"
        private const val API_DOMAIN_DEFAULT = "https://api.senkuro.com"

        // APQ SHA-256 Hashes
        private const val HASH_SEARCH = "2e239cbedda2c8af91bb0f86149b26889f2f800dc08ba36417cdecb91614799e"
        private const val HASH_DETAILS = "062d157eb1158bd14aa95de219d125aa98bc00750f499970893287f1781f0770"
        private const val HASH_CHAPTERS = "8c854e121f05aa93b0c37889e732410df9ea207b4186c965c845a8d970bdcc12"
        private const val HASH_PAGES = "8e166106650d3659d21e7aadc15e7e59e5def36f1793a9b15287c73a1e27aa50"
        private const val HASH_FILTERS = "1e4fb028e6a80b23b4f6840159e2b9cbfb8b19da4341ebc064d4e74bf8daa9a3"
    }
}
