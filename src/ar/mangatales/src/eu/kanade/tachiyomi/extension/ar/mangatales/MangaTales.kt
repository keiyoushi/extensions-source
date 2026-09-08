package eu.kanade.tachiyomi.extension.ar.mangatales

import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Response
import kotlin.getValue

@Source
abstract class MangaTales : KeiSource() {
    private val cdnUrl = "https://media.mangatales.com"

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", getFilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/api/releases?page=$page")
        val releases = response.parseAs<LatestChaptersDto>().releases
            .filterNot { it.manga.isNovel }

        val entries = releases.map { it.manga.toSManga() }
            .distinctBy { it.url }

        return MangasPage(
            entries,
            hasNextPage = (releases.size >= 30),
        )
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val oneShotFilter = filters.firstInstance<OneShotFilter>()
        val mangaTypeFilter = filters.firstInstance<MangaTypeFilter>()
        val storyStatusFilter = filters.firstInstance<StoryStatusFilter>()
        val translationStatusFilter = filters.firstInstance<TranslationStatusFilter>()
        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>() ?: CategoryFilter(emptyList())
        val chapterCountFilter = filters.firstInstance<ChapterCountFilter>()
        val dateRangeFilter = filters.firstInstance<DateRangeFilter>()

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
        ).toJsonRequestBody()

        val data = client.post("$baseUrl/api/mangas/search", body).decryptAs<SearchMangaDto>()

        return MangasPage(
            data.mangas.map { it.toSManga() },
            hasNextPage = data.mangas.size == 50,
        )
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/mangas/").asJsoup()

        val categories = document.select(".js-react-on-rails-component").html()
            .parseAs<FiltersDto>().categories

        return categories.toJsonElement()
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

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.let {
            it.parseAs<List<FilterDto>>()
                .map { TagFilterData(it.id.toString(), it.name) }
        }

        val filters = mutableListOf<Filter<*>>(
            MangaTypeFilter(getTypesFilter()),
            OneShotFilter(),
            StoryStatusFilter(getStatusFilter()),
            TranslationStatusFilter(getTranslationFilter()),
            ChapterCountFilter(),
            DateRangeFilter(),
        )

        if (!categories.isNullOrEmpty()) {
            filters += CategoryFilter(categories)
        }

        return FilterList(filters)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async {
            if (!fetchDetails) return@async manga
            val response = client.get(baseUrl + manga.url)
            response.asJsoup().select(".js-react-on-rails-component").html()
                .parseAs<MangaDataAction<MangaDetailsDto>>()
                .mangaDataAction.mangaData.toSManga()
        }
        val chaptersDeferred = async {
            if (!fetchChapters) return@async chapters
            val response = client.get("$baseUrl/api${manga.url}")
            response.parseAs<ChapterListDto>().mangaReleases
                .map { it.toSChapter() }.sortChapters()
        }

        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    private fun List<SChapter>.sortChapters() = sortedWith(
        compareBy(
            { -it.chapter_number },
            { -it.date_upload },
        ),
    )

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl/${chapter.url}")
        val data = response.asJsoup()
            .select(".js-react-on-rails-component").html()
            .parseAs<ReaderDto>()

        return data.readerDataAction.readerData.release.pages
            .mapIndexed { idx, img ->
                Page(idx, imageUrl = "$cdnUrl/uploads/releases/$img?ak=${data.globals.mediaKey}")
            }
    }

    inline fun <reified T> Response.decryptAs(): T = decrypt(parseAs<EncryptedResponse>().data).parseAs()

    fun createThumbnail(mangaId: String, cover: String): String = "$cdnUrl/uploads/manga/cover/$mangaId/large_$cover"
}
