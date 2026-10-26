package eu.kanade.tachiyomi.extension.tr.nemesisscans

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

@Source
abstract class Nemesisscans : KeiSource() {

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val latest = client.get("$API_URL/latestepisodes?limit=50").parseAs<LatestDto>()
        val mangas = latest.data.map { it.series }.distinctBy { it.id }.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$API_URL/series/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query.trim())
        } else {
            "$API_URL/series/browse".toHttpUrl().newBuilder().apply {
                addQueryParameter("sort", filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "newest")
                filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                    addQueryParameter("genre", it)
                }
            }
        }.addQueryParameter("page", page.toString()).build()

        val result = client.get(url).parseAs<SeriesListDto>()
        return MangasPage(result.data.map { it.toSManga() }, result.pagination.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return client.get("$API_URL/series/$id").parseAs<SeriesDetailDto>().series.toSManga()
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detail = client.get("$API_URL/series/${manga.url}").parseAs<SeriesDetailDto>()
        return SMangaUpdate(
            manga = detail.series.toSManga(),
            chapters = detail.episodes.map { it.toSChapter(detail.series.id) }.sortedByDescending { it.chapter_number },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (seriesId, episode) = chapter.url.split("/")
        return "$baseUrl/series/$seriesId/episode/$episode"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (seriesId, episodeNumber) = chapter.url.split("/")
        val episode = client.get("$API_URL/series/$seriesId/episodes/$episodeNumber").parseAs<EpisodePagesDto>().episode
        return episode.images.sortedBy { it.order }.mapIndexed { index, image ->
            Page(index, imageUrl = image.url)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(SortFilter(), GenreFilter())

    companion object {
        private const val API_URL = "https://yahsirou.xyz"
    }
}
