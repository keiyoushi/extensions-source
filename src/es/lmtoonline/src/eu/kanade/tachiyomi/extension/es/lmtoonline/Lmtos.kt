package eu.kanade.tachiyomi.extension.es.lmtoonline

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Lmtos : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds) { it.host == baseUrl.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int) = fetchSeries(page, "", FilterList(), "rating")

    override suspend fun getLatestUpdates(page: Int) = fetchSeries(page, "", FilterList(), "recent")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = fetchSeries(page, query, filters, "title")

    private suspend fun fetchSeries(page: Int, query: String, filters: FilterList, defaultSort: String): MangasPage {
        val url = "$baseUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", filters.firstInstanceOrNull<OrderFilter>()?.selected ?: defaultSort)
            if (query.isNotBlank()) addQueryParameter("q", query)
            filters.firstInstanceOrNull<GenreFilter>()?.state
                ?.filter { it.state }
                ?.forEach { addQueryParameter("genre", it.name) }
            filters.firstInstanceOrNull<StatusFilter>()?.selected?.takeIf { it != "all" }?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<DemographicFilter>()?.selected?.takeIf { it != "all" }?.let { addQueryParameter("demographic", it) }
            filters.firstInstanceOrNull<TypeFilter>()?.selected?.takeIf { it != "all" }?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<NsfwFilter>()?.selected?.takeIf { it != "all" }?.let { addQueryParameter("adult", it) }
        }.build()

        val result = client.get(url).asJsoup().extractNextJs<MangaList>() ?: return MangasPage(emptyList(), false)
        return MangasPage(result.mangas.map { it.toSManga() }, page * result.pageSize < result.total)
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = document.extractNextJs<MangaDetails>()!!.manga.toSManga()

        val chapterList = document.extractNextJs<ChapterList>()?.let { result ->
            val mangaSlug = result.manga.slug
            result.chapters.map { it.toSChapter(mangaSlug) }
        }.orEmpty()

        return SMangaUpdate(details, chapterList)
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.get(getChapterUrl(chapter)).asJsoup().extractNextJs<ChapterPages>() ?: return emptyList()
        return result.chapter.pages.orEmpty().mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }
}
