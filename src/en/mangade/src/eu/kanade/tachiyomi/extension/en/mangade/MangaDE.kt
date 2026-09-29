package eu.kanade.tachiyomi.extension.en.mangade

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaDE : KeiSource() {

    private val apiUrl get() = "https://api.${baseUrl.toHttpUrl().host}/api"
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.POPULAR)))
    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.LATEST)))
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", "20")

        if (query.isNotEmpty()) {
            url.addQueryParameter("name", query)
        }

        filters.firstInstanceOrNull<SortFilter>()?.let {
            url.addQueryParameter("sort", it.toUriPart())
        }
        filters.firstInstanceOrNull<StatusFilter>()?.takeIf { it.state != 0 }?.let {
            url.addQueryParameter("comic_status", it.toUriPart())
        }
        filters.firstInstanceOrNull<TypeFilter>()?.takeIf { it.state != 0 }?.let {
            url.addQueryParameter("category", it.toUriPart())
        }
        filters.firstInstanceOrNull<YearFilter>()?.takeIf { it.state != 0 }?.let {
            url.addQueryParameter("year", it.toUriPart())
        }
        filters.firstInstanceOrNull<ChapterCountFilter>()?.let {
            url.addQueryParameter("min_chapter_count", it.toUriPart())
        }
        filters.firstInstanceOrNull<GenreFilter>()?.state
            ?.filter { it.state }
            ?.forEach { url.addQueryParameter("genres[]", it.id) }

        val response = client.get(url.build())
        return response.parseAs<PayloadDto<MangaListPageDto>>().data.toMangasPage()
    }

    override fun getMangaUrl(manga: SManga): String {
        val url = super.getMangaUrl(manga).toHttpUrl()
        val slug = url.pathSegments[0]
        val mid = url.queryParameter("mid")

        return "$baseUrl/comic/$slug-pid$mid"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = super.getChapterUrl(chapter).toHttpUrl()
        val mid = url.queryParameter("mid")
        val mangaSlug = url.pathSegments[0]
        val chapterSlug = url.pathSegments[1]

        return "$baseUrl/comic/$mangaSlug-$mid/$chapterSlug"
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        if (url.pathSegments.firstOrNull() != "comic") return null
        val mangaId = url.pathSegments.getOrNull(1)
            ?.substringAfterLast('-', "")
            ?.removePrefix("pid")
            ?.takeIf { it.isNotEmpty() } ?: return null

        val data = client.get("$apiUrl/comics/$mangaId/view").parseAs<PayloadDto<MangaDto>>().data
        return data.toSManga().apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = super.getMangaUrl(manga).toHttpUrl().queryParameter("mid")!!
        val data = client.get("$apiUrl/comics/$id/view").parseAs<PayloadDto<MangaDto>>().data
        return SMangaUpdate(data.toSManga(), data.toSChapterList(dateFormat))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = super.getChapterUrl(chapter).toHttpUrl().queryParameter("cid")!!
        return client.get("$apiUrl/chapters/$id/view").parseAs<PayloadDto<ChapterDto>>().data.toPageList()
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres?size=500").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SortFilter())
            add(StatusFilter())
            add(TypeFilter())
            add(YearFilter())
            add(ChapterCountFilter())

            val genres = data?.parseAs<PayloadDto<GenreListPageDto>>()?.data?.genres.orEmpty()
            if (genres.isEmpty()) return@buildList

            add(Filter.Separator())
            add(GenreFilter(genres.map { Genre(it.name, it.id) }))
        },
    )
}
