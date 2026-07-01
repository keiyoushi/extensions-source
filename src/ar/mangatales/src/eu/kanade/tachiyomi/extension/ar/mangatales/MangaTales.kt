package eu.kanade.tachiyomi.extension.ar.mangatales

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import kotlin.getValue

@Source
abstract class MangaTales : HttpSource() {

    private val cdnUrl = "https://media.mangatales.com"

    override val supportsLatest = true

    val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", getFilterList())
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/releases?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val releases = response.parseAs<LatestChaptersDto>().releases
            .filterNot { it.manga.isNovel }

        val entries = releases.map { it.manga.toSManga(::createThumbnail) }
            .distinctBy { it.url }

        return MangasPage(
            entries,
            hasNextPage = (releases.size >= 30),
        )
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val mangaTypeFilter = filterList.findInstance<MangaTypeFilter>()!!
        val oneShotFilter = filterList.findInstance<OneShotFilter>()!!
        val storyStatusFilter = filterList.findInstance<StoryStatusFilter>()!!
        val translationStatusFilter = filterList.findInstance<TranslationStatusFilter>()!!
        val chapterCountFilter = filterList.findInstance<ChapterCountFilter>()!!
        val dateRangeFilter = filterList.findInstance<DateRangeFilter>()!!
        val categoryFilter = filterList.findInstance<CategoryFilter>() ?: CategoryFilter(emptyList())

        val body = SearchPayload(
            oneshot = OneShot(
                value = oneShotFilter.state.first().run {
                    when {
                        isIncluded() -> true
                        else -> false
                    }
                },
            ),
            title = query,
            page = page,
            mangaTypes = IncludeExclude(
                include = mangaTypeFilter.state.filter { it.isIncluded() }.map { it.id },
                exclude = mangaTypeFilter.state.filter { it.isExcluded() }.map { it.id },
            ),
            storyStatus = IncludeExclude(
                include = storyStatusFilter.state.filter { it.isIncluded() }.map { it.id },
                exclude = storyStatusFilter.state.filter { it.isExcluded() }.map { it.id },
            ),
            tlStatus = IncludeExclude(
                include = translationStatusFilter.state.filter { it.isIncluded() }.map { it.id },
                exclude = translationStatusFilter.state.filter { it.isExcluded() }.map { it.id },
            ),
            categories = IncludeExclude(
                // always include null, maybe to avoid shifting index in the backend
                include = listOf(null) + categoryFilter.state.filter { it.isIncluded() }.map { it.id },
                exclude = categoryFilter.state.filter { it.isExcluded() }.map { it.id },
            ),
            chapters = MinMax(
                min = chapterCountFilter.min.run {
                    when {
                        state == "" -> ""
                        isValid() -> state
                        else -> throw Exception("الحد الأدنى لعدد الفصول غير صالح")
                    }
                },
                max = chapterCountFilter.max.run {
                    when {
                        state == "" -> ""
                        isValid() -> state
                        else -> throw Exception("الحد الأقصى لعدد الفصول غير صالح")
                    }
                },
            ),
            dates = StartEnd(
                start = dateRangeFilter.start.run {
                    when {
                        state == "" -> ""
                        isValid() -> state
                        else -> throw Exception("تاريخ بداية غير صالح")
                    }
                },
                end = dateRangeFilter.end.run {
                    when {
                        state == "" -> ""
                        isValid() -> state
                        else -> throw Exception("تاريخ نهاية غير صالح")
                    }
                },
            ),
        ).let(json::encodeToString).toRequestBody(MEDIA_TYPE)

