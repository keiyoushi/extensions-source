package eu.kanade.tachiyomi.extension.en.komix

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Komix : KeiSource() {

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter()))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter("date")))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/page-data/browse".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<SelectFilter>().forEach { it.addToUrl(this) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        val browse = client.get(url).parseAs<BrowseDto>()
        val baseHttpUrl = baseUrl.toHttpUrl()

        return MangasPage(
            browse.results.map { it.toSManga(baseHttpUrl) },
            browse.page * PAGE_SIZE < browse.totalCount,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "title") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null

        return getMangaUpdate(
            manga = SManga.create().apply { this.url = slug },
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaPage = client.get("$baseUrl/api/page-data/title/${manga.url}").parseAs<MangaPageDto>()
        val synopsis = mangaPage.jsonLd
            ?.parseAs<LdJsonDto>()
            ?.graph
            ?.firstOrNull { it.type == "Book" }
            ?.description
        val baseHttpUrl = baseUrl.toHttpUrl()

        return SMangaUpdate(
            mangaPage.manga.toSManga(baseHttpUrl, synopsis),
            mangaPage.chapters.map { it.toSChapter(baseHttpUrl, mangaPage.manga.slug) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reader = client.get(getChapterUrl(chapter)).extractNextJs<ReaderPropsDto> {
            it is JsonObject && it["chapter"] is JsonObject && "manga" in it
        }!!

        return reader.chapter.pages.mapIndexed { index, page ->
            val imageUrl = "$baseUrl/api/images".toHttpUrl().newBuilder()
                .addQueryParameter("t", page.enc)
                .build()
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/page-data/browse").parseAs<GenresDto>().allGenres.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            SortFilter(),
            data?.parseAs<List<GenreDto>>()?.takeIf { it.isNotEmpty() }?.let(::GenreFilter),
            StatusFilter(),
            TypeFilter(),
        ),
    )
}

private const val PAGE_SIZE = 60
