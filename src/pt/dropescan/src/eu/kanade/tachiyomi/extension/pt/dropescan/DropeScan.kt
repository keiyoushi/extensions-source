package eu.kanade.tachiyomi.extension.pt.dropescan

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class DropeScan : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = obras(page) {}

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$API_URL/chapters/recent".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val recent = client.get(url, Fetch.XHR).parseAs<DataDto<RecentPageDto>>().data

        return MangasPage(recent.data.map { it.toSManga() }, recent.meta.currentPage < recent.meta.lastPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = obras(page) {
        if (query.isNotBlank()) addQueryParameter("query", query.trim())
        filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }

        val tags = filters.filterIsInstance<TagGroupFilter>().flatMap { it.state }
        val included = tags.filter { it.isIncluded() }
        val excluded = tags.filter { it.isExcluded() }
        if (included.isNotEmpty()) {
            included.forEach { addQueryParameter("includedTags[]", it.id) }
            addQueryParameter("includedTagsMode", filters.firstInstance<IncludedTagsModeFilter>().value)
        }
        if (excluded.isNotEmpty()) {
            excluded.forEach { addQueryParameter("excludedTags[]", it.id) }
            addQueryParameter("excludedTagsMode", filters.firstInstance<ExcludedTagsModeFilter>().value)
        }
    }

    private suspend fun obras(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = "$API_URL/obras".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .apply(params)
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<ObrasDto>()

        return MangasPage(result.data.map { it.toSManga() }, result.meta.offset + result.data.size < result.meta.total)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2 || url.pathSegments[0] != "obras") return null

        val manga = SManga.create().apply { this.url = "/obras/${url.pathSegments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) getChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun getMangaDetails(manga: SManga): SManga = client.get(getMangaUrl(manga), Fetch.DOCUMENT)
        .extractNextJs<ObraDto> { it is JsonObject && "ObraId" in it && "Chapters" in it }!!
        .toSManga()

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val mangaUrl = getMangaUrl(manga)
        val obraId = mangaUrl.toHttpUrl().pathSegments[1]

        return client.get("$API_URL/obras/$obraId/chapters", Fetch.XHR.from(mangaUrl)).parseAs<DataDto<ChapterListDto>>().data.chapters
            .map { it.toSChapter(obraId) }
            .sortedByDescending { it.chapter_number }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val chapterId = chapterUrl.toHttpUrl().pathSegments[2]

        return client.get("$API_URL/chapter/$chapterId", Fetch.XHR.from(chapterUrl)).parseAs<DataDto<ChapterDetailsDto>>().data.chapter.pages
            .mapIndexed { i, page -> Page(i, imageUrl = imageUrl(page.path)) }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val formats = async { client.get("$API_URL/tags/formats?limit=100", Fetch.XHR).parseAs<TagListDto<FormatTagDto>>().data.map { it.name to it.id } }
        val genres = async { client.get("$API_URL/tags/genres?limit=100", Fetch.XHR).parseAs<TagListDto<GenreTagDto>>().data.map { it.name to it.id } }
        val themes = async { client.get("$API_URL/tags/themes?limit=100", Fetch.XHR).parseAs<TagListDto<ThemeTagDto>>().data.map { it.name to it.id } }
        TagsData(formats.await(), genres.await(), themes.await()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<TagsData>()

        return FilterList(
            buildList {
                add(SortFilter())
                add(StatusFilter())
                add(DemographicFilter())
                add(ContentRatingFilter())
                add(ContentWarningFilter())
                add(LanguageFilter())
                add(YearFilter())
                if (tags != null) {
                    add(Filter.Separator())
                    add(TagGroupFilter("Formatos", tags.formats))
                    add(TagGroupFilter("Gêneros", tags.genres))
                    add(TagGroupFilter("Temas", tags.themes))
                    add(IncludedTagsModeFilter())
                    add(ExcludedTagsModeFilter())
                }
            },
        )
    }

    companion object {
        private const val API_URL = "https://api.dropescan.com/v1"
        private const val PAGE_SIZE = 20
    }
}