        return POST("$baseUrl/api/mangas/search", headers, body)
    }

    private var categories: List<TagFilterData> = emptyList()
    private var filtersState = FilterState.Unfetched
    private var filterAttempts = 0

    private enum class FilterState {
        Fetching,
        Fetched,
        Unfetched,
    }

    private suspend fun fetchFilters() {
        if (filtersState == FilterState.Unfetched && filterAttempts < 3) {
            filtersState = FilterState.Fetching
            filterAttempts++

            try {
                categories = client.newCall(GET("$baseUrl/mangas/", headers))
                    .await()
                    .asJsoup()
                    .select(".js-react-on-rails-component").html()
                    .parseAs<FiltersDto>()
                    .run {
                        categories ?: categoryTypes!!.flatMap { it.categories!! }
                    }
                    .map { TagFilterData(it.id.toString(), it.name) }

                filtersState = FilterState.Fetched
            } catch (e: Exception) {
                Log.e(name, e.stackTraceToString())
                filtersState = FilterState.Unfetched
            }
        }
    }

    private fun getTypesFilter() = listOf(
        TagFilterData("1", "عربية", Filter.TriState.STATE_INCLUDE),
        TagFilterData("2", "إنجليزي", Filter.TriState.STATE_INCLUDE),
    )

    private fun getStatusFilter() = listOf(
        TagFilterData("2", "مستمرة"),
        TagFilterData("3", "منتهية"),
    )

    private fun getTranslationFilter() = listOf(
        TagFilterData("0", "منتهية"),
        TagFilterData("1", "مستمرة"),
        TagFilterData("2", "متوقفة"),
        TagFilterData("3", "غير مترجمة", Filter.TriState.STATE_EXCLUDE),
    )

    override fun getFilterList(): FilterList {
        CoroutineScope(Dispatchers.IO).launch { fetchFilters() }

        val filters = mutableListOf<Filter<*>>(
            MangaTypeFilter(getTypesFilter()),
            OneShotFilter(),
            StoryStatusFilter(getStatusFilter()),
            TranslationStatusFilter(getTranslationFilter()),
            ChapterCountFilter(),
            DateRangeFilter(),
        )

        filters += if (filtersState == FilterState.Fetched) {
            listOf(
                CategoryFilter(categories),
            )
        } else {
            listOf(
                Filter.Separator(),
                Filter.Header("اضغط على\"إعادة تعيين\"لمحاولة تحميل التصنيفات"),
            )
        }

        return FilterList(filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.decryptAs<SearchMangaDto>()
        return MangasPage(
            data.mangas.map { it.toSManga(::createThumbnail) },
            hasNextPage = data.mangas.size == 50,
        )
    }

    override fun mangaDetailsParse(response: Response): SManga = response.asJsoup()
        .select(".js-react-on-rails-component").html()
        .parseAs<MangaDataAction<MangaDetailsDto>>()
        .mangaDataAction.mangaData
        .toSManga(::createThumbnail)

    private fun chaptersRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/mangas/$mangaId", headers)
    }

    private fun chaptersParse(response: Response): List<SChapter> {
        val releases = response.parseAs<ChapterListDto>().mangaReleases

        return releases.map { it.toSChapter() }
    }

    override fun chapterListRequest(manga: SManga) = chaptersRequest(manga)
    override fun chapterListParse(response: Response) = chaptersParse(response).sortChapters()

    private fun List<SChapter>.sortChapters() = sortedWith(
        compareBy(
            { -it.chapter_number },
            { -it.date_upload },
        ),
    )

    override fun pageListParse(response: Response): List<Page> {
        val data = response.asJsoup()
            .select(".js-react-on-rails-component").html()
            .parseAs<ReaderDto>()

        return data.readerDataAction.readerData.release.pages
            .mapIndexed { idx, img ->
                Page(idx, imageUrl = "$cdnUrl/uploads/releases/$img?ak=${data.globals.mediaKey}")
            }
    }

    private val pageSort =
        compareBy<String>({ parseNumber(0, it) ?: Double.MAX_VALUE }, { parseNumber(1, it) }, { parseNumber(2, it) })

    private fun parseNumber(index: Int, string: String): Double? = Regex("\\d+").findAll(string).map { it.value }.toList().getOrNull(index)?.toDoubleOrNull()

    inline fun <reified T> Response.decryptAs(): T = decrypt(parseAs<EncryptedResponse>().data).parseAs()

    inline fun <reified T> Response.parseAs(): T = body.string().parseAs()

    inline fun <reified T> String.parseAs(): T = json.decodeFromString(this)

    inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private fun createThumbnail(mangaId: String, cover: String): String = "$cdnUrl/uploads/manga/cover/$mangaId/large_$cover"

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}
