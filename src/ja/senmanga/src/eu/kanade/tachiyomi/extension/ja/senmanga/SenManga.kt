package eu.kanade.tachiyomi.extension.ja.senmanga

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
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Source
abstract class SenManga : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$apiUrl/directory?order=Popular&page=$page").parseAs<DirectoryResponse>().toMangasPage()

    private fun DirectoryResponse.toMangasPage(): MangasPage {
        val mangas = series.map { it.toSManga() }
        val hasNext = (currentPage ?: 1) < (totalPages ?: 1)

        return MangasPage(mangas, hasNext)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val data = client.get("$apiUrl/home").parseAs<HomeResponse>()
        val mangas = data.series.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/directory".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("s", query)
        }

        filters.firstInstanceOrNull<TypeFilter>()?.let {
            url.addQueryParameter("type", it.toUriPart())
        }

        filters.firstInstanceOrNull<StatusFilter>()?.let {
            url.addQueryParameter("status", it.toUriPart())
        }

        filters.firstInstanceOrNull<OrderFilter>()?.let {
            url.addQueryParameter("order", it.toUriPart())
        }

        return client.get(url.build()).parseAs<DirectoryResponse>().toMangasPage()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        StatusFilter(),
        OrderFilter(),
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = client.get("$apiUrl/manga/${manga.url}").parseAs<SeriesDto>()
        val mangaSlug = data.slug

        val chapterList = data.chapterList?.map { chapter ->
            SChapter.create().apply {
                url = "$mangaSlug/${chapter.url}"
                name = chapter.title
                date_upload = Instant.tryParse(chapter.datetime)
            }
        } ?: emptyList()

        return SMangaUpdate(data.toSManga(), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get("$apiUrl/read/${chapter.url}").parseAs<ReadResponse>()
        return data.pages.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaSlug = chapter.url.substringBefore("/")
        val chapterSlug = chapter.url.substringAfter("/")
        return "$baseUrl/manga/$mangaSlug/chapter-$chapterSlug/"
    }
}
