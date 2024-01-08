package eu.kanade.tachiyomi.multisrc.senkuro

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

abstract class Senkuro(
    override val name: String,
    override val baseUrl: String,
    final override val lang: String,
) : ConfigurableSource, HttpSource() {

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Tachiyomi (+https://github.com/tachiyomiorg/tachiyomi)")
        .add("Content-Type", "application/json")

    override val client: OkHttpClient =
        network.client.newBuilder()
            .rateLimit(5)
            .build()

    private inline fun <reified T : Any> T.toJsonRequestBody(): RequestBody =
        json.encodeToString(this)
            .toRequestBody(JSON_MEDIA_TYPE)

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val requestBody = GraphQL(
            SEARCH_QUERY,
            SearchVariables(
                offset = offsetCount * (page - 1),
                genre = SearchVariables.FiltersDto(
                    // Senkuro eternal built-in exclude 18+ filter
                    exclude = if (name == "Senkuro") { senkuroExcludeGenres } else { listOf() },
                ),
            ),
        ).toJsonRequestBody()

        fetchTachiyomiSearchFilters(page)

        return POST(API_URL, headers, requestBody)
    }
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = throw NotImplementedError("Unused")

    override fun latestUpdatesParse(response: Response): MangasPage = throw NotImplementedError("Unused")

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        fetchTachiyomiSearchFilters(page) // reset filters before sending searchMangaRequest
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val includeGenres = mutableListOf<String>()
        val excludeGenres = mutableListOf<String>()
        val includeTags = mutableListOf<String>()
        val excludeTags = mutableListOf<String>()
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

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        if (genre.isIncluded()) includeGenres.add(genre.slug) else excludeGenres.add(genre.slug)
                    }
                }
                is TagList -> filter.state.forEach { tag ->
                    if (tag.state != Filter.TriState.STATE_IGNORE) {
                        if (tag.isIncluded()) includeTags.add(tag.slug) else excludeTags.add(tag.slug)
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

        // Senkuro eternal built-in exclude 18+ filter
        if (name == "Senkuro") {
            excludeGenres.addAll(senkuroExcludeGenres)
        }

        val requestBody = GraphQL(
            SEARCH_QUERY,
            SearchVariables(
                query = query, offset = offsetCount * (page - 1),
                genre = SearchVariables.FiltersDto(
                    includeGenres,
                    excludeGenres,
                ),
                tag = SearchVariables.FiltersDto(
                    includeTags,
                    excludeTags,
                ),
                type = SearchVariables.FiltersDto(
                    includeTypes,
                    excludeTypes,
                ),
                format = SearchVariables.FiltersDto(
                    includeFormats,
                    excludeFormats,
                ),
                status = SearchVariables.FiltersDto(
                    includeStatus,
                    excludeStatus,
                ),
                translationStatus = SearchVariables.FiltersDto(
                    includeTStatus,
                    excludeTStatus,
                ),
                rating = SearchVariables.FiltersDto(
                    includeAges,
                    excludeAges,
                ),
            ),
        ).toJsonRequestBody()

        return POST(API_URL, headers, requestBody)
    }
    override fun searchMangaParse(response: Response): MangasPage {
        val page = json.decodeFromString<PageWrapperDto<MangaTachiyomiSearchDto<MangaTachiyomiInfoDto>>>(response.body.string())
        val mangasList = page.data.mangaTachiyomiSearch.mangas.map {
            it.toSManga()
        }

        return MangasPage(mangasList, mangasList.isNotEmpty())
    }

    // Details
    private fun parseStatus(status: String?): Int {
        return when (status) {
            "FINISHED" -> SManga.COMPLETED
            "ONGOING" -> SManga.ONGOING
            "HIATUS" -> SManga.ON_HIATUS
            "ANNOUNCE" -> SManga.ONGOING
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun MangaTachiyomiInfoDto.toSManga(): SManga {
        val o = this
        return SManga.create().apply {
            title = titles.find { it.lang == "RU" }?.content ?: titles.find { it.lang == "EN" }?.content ?: titles[0].content
            url = "$id,,$slug" // mangaId[0],,mangaSlug[1]
            thumbnail_url = cover?.original?.url
            var altName = alternativeNames?.joinToString(" / ") { it.content }
            if (!altName.isNullOrEmpty()) {
                altName = "Альтернативные названия:\n$altName\n\n"
            }
            author = mainStaff?.filter { it.roles.contains("STORY") }?.joinToString(", ") { it.person.name }
            artist = mainStaff?.filter { it.roles.contains("ART") }?.joinToString(", ") { it.person.name }
            description = altName + localizations?.find { it.lang == "RU" }?.description.orEmpty()
            status = parseStatus(o.status)
            genre = (
                getTypeList().find { it.slug == type }?.name + ", " +
                    getAgeList().find { it.slug == rating }?.name + ", " +
                    getFormatList().filter { formats.orEmpty().contains(it.slug) }.joinToString { it.name } + ", " +
                    genres?.joinToString { git -> git.titles.find { it.lang == "RU" }!!.content } + ", " +
                    tags?.joinToString { tit -> tit.titles.find { it.lang == "RU" }!!.content }
                ).split(", ").filter { it.isNotEmpty() }.joinToString { it.trim().capitalize() }
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val requestBody = GraphQL(
            DETAILS_QUERY,
            FetchDetailsVariables(mangaId = manga.url.split(",,")[0]),
        ).toJsonRequestBody()

        return POST(API_URL, headers, requestBody)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val series = json.decodeFromString<PageWrapperDto<SubInfoDto>>(response.body.string())
        return series.data.mangaTachiyomiInfo.toSManga()
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + "/manga/" + manga.url.split(",,")[1]

    // Chapters
    private val simpleDateFormat by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S", Locale.ROOT) }

    private fun parseDate(date: String?): Long {
        date ?: return 0L
        return try {
            simpleDateFormat.parse(date)!!.time
        } catch (_: Exception) {
            Date().time
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga)
            }
    }
    override fun chapterListParse(response: Response) = throw UnsupportedOperationException("chapterListParse(response: Response, manga: SManga)")
    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val chaptersList = json.decodeFromString<PageWrapperDto<MangaTachiyomiChaptersDto>>(response.body.string())
        val teamsList = chaptersList.data.mangaTachiyomiChapters.teams
        return chaptersList.data.mangaTachiyomiChapters.chapters.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.number.toFloatOrNull() ?: -2F
                name = "${chapter.volume}. Глава ${chapter.number} " + (chapter.name ?: "")
                url = "${manga.url},,${chapter.id},,${chapter.slug}" // mangaId[0],,mangaSlug[1],,chapterId[2],,chapterSlug[3]
                date_upload = parseDate(chapter.createdAt)
                scanlator = teamsList.filter { chapter.teamIds.contains(it.id) }.joinToString { it.name }
            }
        }
    }
    override fun chapterListRequest(manga: SManga): Request {
        val requestBody = GraphQL(
            CHAPTERS_QUERY,
            FetchDetailsVariables(mangaId = manga.url.split(",,")[0]),
        ).toJsonRequestBody()

        return POST(API_URL, headers, requestBody)
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val mangaChapterId = chapter.url.split(",,")
        val requestBody = GraphQL(
            CHAPTERS_PAGES_QUERY,
            FetchChapterPagesVariables(mangaId = mangaChapterId[0], chapterId = mangaChapterId[2]),
        ).toJsonRequestBody()

        return POST(API_URL, headers, requestBody)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaChapterSlug = chapter.url.split(",,")
        return baseUrl + "/manga/" + mangaChapterSlug[1] + "/chapters/" + mangaChapterSlug[3]
    }

    override fun pageListParse(response: Response): List<Page> {
        val imageList = json.decodeFromString<PageWrapperDto<MangaTachiyomiChapterPages>>(response.body.string())
        return imageList.data.mangaTachiyomiChapterPages.pages.mapIndexed { index, page ->
            Page(index, "", page.url)
        }
    }

    override fun imageUrlRequest(page: Page): Request = throw NotImplementedError("Unused")

    override fun imageUrlParse(response: Response): String = throw NotImplementedError("Unused")

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.url)
    }

    // Filters
    // Filters are fetched immediately once an extension loads
    // We're only able to get filters after a loading the manga directory, and resetting
    // the filters is the only thing that seems to reinflate the view
    private fun fetchTachiyomiSearchFilters(pageRequest: Int) {
        // The function must be used in PopularMangaRequest and fetchSearchManga to correctly/guaranteed reset the selected filters!
        if (pageRequest == 1) {
            val responseBody = client.newCall(
                POST(
                    API_URL,
                    headers,
                    GraphQL(
                        FILTERS_QUERY,
                        SearchVariables(),
                    ).toJsonRequestBody(),
                ),
            ).execute().body.string()

            val filterDto =
                json.decodeFromString<PageWrapperDto<MangaTachiyomiSearchFilters>>(responseBody).data.mangaTachiyomiSearchFilters

            genresList =
                filterDto.genres.filterNot { name == "Senkuro" && senkuroExcludeGenres.contains(it.slug) }
                    .map { genre ->
                        FilterersTri(
                            genre.titles.find { it.lang == "RU" }!!.content.capitalize(),
                            genre.slug,
                        )
                    }

            tagsList = filterDto.tags.map { tag ->
                FilterersTri(
                    tag.titles.find { it.lang == "RU" }!!.content.capitalize(),
                    tag.slug,
                )
            }
        }
    }
    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters += if (genresList.isEmpty() or tagsList.isEmpty()) {
            listOf(
                Filter.Separator(),
                Filter.Header("Нажмите «Сбросить», чтобы загрузить все фильтры"),
                Filter.Separator(),
            )
        } else {
            listOf(
                GenreList(genresList),
                TagList(tagsList),
            )
        }
        filters += listOf(
            TypeList(getTypeList()),
            FormatList(getFormatList()),
            StatList(getStatList()),
            StatTranslateList(getStatTranslateList()),
            AgeList(getAgeList()),
        )
        return FilterList(filters)
    }

    private class FilterersTri(name: String, val slug: String) : Filter.TriState(name)
    private class GenreList(genres: List<FilterersTri>) : Filter.Group<FilterersTri>("Жанры", genres)
    private class TagList(tags: List<FilterersTri>) : Filter.Group<FilterersTri>("Тэги", tags)
    private class TypeList(types: List<FilterersTri>) : Filter.Group<FilterersTri>("Тип", types)
    private class FormatList(formats: List<FilterersTri>) : Filter.Group<FilterersTri>("Формат", formats)
    private class StatList(status: List<FilterersTri>) : Filter.Group<FilterersTri>("Статус", status)
    private class StatTranslateList(tstatus: List<FilterersTri>) : Filter.Group<FilterersTri>("Статус перевода", tstatus)
    private class AgeList(ages: List<FilterersTri>) : Filter.Group<FilterersTri>("Возрастное ограничение", ages)

    private var genresList: List<FilterersTri> = listOf()
    private var tagsList: List<FilterersTri> = listOf()

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

    companion object {
        private const val offsetCount = 20
        private const val API_URL = "https://api.senkuro.com/graphql"
        private val senkuroExcludeGenres = listOf("hentai", "yaoi", "yuri", "shoujo_ai", "shounen_ai")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }

    private val json: Json by injectLazy()
}
